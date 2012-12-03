package ca.figmint.netty.socket

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
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame
import org.jboss.netty.handler.codec.http.HttpHeaders
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.util.CharsetUtil

import akka.actor.actorRef2Scala
import akka.actor.ActorRef
import akka.event.LoggingAdapter
import ca.figmint.netty.socket.validation.WebSocketRequestValidator

case class WebSocketHandshake(ctx: ChannelHandlerContext, request: HttpRequest)

case class WebSocketRequest(channel: Channel)

case class WebSocketPayload(payload: String)

case object WebSocketClosed

// Would have liked this to be a trait, but cannot mix SimpleChannelUpstreamHandler with scala Object...
// Validator used to perform application specific steps. eg. security and data type and content.
class WebSocketHandler(logger: LoggingAdapter,
    webSocketRequestValidator: WebSocketRequestValidator,
    websocketHandshaker: WebSocketHandshaker,
    parentActorRef: ActorRef) extends SimpleChannelUpstreamHandler {
	
	var channel: Option[Channel] = None
	
	override def messageReceived(ctx: ChannelHandlerContext, msgEvent: MessageEvent) {
        msgEvent.getMessage() match {
            case frame: WebSocketFrame => handleWebSocketFrame(ctx, frame)
            case anythingElse => {
                logger warning("Did not get WebSocketFrame: " + anythingElse.getClass().toString())
                logger warning("Unexpected event toString:  " + anythingElse.toString)
                ctx.getChannel().close()
            }
        }
    }
	
	def handleWebsocketHandshake(ctx: ChannelHandlerContext, request: HttpRequest) {
	    webSocketRequestValidator.validate(request) match {
            case true => {
                val protocol = request.getHeader(HttpHeaders.Names.WEBSOCKET_PROTOCOL)
                    logger info("WebSocket protocol:  " + protocol)
                    
                    val wsLocation = "ws://" + request.getHeader(HttpHeaders.Names.HOST) + "/websocket"
                    val factory = new WebSocketServerHandshakerFactory(wsLocation, null, false)
                    
                    Option(websocketHandshaker.getHandshaker(request)) match {
                        case None => {
                            logger error("Unsupported WebSocket version")
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
            case false => logger error("Invalid request : {}", request)
        }
    }
	
    def handleWebSocketFrame(ctx: ChannelHandlerContext, frame: WebSocketFrame) {
        frame match {
            case text: TextWebSocketFrame => {
                val inboundPayload = text.getText()
                logger info("WebSocket frame text:  " + inboundPayload)
                parentActorRef ! WebSocketPayload(inboundPayload)
            }
            case ping: PingWebSocketFrame => {
                ctx.getChannel().write(new PongWebSocketFrame(ping.getBinaryData()))
            }
            case closing: CloseWebSocketFrame => handleCloseSocket()
            case somethingElse => {                     
                logger warning("Got an unrecognized WebSocketFrame:  " + somethingElse.getClass.toString)
                sendWebSocketFrame("BAD REQUEST")
            }
        }
    }
    
    /**
     * Can be used by implementing class to extend method and trigger death of parent actor.
     */
    def handleCloseSocket() = {
        logger info("WebSocket close.")
        channel match {
            case Some(chan) => {
                if (chan.isOpen) {
                    chan.close()
                } else {
                    logger warning ("Trying to close a closed channel.")
                }
            }
            case None => logger warning ("Trying to close with no channel.")
        }
        parentActorRef ! WebSocketClosed
    }

    /**
     * Simple websocket push method.
     * 
     * @param channel to push payload across.
     * @param payload
     */
    def sendWebSocketFrame(payload: String) {
        val frame = new TextWebSocketFrame(ChannelBuffers.copiedBuffer(payload, CharsetUtil.UTF_8))

        channel match {
            case Some(chan) => {
                if (chan.isOpen) {
                    chan.write(frame)
                } else {
                    logger warning ("Trying to push {} with closed channel", payload)
                }
            }
            case None => logger warning ("Trying to push with no channel. Dropping")
        }
    }
    
}
