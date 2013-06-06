package geidsvig.netty.socket.ws

import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.ChannelFuture
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import org.jboss.netty.handler.codec.http.websocketx.PingWebSocketFrame
import org.jboss.netty.handler.codec.http.websocketx.PongWebSocketFrame
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshaker
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory
import org.jboss.netty.util.CharsetUtil

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.actorRef2Scala
import akka.event.LoggingAdapter
import geidsvig.netty.rest.ChannelWithRequest

trait WebSocketHandlerRequirements {
  val webSocketSessionManager: WebSocketManager
}

/**
 * The session handler delegates websocket setup to a WebSocketHandler,
 * and provides hooks for that handler to let this actor know when events have occurred.
 *
 * @param uuid a unique user id for the system. Expecting only 1 session handler per uuid.
 */
abstract class WebSocketHandler(uuid: String) extends Actor with ActorLogging {
  this: WebSocketHandlerRequirements =>

  case class WebSocketPayload(payload: String)

  case object WebSocketClosed

  var webSocketConnectionHandler: WebSocketConnectionHandler = null

  override def preStart() {
    webSocketSessionManager.registerHandler(uuid)
    webSocketConnectionHandler = new WebSocketConnectionHandler(log, self)
  }
  
  override def postStop() {
    webSocketSessionManager.deregisterHandler(uuid)
  }

  def receive = {
    case ChannelWithRequest(ctx, request) => webSocketConnectionHandler.handleWebsocketHandshake(ctx, request)
    case WebSocketPayload(payload) => handlePayload(payload)
    case WebSocketClosed => handleCloseSocket()
    case other => {
      log warning ("Unsupported operation {}", other.toString())
    }
  }

  /**
   * An abstract method to be dealt with by the implementing class.
   * 
   * @param payload
   */
  def handlePayload(payload: String)

  /**
   * Use this method to send a response back to the client.
   *
   * @param packet
   */
  def sendResponse(payload: String) {
    webSocketConnectionHandler.sendWebSocketFrame(payload)
  }

  /**
   * 
   */
  private def handleCloseSocket() = {
    self ! PoisonPill
  }

  /**
   * Inner class because of how SimpleChannelUpstreamHandler needs to be implemented.
   *
   * @param logger
   * @param parentActorRef the WebSocketHandler that instantiates this class
   */
  class WebSocketConnectionHandler(logger: LoggingAdapter,
    webSocketHandler: ActorRef) extends SimpleChannelUpstreamHandler {

    var channel: Option[Channel] = None

    override def messageReceived(ctx: ChannelHandlerContext, msgEvent: MessageEvent) {
      msgEvent.getMessage() match {
        case close: CloseWebSocketFrame => handleCloseSocket()
        case frame: WebSocketFrame => handleWebSocketFrame(ctx, frame)
        case unhandled => {
          logger warning ("Did not get WebSocketFrame: " + unhandled.getClass().toString())
          logger warning ("Unexpected event toString:  " + unhandled.toString)
          //respond with invalid frame msg
          sendWebSocketFrame("BAD REQUEST")
        }
      }
    }

    /**
     *
     * @param ctx
     * @param request
     */
    def handleWebsocketHandshake(ctx: ChannelHandlerContext, request: HttpRequest) {
      val protocol = request.getHeader(HttpHeaders.Names.WEBSOCKET_PROTOCOL)
      logger info ("WebSocket protocol:  " + protocol)

      val wsLocation = "ws://" + request.getHeader(HttpHeaders.Names.HOST) + "/websocket"
      val factory = new WebSocketServerHandshakerFactory(wsLocation, null, false)

      Option(factory.newHandshaker(request)) match {
        case None => {
          logger error ("Unsupported WebSocket version")
          factory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel)
        }
        case Some(shaker) => {
          shaker.handshake(ctx.getChannel(), request).addListener(WebSocketServerHandshaker.HANDSHAKE_LISTENER)
          logger debug "Handshake complete"
        }
      }
      channel = Some(ctx.getChannel)
      sendWebSocketFrame("SOCKET CREATED")
    }

    /**
     *
     * @param ctx
     * @param frame
     */
    def handleWebSocketFrame(ctx: ChannelHandlerContext, frame: WebSocketFrame) {
      frame match {
        case text: TextWebSocketFrame => {
          val inboundPayload = text.getText
          logger info ("WebSocket frame text:  " + inboundPayload)
          webSocketHandler ! WebSocketPayload(inboundPayload)
        }
        case ping: PingWebSocketFrame => {
          ctx.getChannel().write(new PongWebSocketFrame(ping.getBinaryData()))
        }
        case closing: CloseWebSocketFrame => handleCloseSocket()
        case somethingElse => {
          logger warning ("Got an unrecognized WebSocketFrame:  " + somethingElse.getClass.toString)
          sendWebSocketFrame("BAD REQUEST")
        }
      }
    }

    /**
     * Simple websocket push method.
     *
     * @param payload
     */
    def sendWebSocketFrame(payload: String) {
      channel match {
        case Some(chan) if (chan.isOpen) => chan.write(new TextWebSocketFrame(ChannelBuffers.copiedBuffer(payload, CharsetUtil.UTF_8)))
        case Some(chan) => logger warning ("Trying to push {} with closed channel", payload)
        case None => logger warning ("Trying to push with no channel. Dropping")
      }
    }

    /**
     * Can be used by implementing class to extend method and trigger death of parent actor.
     */
    def handleCloseSocket() {
      logger info ("WebSocket close.")
      channel match {
        case Some(chan) => {
          if (chan.isOpen) {
            chan.close().addListener(new ChannelFutureListener() {
              def operationComplete(future: ChannelFuture) {
                // Perform post-closure operation
                webSocketHandler ! WebSocketClosed
              }
            })
          } else {
            logger warning ("Trying to close a closed channel.")
            webSocketHandler ! WebSocketClosed
          }
        }
        case None => {
          logger warning ("Trying to close with no channel.")
          webSocketHandler ! WebSocketClosed
        }
      }
    }

  } //WebSocketConnectionHandler

}


