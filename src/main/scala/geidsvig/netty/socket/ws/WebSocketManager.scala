package geidsvig.netty.socket.ws

import org.jboss.netty.handler.codec.http.HttpResponseStatus

import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import akka.event.LoggingAdapter
import geidsvig.netty.rest.ChannelWithRequest
import geidsvig.netty.rest.RestUtils

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
    Option(request.request.getHeader("uuid")) match {
      case Some(uuid) => {
        // check if we have registered the handler.
        // if yes, check if actorRef is alive.
        // if yes. respond to client that they already have a connection and close the request.ctx.channel
        // if no, then create a new handler, and send it the request
        hasRegisteredHandler(uuid) match {
          case None => {
            val handler = webSocketHandlerFactory.createWebSocketHandler(uuid)
            handler ! request
          }
          case Some(handler) => {
            val response = createHttpResponse(HttpResponseStatus.CONFLICT, callback(request.request, "Duplicate websocket request for uuid"))
            sendHttpResponse(request.ctx, request.request, response)
          }
        }
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
   */
  def registerHandler(uuid: String)

  /**
   * Regardless of implementations, the uuid and actorRef for WebSocketSessionHandler should be removed.
   *
   * @param uuid
   */
  def deregisterHandler(uuid: String)

}