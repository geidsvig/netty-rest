package ca.figmint.netty

import org.jboss.netty.channel.ChannelFuture
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.ExceptionEvent
import org.jboss.netty.channel.MessageEvent
import org.jboss.netty.channel.SimpleChannelUpstreamHandler
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpRequest

trait RestServerRouteHandlerRequirements {
	val logger: akka.event.LoggingAdapter
	val instantiationTime: Long
}

abstract class RestServerRouteHandler extends SimpleChannelUpstreamHandler {
	self: RestServerRouteHandlerRequirements =>

	logger info "RestServerRouteHandler started"
	
	val writeAndCloseListener = new ChannelFutureListener { def operationComplete(cf: ChannelFuture) { cf.getChannel().close() } }
	
	override def messageReceived(ctx: ChannelHandlerContext, msgEvent: MessageEvent) {
		msgEvent.getMessage() match {
			case request: HttpRequest => handleHttpRequest(ctx, request)
			case anythingElse => {
				logger warning("Did not get HttpRequest:  " + anythingElse.getClass().toString())
				logger warning("Unexpected event toString:  " + anythingElse.toString)
				ctx.getChannel().close()
			}
		}
	}
	
	/**
	 * Method left for implementing class to provide.
	 */
	def handleHttpRequest(ctx: ChannelHandlerContext, request: HttpRequest)
	
	private def respond(ctx: ChannelHandlerContext, response: DefaultHttpResponse) = {
		logger info("Sending response.")
		ctx.getChannel().write(response).addListener(writeAndCloseListener)
		logger info("Closed.")
	}
	
	override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
		logger error (e.getCause, "Exception caught in handler!")
		try {
			ctx.getChannel().close()
		}
	}
	
}
