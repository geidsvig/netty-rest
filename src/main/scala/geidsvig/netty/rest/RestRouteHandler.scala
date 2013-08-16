package geidsvig.netty.rest

import scala.collection.Set
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
import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import akka.event.LoggingAdapter
import geidsvig.netty.socket.comet.CometManager
import geidsvig.netty.socket.ws.WebSocketManager

case class ChannelWithRequest(ctx: ChannelHandlerContext, request: HttpRequest)

case class RestPathHandler(httpMethod: HttpMethod, regex: Regex, actorRef: ActorRef)

trait RestRouteHandlerRequirements {
  val logger: LoggingAdapter
  val pathsAndHandlers: Set[RestPathHandler]
  val cometManager: CometManager
  val webSocketManager: WebSocketManager
  val cometEnabled: Boolean
  val websocketEnabled: Boolean
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
      //logger info ("checking {} {} {}", pathHandler.httpMethod, pathHandler.regex, pathHandler.actorRef)
      if (pathHandler.httpMethod == method && pathMatches(path, pathHandler.regex)) {
          logger info ("matching handler found for {} {} {}", method, pathHandler.regex, pathHandler.actorRef)
          pathHandler.actorRef ! ChannelWithRequest(ctx, request)
          handled = true
      }
    }

    if (!handled) {
      // check if the request is for comet or websocket
      if (HttpMethod.GET == method && pathMatches(path, cometPath) && cometEnabled) {
        cometManager.handleCometRequest(ChannelWithRequest(ctx, request))
        handled = true
      } else if (HttpMethod.GET == method && pathMatches(path, websocketPath) && websocketEnabled) {
        webSocketManager.handleWebSocketRequest(ChannelWithRequest(ctx, request))
        handled = true
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
