package geidsvig.netty.socket.ws

import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import org.jboss.netty.handler.codec.http.websocketx.PingWebSocketFrame
import org.jboss.netty.handler.codec.http.websocketx.PongWebSocketFrame
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshaker
import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory
import org.jboss.netty.handler.codec.http.websocketx.WebSocketFrame
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.util.CharsetUtil
import akka.actor.actorRef2Scala
import akka.actor.ActorRef
import akka.event.LoggingAdapter
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.channel.ChannelFuture

case class WebSocketRequest(ctx: ChannelHandlerContext, request: HttpRequest)

case class WebSocketPayload(payload: Array[Byte])

case object WebSocketClosed

/**
 * Would have liked this to be a trait, but cannot mix SimpleChannelUpstreamHandler with scala Object...
 * 
 * @param logger
 * @param websocketHandshaker
 * @param parentActorRef the WebSocketSessionHandler that instantiates this class
 */
class WebSocketHandler(logger: LoggingAdapter,
  websocketHandshaker: WebSocketHandshaker,
  parentActorRef: ActorRef) extends SimpleChannelUpstreamHandler {

  var channel: Option[Channel] = None

  override def messageReceived(ctx: ChannelHandlerContext, msgEvent: MessageEvent) {
    msgEvent.getMessage() match {
      case close: CloseWebSocketFrame => handleCloseSocket()
      case frame: WebSocketFrame => handleWebSocketFrame(ctx, frame)
      case WebSocketPayload(payload) => sendWebSocketFrame(payload)
      case unhandled => {
        logger warning ("Did not get WebSocketFrame: " + unhandled.getClass().toString())
        logger warning ("Unexpected event toString:  " + unhandled.toString)
        //respond with invalid frame msg
        sendWebSocketFrame("BAD REQUEST".getBytes)
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

    Option(websocketHandshaker.getHandshaker(request)) match {
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
    sendWebSocketFrame("SOCKET CREATED".getBytes)
  }

  /**
   * 
   * @param ctx
   * @param frame
   */
  def handleWebSocketFrame(ctx: ChannelHandlerContext, frame: WebSocketFrame) {
    frame match {
      case text: TextWebSocketFrame => {
        val inboundPayload = text.getText.getBytes
        logger info ("WebSocket frame text:  " + inboundPayload)
        parentActorRef ! WebSocketPayload(inboundPayload)
      }
      case ping: PingWebSocketFrame => {
        ctx.getChannel().write(new PongWebSocketFrame(ping.getBinaryData()))
      }
      case closing: CloseWebSocketFrame => handleCloseSocket()
      case somethingElse => {
        logger warning ("Got an unrecognized WebSocketFrame:  " + somethingElse.getClass.toString)
        sendWebSocketFrame("BAD REQUEST".getBytes)
      }
    }
  }

  /**
   * Simple websocket push method.
   *
   * @param payload
   */
  def sendWebSocketFrame(payload: Array[Byte]) {
    channel match {
      case Some(chan) if (chan.isOpen) => chan.write(new TextWebSocketFrame(ChannelBuffers.copiedBuffer(new String(payload), CharsetUtil.UTF_8)))
      case Some(chan) => logger warning ("Trying to push {} with closed channel", new String(payload))
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
                 parentActorRef ! WebSocketClosed
             }
         })
        } else {
          logger warning ("Trying to close a closed channel.")
          parentActorRef ! WebSocketClosed
        }
      }
      case None => {
        logger warning ("Trying to close with no channel.")
        parentActorRef ! WebSocketClosed
      }
    }
  }

}
