package geidsvig.netty.socket.ws

import org.jboss.netty.handler.codec.http.HttpResponseStatus

import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import akka.event.LoggingAdapter
import geidsvig.netty.rest.ChannelWithRequest
import geidsvig.netty.rest.RestUtils
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame

trait WebSocketManagerRequirements {
  val webSocketHandlerFactory: WebSocketHandlerFactory
  val logger: LoggingAdapter
}

abstract class WebSocketManager extends RestUtils {
  this: WebSocketManagerRequirements =>

  /**
   * Implementation can vary.
   * examples:
   * 1) singleton can store a key-value map of uuid and actorRefs
   * 2) distributed system can use guardian ring to distribute handlers
   * 3) or could use memcache to store uuid -> sessionHandler actor
   */
  def handleWebSocketRequest(request: ChannelWithRequest) {
    
    /* 
     * Websockets cannot attach headers on handshake. This is unfortunate...
     * We will expect the uuid as a query param instead.
     */
    extractUuid(request.request) match {
      case Some(uuid) => {
        
        // check if we have registered the handler.
        // if yes, then tell it to close, because there is a new handler to replace it.
        // if no, then create a new handler, and send it the request
        hasRegisteredHandler(uuid) match {
          case None => {}
          case Some(handler) => {
            handler ! new CloseWebSocketFrame
          }
        }
        
        val newHandler = webSocketHandlerFactory.createWebSocketHandler(uuid)
        newHandler ! request
      }
      case None => {
        val response = createHttpResponse(HttpResponseStatus.BAD_REQUEST, callback(request.request, "Missing uuid"))
        sendHttpResponse(request.ctx, request.request, response)
      }
    }
  }

  /**
   *
   * @param uuid
   * @returns None if handler not registered for uuid. Some(ActorRef) otherwise
   */
  def hasRegisteredHandler(uuid: String): Option[ActorRef]

  /**
   * @param uuid
   * @param actorRef
   */
  def registerHandler(uuid: String, actorRef: ActorRef): Unit

  /**
   * Regardless of implementations, the uuid and actorRef for WebSocketSessionHandler should be removed.
   *
   * @param uuid
   */
  def deregisterHandler(uuid: String): Unit

}