package ca.figmint.netty.rest.status

import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpResponseStatus

import akka.actor.Actor
import akka.event.LoggingAdapter
import ca.figmint.netty.rest.RestUtils

case class StatusRequest(ctx: ChannelHandlerContext, request: HttpRequest)

abstract class StatusHandler extends Actor with RestUtils {

	val logger: LoggingAdapter

	def receive = {
		case event: StatusRequest => status(event.ctx, event.request)
	}

	def status(ctx: ChannelHandlerContext, request: HttpRequest) = {
		val response = createHttpResponse(HttpResponseStatus.OK, callback(request, "OK"))
		sendHttpResponse(ctx, request, response)
	}

}
