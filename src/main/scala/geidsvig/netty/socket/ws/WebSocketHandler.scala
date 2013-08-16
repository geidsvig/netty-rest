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
import akka.actor.ReceiveTimeout
import akka.event.LoggingAdapter
import geidsvig.netty.rest.ChannelWithRequest
import org.jboss.netty.channel.ChannelPipeline
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameDecoder
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrameEncoder
import org.jboss.netty.handler.codec.http.websocketx.WebSocket08FrameDecoder
import org.jboss.netty.handler.codec.http.websocketx.WebSocket08FrameEncoder
import org.jboss.netty.channel.ExceptionEvent
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

/**
 * @param receiveTimeout the amount of time in millis where no inbound nor outbound messages occur before Actor shutsdown
 * @param useTLS refers to enabling Transport Layer Security (TLS)/Secure Sockets Layer (SSL)
 * @param webSocketManager
 */
trait WebSocketHandlerRequirements {
  val receiveTimeout: Long
  val useTLS: Boolean
  val webSocketManager: WebSocketManager
}

case class TransmitPayload(payload: String)

/**
 * The session handler delegates websocket setup to a WebSocketHandler,
 * and provides hooks for that handler to let this actor know when events have occurred.
 *
 * @param uuid a unique user id for the system. Expecting only 1 session handler per uuid.
 */
abstract class WebSocketHandler(uuid: String) extends Actor with ActorLogging {
  this: WebSocketHandlerRequirements =>

  val startTime = System.currentTimeMillis

  case class WebSocketPayload(payload: String)

  case object WebSocketClosed

  var webSocketConnectionHandler: WebSocketConnectionHandler = null

  override def preStart() {
    webSocketManager.registerHandler(uuid, self)
    webSocketConnectionHandler = new WebSocketConnectionHandler(log, self)
  }

  override def postStop() {
    webSocketManager.deregisterHandler(uuid)
  }

  // set default receiveTimeout because trait injected timeout value is not loaded yet
  context.setReceiveTimeout(Duration.create(5000, TimeUnit.MILLISECONDS))
  def receive = {
    case ChannelWithRequest(ctx, request) => {
      webSocketConnectionHandler.handleWebsocketHandshake(ctx, request)
      context.setReceiveTimeout(Duration.create(receiveTimeout, TimeUnit.MILLISECONDS))
    }
    case WebSocketPayload(payload) => handlePayload(payload)
    case TransmitPayload(payload) => sendResponse(payload)
    case WebSocketClosed => handleCloseSocket()
    case ReceiveTimeout => handleReceiveTimeout()
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
   * Handle the closing of the websocket and shutting down self.
   * 
   */
  private def handleCloseSocket() = {
    val endTime = System.currentTimeMillis
    webSocketConnectionHandler.handleCloseSocket()
    log info ("WebSocket Handler terminated after {}ms", (endTime - startTime))
    context.stop(self)
  }

  /**
   * When a receive timeout event occurs, we want to close the current websocket channel and shut down self.
   * 
   */
  private def handleReceiveTimeout() {
    val endTime = System.currentTimeMillis
    webSocketConnectionHandler.handleCloseSocket()
    log info ("WebSocket Handler timeout after {}ms", (endTime - startTime))
    context.stop(self)
  }

  /**
   * Inner class because of how SimpleChannelUpstreamHandler needs to be implemented.
   *
   * @param logger
   * @param parentActorRef the WebSocketHandler that instantiates this class
   */
  class WebSocketConnectionHandler(logger: LoggingAdapter,
    webSocketHandler: ActorRef) extends SimpleChannelUpstreamHandler with TextFrameBuilder {

    var channel: Option[Channel] = None
    private val subprotocols = null
    private val allowExtensions = true

    private val SOCKETCREATED = ResponseTextFrame(200, """"connected"""").toJson
    private val BADREQUEST = ResponseTextFrame(400, """"bad request"""").toJson

    override def messageReceived(ctx: ChannelHandlerContext, msgEvent: MessageEvent) {
      msgEvent.getMessage() match {
        case close: CloseWebSocketFrame => {
          logger info ("client requested closing websocket")
          webSocketHandler ! WebSocketClosed
        }
        case text: TextWebSocketFrame => {
          val inboundPayload = text.getText
          logger info ("WebSocket frame text:  " + inboundPayload)
          webSocketHandler ! WebSocketPayload(inboundPayload)
        }
        case ping: PingWebSocketFrame => {
          ctx.getChannel().write(new PongWebSocketFrame(ping.getBinaryData()))
        }
        case unhandled => {
          logger warning ("Did not get WebSocketFrame: " + unhandled.getClass().toString())
          logger warning ("Unexpected event toString:  " + unhandled.toString)
          //respond with invalid frame msg
          sendWebSocketFrame(BADREQUEST)
        }
      }
    }

    /**
     * Override exceptionCaught because we have to...
     * Also, if something does cause an exception, we tell our parent webSocketHandler to handle WebSocketClosed.
     * 
     * @param ctx
     * @param e an ExceptionEvent
     */
    override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
      logger error (e.getCause, "Exception caught in handler!")
      webSocketHandler ! WebSocketClosed
    }

    /**
     * Handles the handshake. Supports both with and without Transport Layer Security (TLS)/Secure Sockets Layer (SSL).
     * Updates the pipeline from HTTP to websocket so that the RestRouteHandler is no longer in control of the channel.
     * 
     * @param ctx
     * @param request
     */
    def handleWebsocketHandshake(ctx: ChannelHandlerContext, request: HttpRequest) {
      val protocol = request.getHeader(HttpHeaders.Names.WEBSOCKET_PROTOCOL)
      logger info ("WebSocket protocol:  " + protocol)

      // supports Transport Layer Security (TLS)/Secure Sockets Layer (SSL) 
      val wsLocation = useTLS match {
        case true => "wss://" + request.getHeader(HttpHeaders.Names.HOST) + "/websocket"
        case false => "ws://" + request.getHeader(HttpHeaders.Names.HOST) + "/websocket"
      }
      val factory = new WebSocketServerHandshakerFactory(wsLocation, subprotocols, allowExtensions)

      Option(factory.newHandshaker(request)) match {
        case None => {
          logger error ("Unsupported WebSocket version")
          factory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel)
        }
        case Some(shaker) => {

          // Update the pipeline so that this handler manages both the channel upstream and downstream flow.
          // Handshake should have already removed the aggregator, but just in case we make sure it is removed. (Play! does this as well)
          Option(ctx.getChannel.getPipeline.get("aggregator")) match {
            case Some(c) => ctx.getChannel.getPipeline.remove("aggregator")
            case None => {}
          }

          ctx.getChannel.getPipeline.replace("handler", "handler", this)
          logger info ("http pipeline replaced with websocket pipeline")

          shaker.handshake(ctx.getChannel(), request).addListener(WebSocketServerHandshaker.HANDSHAKE_LISTENER)
          logger info "Handshake complete"
        }
      }

      channel = Some(ctx.getChannel)
    }

    /**
     * Simple websocket push method.
     *
     * @param payload
     */
    def sendWebSocketFrame(payload: String) {
      channel match {
        case Some(chan) if (chan.isOpen) => chan.write(new TextWebSocketFrame(ChannelBuffers.copiedBuffer(payload, CharsetUtil.UTF_8)))
        case _ => webSocketHandler ! WebSocketClosed
      }
    }

    /**
     * Can be used by implementing class to extend method and trigger death of parent actor.
     */
    def handleCloseSocket() {
      channel match {
        case Some(chan) if (chan.isOpen) => {
          chan.close()
          channel = None
          logger info ("WebSocket closed")
        }
        case _ => {}
      }
    }

  } //WebSocketConnectionHandler

}


