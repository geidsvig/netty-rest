package geidsvig.netty.rest.status

import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpResponseStatus

import akka.actor.Actor
import akka.actor.ActorLogging
import geidsvig.netty.rest.ChannelWithRequest
import geidsvig.netty.rest.RestUtils

abstract class StatusHandler extends Actor with ActorLogging with RestUtils {

  def receive = {
    case ChannelWithRequest(ctx, request) => status(ctx, request)
  }

  def status(ctx: ChannelHandlerContext, request: HttpRequest) = {
    log info ("Status : {} {} ", ctx, request)
    val response = createHttpResponse(HttpResponseStatus.OK, callback(request, "OK"))
    sendHttpResponse(ctx, request, response)
  }

}
