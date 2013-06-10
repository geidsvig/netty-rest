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

trait WebSocketHandlerRequirements {
  val receiveTimeout: Long
  val webSocketManager: WebSocketManager
}

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
    webSocketManager.registerHandler(uuid)
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
   * 
   */
  private def handleCloseSocket() = {
    val endTime = System.currentTimeMillis
    webSocketConnectionHandler.handleCloseSocket()
    log info ("WebSocket Handler terminated after {}ms", (endTime - startTime))
    context.stop(self)
  }
  
  /**
   * When a receive timeout event occurs, we want to close the current websocket channel.
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
    webSocketHandler: ActorRef) extends SimpleChannelUpstreamHandler {

    var channel: Option[Channel] = None
    private val subprotocols = null
    private val allowExtensions = true
    
    //FIXME standardize on return strings for clients
    private val SOCKETCREATED = "SOCKET CREATED"
    private val BADREQUEST = "BAD REQUEST"

    override def messageReceived(ctx: ChannelHandlerContext, msgEvent: MessageEvent) {
      msgEvent.getMessage() match {
        case close: CloseWebSocketFrame => {
          logger info ("client requested closing websocket")
          webSocketHandler ! WebSocketClosed
        }
        //TODO support BinaryWebSocketFrame
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
    
    override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
      logger error (e.getCause, "Exception caught in handler!")
      webSocketHandler ! WebSocketClosed
    }

    /**
     *
     * @param ctx
     * @param request
     */
    def handleWebsocketHandshake(ctx: ChannelHandlerContext, request: HttpRequest) {
      val protocol = request.getHeader(HttpHeaders.Names.WEBSOCKET_PROTOCOL)
      logger info ("WebSocket protocol:  " + protocol)

      //TODO support both ws and wss
      val wsLocation = "ws://" + request.getHeader(HttpHeaders.Names.HOST) + "/websocket"
      val factory = new WebSocketServerHandshakerFactory(wsLocation, subprotocols, allowExtensions)

      Option(factory.newHandshaker(request)) match {
        case None => {
          logger error ("Unsupported WebSocket version")
          factory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel)
        }
        case Some(shaker) => {
          
          // And fix pipeline so that this handler manages both the channel upstream and downstream flow.
          // Handshake should have already removed the aggregator, but just in case we make sure it is removed. (Play! does this as well... HACK)
          Option(ctx.getChannel.getPipeline.get("aggregator")) match {
            case Some(c) => ctx.getChannel.getPipeline.remove("aggregator")
            case None => {}
          }
          ctx.getChannel.getPipeline.replace("handler", "handler", this)
          logger info("http pipeline replaced with websocket pipeline")
          
          shaker.handshake(ctx.getChannel(), request).addListener(WebSocketServerHandshaker.HANDSHAKE_LISTENER)
          logger info "Handshake complete"
        }
      }

      channel = Some(ctx.getChannel)

      sendWebSocketFrame(SOCKETCREATED)
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


