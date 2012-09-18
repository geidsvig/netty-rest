package ca.figmint.netty

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

import akka.actor.Actor
import akka.event.LoggingAdapter
			
/**
 * It is expected that this trait is used by the RestServerRouteHandler's implementing class.
 * 
 */
trait RestUtils {
	self: Actor =>
		
	val logger: LoggingAdapter
	val writeAndCloseListener = new ChannelFutureListener {
		def operationComplete(cf: ChannelFuture) {
			logger.debug("Write completed")
			cf.getChannel().close()
			logger.debug("Channel close requested")
		}
	}

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
			case list => list tail // removes first empty element because split on path will always create empty item in location 0
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
		path match {
			case x if regex.pattern.matcher(x).matches => true
			case _ => false
		}
	}
	
	/**
	 * Extracts the content body from the request param as UTF-8.
	 * 
	 * @param request
	 * @returns content as UTF-8 string.
	 */
	def requestBodyAsJSON(request: HttpRequest) = {
		request.getContent.toString("UTF-8")
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
	 * Simple function to response over HTTP on the channelHandlerContext with request and response.
	 * Closes channel after writing to channel.
	 * 
	 * @param ctx
	 * @param request
	 * @param response
	 */
	def sendHttpResponse(ctx: ChannelHandlerContext, request: HttpRequest, response: HttpResponse) {
		logger info("Sending response: {}", response.getStatus)
		if(ctx.getChannel().isOpen())
			ctx.getChannel().write(response).addListener(writeAndCloseListener)
		else
			logger debug("Closed channel. Dropping response {} for request: {} {} ", response.getStatus, request.getMethod, request.getUri)
	}
	
	val ContentTypeJson = "application/json"
	val ContentTypeText = "text/plain"
	def createHttpResponse(status: HttpResponseStatus, msg: String, contentType: String = ContentTypeText) = {
		val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status)
		response.setHeader("Content-Type", contentType + "; charset=UTF-8");
		response.setContent(asContent(msg))
		response
	}
	
	/**
	 * Extracts memberId from request queryString.
	 * 
	 * @param request
	 * @returns memberId if found, none otherwise
	 */
	def extractMemberId(request: HttpRequest) = {
		val decoder = new org.jboss.netty.handler.codec.http.QueryStringDecoder(request.getUri)
		decoder.getParameters.containsKey("memberId") match {
			case true => Some(decoder.getParameters.get("memberId").get(0))
			case false => None
		}
	}
	
	/**
	 * Crafts HTTP response code key for graphite.
	 */
	def responseCodeForReporting(operation: String, status: String) = {
		"response." + operation + "." + status
	}
	
}
