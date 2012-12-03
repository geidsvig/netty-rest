package ca.figmint.netty.rest

import scala.util.matching.Regex
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ExceptionEvent
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpMethod
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.handler.codec.http.HttpVersion
import org.jboss.netty.util.CharsetUtil
import akka.actor.actorRef2Scala
import akka.actor.ActorRef
import ca.figmint.netty.rest.status.StatusRequest
import ca.figmint.netty.rest.validation.RestRequestValidator
import ca.figmint.netty.socket.WebSocketSessionHandlerManager
import ca.figmint.netty.socket.WebSocketHandshake
import ca.figmint.netty.socket.WebSocketRequest
import scala.collection.Set

case class RestHttpRequest(ctx: ChannelHandlerContext, request: HttpRequest)

trait RestRouteHandlerRequirements {
    val logger: akka.event.LoggingAdapter
    val instantiationTime: Long
    val apiStatusPath: Regex
    val websocketPath: Regex
    val apiHandler: ActorRef
    val pathsAndHandlers: Set[(HttpMethod, Regex, ActorRef)]
    val restRequestValidator: RestRequestValidator
    val webSocketSessionManager: WebSocketSessionHandlerManager
}

abstract class RestRouteHandler extends SimpleChannelUpstreamHandler with RestUtils {
	self: RestRouteHandlerRequirements =>
	
	override def messageReceived(ctx: ChannelHandlerContext, msgEvent: MessageEvent) {
        msgEvent.getMessage() match {
            case request: HttpRequest => handleHttpRequest(ctx, request)
            case anythingElse => {
                logger warning("Did not get HttpRequest: " + anythingElse.getClass().toString())
                logger warning("Unexpected event toString:  " + anythingElse.toString)
                ctx.getChannel().close()
            }
        }
    }
	
	def handleHttpRequest(ctx: ChannelHandlerContext, request: HttpRequest) {
		val method = request.getMethod
		val decoder = new org.jboss.netty.handler.codec.http.QueryStringDecoder(request.getUri)
		
		logger info (method + " " + request.getUri)
		
		/*
		 * Support for configurable paths and handlers.
		 */
		var handled = false
		pathsAndHandlers map { pathAndHandler =>
		    val (httpMethod, regex, actorRef) = (pathAndHandler._1, pathAndHandler._2, pathAndHandler._3)
		    (method, decoder.getPath) match {
                case (httpMethod, requestedPath) if pathMatches(requestedPath, regex) => {
                    actorRef ! RestHttpRequest(ctx, request)
                    handled = true
                }
		    }
		}
		
		if (!handled) {
    		/*
    		 * case matches for REST methods and regex paths go here.
    		 * requests are delegated to handlers (akka actors).
    		 */
    		(method, decoder.getPath) match {
    			case (HttpMethod.GET, path) if pathMatches(path, apiStatusPath) => {
    				apiHandler ! StatusRequest(ctx, request)
    			}
    			case (HttpMethod.GET, path) if pathMatches(path, websocketPath) => {
    			    val webSocketSessionHandler = webSocketSessionManager.findOrCreateWebSocketSessionHandler(WebSocketRequest(ctx.getChannel()))
    				webSocketSessionHandler ! WebSocketHandshake(ctx, request)
    			}
    			case (_, uri) => {
    				val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
    				response.setContent(ChannelBuffers.copiedBuffer("Unrecognized " + method + " " + uri, CharsetUtil.UTF_8))
    				sendHttpResponse(ctx, request, response)
    			}
    		}
		}
	}

	override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
        logger error (e.getCause, "Exception caught in handler!")
        try {
            ctx.getChannel().close()
        }
    }
	
}
