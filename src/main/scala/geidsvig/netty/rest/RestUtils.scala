package geidsvig.netty.rest

import scala.collection.JavaConversions.asScalaBuffer
import scala.util.matching.Regex
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel.ChannelFuture
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpResponse
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.handler.codec.http.HttpVersion
import org.jboss.netty.handler.codec.http.QueryStringDecoder
import org.jboss.netty.util.CharsetUtil
import org.jboss.netty.channel.Channel
import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame

trait RestUtils {

  val ContentTypeJson = "application/json"
  val ContentTypeText = "text/plain"

  /**
   * Splits the path of the request param.
   *
   * @param request
   * @returns list of path parts split on "/"
   */
  def splitPath(request: HttpRequest) = {
    val decoder = new org.jboss.netty.handler.codec.http.QueryStringDecoder(request.getUri)
    decoder.getPath.split("/").toList match {
      case List() => List()
      case (head :: tail) => tail // removes first empty element because split on path will always create empty item in location 0
      case _ => List()
    }
  }

  /**
   * Function to validate that a String path matches the given Regex.
   *
   *  @param path
   *  @param regex
   *  @returns true if path fits regex. false otherwise
   */
  def pathMatches(path: String, regex: Regex): Boolean = {
    regex.pattern.matcher(path).matches
  }

  /**
   * Extracts the content body from the request param as UTF-8.
   *
   * @param request
   * @returns content as UTF-8 string.
   */
  def requestBodyAsJSON(request: HttpRequest) = {
    request.getContent.toString(java.nio.charset.Charset.forName("UTF-8"))
  }

  /**
   * Converts string to channel buffer ready content.
   *
   * @param body
   * @returns channel buffer
   */
  def asContent(body: String) = {
    ChannelBuffers.copiedBuffer(body, CharsetUtil.UTF_8)
  }

  /**
   * Handles callbacks by checking for jsonp callback param in request url.
   *
   * @param request
   * @param body the message that is to be returned. Expects JSON format, but does not enforce.
   * @returns the body if no callback is provided. the body with callback wrapper otherwise.
   */
  def callback(request: HttpRequest, body: String) = {
    val params = new QueryStringDecoder(request.getUri).getParameters
    (Option(params.get("callback")) flatMap (_.toList.headOption)) match {
      case Some(callback) =>
        """%s(%s)""" format (callback, body.replace("\"", "\\\""))
      case _ => body
    }
  }

  /**
   * Checks request for callback.
   *
   * @param request
   * @returns true when request has callback. false otherwise.
   */
  def hasCallback(request: HttpRequest) = {
    val params = new QueryStringDecoder(request.getUri).getParameters
    (Option(params.get("callback")) flatMap (_.toList.headOption)) match {
      case Some(callback) => true
      case _ => false
    }
  }

  /**
   * Simple function to respond over HTTP on the channelHandlerContext with request and response.
   * Closes channel after writing to channel.
   *
   * @param ctx
   * @param request
   * @param response
   * @returns option exception
   */
  def sendHttpResponse(ctx: ChannelHandlerContext, request: HttpRequest, response: HttpResponse): Option[Exception] = {
    Option(ctx.getChannel) match {
      case Some(channel) if (channel.isOpen) => {
        ctx.getChannel().write(response).addListener(writeAndCloseFutureListener)
        None
      }
      case _ => {
        Some(new Exception(s"Closed channel. Dropping response ${response.getStatus} for request: ${request.getMethod} ${request.getUri}"))
      }
    }
  }

  def createHttpResponse(status: HttpResponseStatus, msg: String, contentType: String = ContentTypeText) = {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status)
    response.setHeader("Content-Type", contentType + "; charset=UTF-8");
    response.setContent(asContent(msg))
    response
  }

  /**
   * Extracts uuid from request queryString.
   *
   * @param request
   * @returns memberId if found, none otherwise
   */
  def extractUuid(request: HttpRequest) = {
    val decoder = new org.jboss.netty.handler.codec.http.QueryStringDecoder(request.getUri)
    decoder.getParameters.containsKey("uuid") match {
      case true => Some(decoder.getParameters.get("uuid").get(0))
      case false => None
    }
  }

  /**
   * Crafts HTTP response code key for graphite.
   */
  def responseCodeForReporting(operation: String, status: String) = {
    "response." + operation + "." + status
  }

  val writeAndCloseFutureListener = new ChannelFutureListener {
    def operationComplete(cf: ChannelFuture) {
      cf.getChannel().close()
    }
  }

}
