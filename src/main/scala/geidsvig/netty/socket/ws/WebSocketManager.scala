package geidsvig.netty.socket.ws

import akka.actor.ActorRef
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.util.CharsetUtil
import akka.event.LoggingAdapter
import org.jboss.netty.channel.Channel
import akka.actor.actorRef2Scala

trait WebSocketManagerRequirements {
  val webSocketSessionHandlerFactory: WebSocketSessionHandlerFactory
  val logger: LoggingAdapter
}

abstract class WebSocketManager {
  this: WebSocketManagerRequirements =>

  /**
   * Implementation can vary.
   * examples:
   * 1) singleton can store a key-value map of uuid and actorRefs
   * 2) distributed system can use guardian ring to distribute handlers
   * 3) or could use memcache to store uuid -> sessionHandler actor
   */
  def handleWebSocketRequest(request: WebSocketRequest) {
    Option(request.request.getHeader("uuid")) match {
      case Some(uuid) => {
        // check if we have registered the handler.
        // if yes, check if actorRef is alive.
        // if yes. respond to client that they already have a connection and close the request.ctx.channel
        // if no, then create a new handler, and send it the request
        hasRegisteredHandler(uuid) match {
          case Some(handler) => sendWebSocketFrame(Option(request.ctx.getChannel()), "DUPLICATE")
          case None => {
            val handler = webSocketSessionHandlerFactory.createWebSocketSessionHandler()
            handler ! request
          }
        }
      }
      case None => sendWebSocketFrame(Option(request.ctx.getChannel()), "MISSING UUID")
    }

  }

  /**
   * Simple websocket push method.
   *
   * @param payload
   */
  def sendWebSocketFrame(channel: Option[Channel], payload: String) {
    val frame = new TextWebSocketFrame(ChannelBuffers.copiedBuffer(payload, CharsetUtil.UTF_8))

    channel match {
      case Some(chan) if (chan.isOpen) => chan.write(frame)
      case Some(chan) => logger warning ("Trying to push {} with closed channel", payload)
      case None => logger warning ("Trying to push with no channel. Dropping")
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