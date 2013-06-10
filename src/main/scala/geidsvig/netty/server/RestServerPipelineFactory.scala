package geidsvig.netty.server

import org.jboss.netty.channel.ChannelPipelineFactory
import org.jboss.netty.channel.Channels
import org.jboss.netty.handler.codec.http.HttpChunkAggregator
import org.jboss.netty.handler.codec.http.HttpRequestDecoder
import org.jboss.netty.handler.codec.http.HttpResponseEncoder
import geidsvig.netty.rest.RestRouteHandler
import org.jboss.netty.handler.stream.ChunkedWriteHandler
import org.jboss.netty.handler.codec.string.StringDecoder
import org.jboss.netty.util.CharsetUtil
import org.jboss.netty.handler.codec.string.StringEncoder

trait RestServerPipelineFactorRequirements {
  val logger: akka.event.LoggingAdapter
  val routeHandler: RestRouteHandler
  val chunkSize: Int
}

abstract class RestServerPipelineFactory extends ChannelPipelineFactory {
  self: RestServerPipelineFactorRequirements =>

  def getPipeline() = {
    val pipeline = Channels.pipeline()

    pipeline.addLast("decoder", new HttpRequestDecoder)
    pipeline.addLast("aggregator", new HttpChunkAggregator(chunkSize))
    pipeline.addLast("encoder", new HttpResponseEncoder)
    pipeline.addLast("handler", routeHandler)

    logger info "Pipeline created"

    pipeline
  }
}
