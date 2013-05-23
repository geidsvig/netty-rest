package geidsvig.netty.rest

import scala.util.matching.Regex
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.Set
import akka.actor.actorRef2Scala
import akka.event.LoggingAdapter
import akka.actor.ActorRef
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
import geidsvig.netty.socket.ws.WebSocketRequest
import geidsvig.netty.socket.ws.WebSocketManager
import geidsvig.netty.socket.ws.WebSocketRequest
import geidsvig.netty.socket.comet.CometManager
import geidsvig.netty.socket.comet.CometRequest

case class RestHttpRequest(ctx: ChannelHandlerContext, request: HttpRequest)
case class RestPathHandler(httpMethod: HttpMethod, regex: Regex, actorRef: ActorRef)

trait RestRouteHandlerRequirements {
  val logger: LoggingAdapter
  val pathsAndHandlers: Set[RestPathHandler]
  val cometManager: CometManager
  val webSocketManager: WebSocketManager
}

abstract class RestRouteHandler extends SimpleChannelUpstreamHandler 
  with RestUtils {
  self: RestRouteHandlerRequirements =>

  val cometPath = new Regex("/comet")
  val websocketPath = new Regex("/websocket")

  /**
   * @param ctx
   * @param msgEvent
   */
  override def messageReceived(ctx: ChannelHandlerContext, msgEvent: MessageEvent) {
    msgEvent.getMessage() match {
      case request: HttpRequest => handleHttpRequest(ctx, request)
      case anythingElse => {
        logger warning ("Did not get HttpRequest: " + anythingElse.getClass().toString())
        logger warning ("Unexpected event toString:  " + anythingElse.toString)
        ctx.getChannel().close()
      }
    }
  }

  /**
   * 
   * @param ctx
   * @param request
   */
  def handleHttpRequest(ctx: ChannelHandlerContext, request: HttpRequest) {
    val method = request.getMethod
    val decoder = new org.jboss.netty.handler.codec.http.QueryStringDecoder(request.getUri)
    val path = decoder.getPath

    logger info (method + " " + request.getUri)

    /*
     * Support for configurable paths and handlers.
     */
    var handled = false
    pathsAndHandlers map { pathHandler =>
      logger info ("checking {} {} {}", pathHandler.httpMethod, pathHandler.regex, pathHandler.actorRef)
      (method, path) match {
        case (httpMethod, requestedPath) if pathMatches(requestedPath, pathHandler.regex) => {
          logger info ("matching handler found for {} {} {}", httpMethod, pathHandler.regex, pathHandler.actorRef)
          pathHandler.actorRef ! RestHttpRequest(ctx, request)
          handled = true
        }
        case (_, _) => {}
      }
    }

    if (!handled) {
      // check if the request is for comet or websocket
      (method, path) match {
        case (httpMethod, requestedPath) if pathMatches(requestedPath, cometPath) => {
          cometManager.handleCometRequest(CometRequest(ctx, request))
          handled = true
        }
        case (httpMethod, requestedPath) if pathMatches(requestedPath, websocketPath) => {
          webSocketManager.handleWebSocketRequest(WebSocketRequest(ctx, request))
          handled = true
        }
        case (_,_) => {}
      }
    }

    if (!handled) {
      logger info ("no handler found")
      val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
      response.setContent(ChannelBuffers.copiedBuffer("Unrecognized " + method + " " + path, CharsetUtil.UTF_8))
      sendHttpResponse(ctx, request, response)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    logger error (e.getCause, "Exception caught in handler!")
    try {
      ctx.getChannel().close()
    }
  }

}
