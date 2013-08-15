package geidsvig.netty.socket.comet

import java.util.concurrent.TimeUnit
import scala.collection.JavaConversions.asScalaBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.handler.codec.http.HttpVersion
import org.jboss.netty.handler.codec.http.QueryStringDecoder
import org.jboss.netty.util.CharsetUtil
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Cancellable
import akka.actor.ReceiveTimeout
import geidsvig.netty.rest.ChannelWithRequest
import org.jboss.netty.channel.ChannelFutureListener

/**
 * A case class to wrap a response status and JSON message.
 *
 * @param statusCode an HTTP status code
 * @param content JSON formatted content
 */
case class CometPacket(responseStatus: HttpResponseStatus, message: String) {
  /**
   * This helper method wraps a status code and JSON content into a single response string.
   */
  def toJson() = s"""{"response":{"status":${responseStatus.getCode},"message":${message}}}"""
}

object CometResponse {

  /**
   * Create javascript jsonp based long polling comet response.
   *
   * @param responseStatus
   * @param callback
   * @param content
   */
  def createResponse(responseStatus: HttpResponseStatus, request: HttpRequest, packet: CometPacket) = {
    val decoder = new QueryStringDecoder(request.getUri)
    val params = decoder.getParameters
    val callback = params.containsKey("callback") match {
      case true => params.get("callback").headOption
      case false => None
    }

    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, responseStatus)
    response.setHeader("Content-Type", "text/javascript")
    val text = callback match {
      case Some(cb) => s"${cb}(${packet.toJson})"
      case None => packet.toJson
    }
    response.setContent(ChannelBuffers.copiedBuffer(text, CharsetUtil.UTF_8))
    response
  }

}

trait CometHandlerRequirements {
  val receiveTimeout: Long
  val responseTimeout: Long
  val cometManager: CometManager
}

/**
 * http://en.wikipedia.org/wiki/Comet_(programming)
 * Long Polling Comet Handler:
 * Script tag long polling.
 *
 * http://objectcloud.kicks-ass.net/Docs/Specs/Comet%20Protocol.page
 *
 * has receive timeout. configured timeout value
 * holds connection until either timeout, or message received and send to client.
 * actor lives until receiveTimeout occurs.
 *
 * @param uuid a unique user id for the system. Expecting only 1 session handler per uuid.
 */
abstract class CometHandler(uuid: String) extends Actor with ActorLogging {
  this: CometHandlerRequirements =>

  val startTime = System.currentTimeMillis

  case class CancellableCometRequest(cancellable: Cancellable, ctx: ChannelHandlerContext, request: HttpRequest)
  case object CometResponseTimeout

  var cancellableCometRequest: Option[CancellableCometRequest] = None

  override def preStart() {
    cometManager.registerHandler(uuid, self)
  }

  override def postStop() {
    cometManager.deregisterHandler(uuid)
  }

  // set default receiveTimeout because trait injected timeout value is not loaded yet
  context.setReceiveTimeout(Duration.create(5000, TimeUnit.MILLISECONDS))
  def receive = {
    case ChannelWithRequest(ctx, request) => {
      context.setReceiveTimeout(Duration.create(receiveTimeout, TimeUnit.MILLISECONDS))
      handleCometRequest(ctx, request)
    }
    case CometResponseTimeout => handleResponseTimeout()
    case ReceiveTimeout => handleReceiveTimeout()
    case other => log debug ("Unsupported message {}", other)
  }

  /**
   * This method registers the cancellableCometRequest and schedules the response timeout.
   * Then the request param is handed off to the handleRequest method.
   *
   * @param ctx
   * @param request
   */
  private def handleCometRequest(ctx: ChannelHandlerContext, request: HttpRequest) {
    cancellableCometRequest = Some(CancellableCometRequest(
      context.system.scheduler.scheduleOnce(Duration.create(responseTimeout, TimeUnit.MILLISECONDS), self, CometResponseTimeout),
      ctx, request))

    handleRequest(request)
  }

  /**
   * An abstract method to be dealt with by the implementing class.
   *
   * @param request
   */
  def handleRequest(request: HttpRequest)

  /**
   * Use this method to send a response back to the client.
   *
   * @param responseStatus
   * @param content
   */
  def sendResponse(responseStatus: HttpResponseStatus, content: String) {
    handleCometResponse(CometPacket(responseStatus, content))
  }

  /**
   * Sends a timeout message to the client.
   *
   */
  def handleResponseTimeout() {
    handleCometResponse(CometPacket(HttpResponseStatus.REQUEST_TIMEOUT, "response timeout"))
  }

  /**
   * Using the cancellableCometRequest to get the channel to response to,
   * send the response message.
   *
   * @param packet
   */
  private def handleCometResponse(packet: CometPacket) {
    cancellableCometRequest match {
      case Some(ccr) => {
        Option(ccr.ctx.getChannel) match {
          case Some(chan) if (chan.isOpen) => {
            val response = CometResponse.createResponse(packet.responseStatus, ccr.request, packet)
            log info (s"Responding with ${response}")
            chan.write(response).addListener(ChannelFutureListener.CLOSE)
          }
          case Some(chan) => log warning ("Trying to respond {} with closed channel", packet.toJson)
          case None => log warning ("Trying to respond with no channel. Dropping")
        }
        handleCancellingCometRequest()
      }
      case None => log warning ("Trying to respond without a comet connection. Dropping.")
    }
  }

  /**
   * Attempt to cancel the cancellable scheduled event of cancellableCometRequest.
   * Then sets cancellableCometRequest to None.
   */
  private def handleCancellingCometRequest() {
    cancellableCometRequest match {
      case Some(ccr) => {
        // can be cancelled infinite times. will only change to isCancelled=true with no side effects
        ccr.cancellable.cancel()
        cancellableCometRequest = None
      }
      case None => {}
    }
  }

  /**
   * Closes the current channel, if one exists.
   */
  private def closeChannel() {
    cancellableCometRequest match {
      case Some(ccr) => {
        Option(ccr.ctx.getChannel) match {
          case Some(chan) if (chan.isOpen) => chan.close()
          case _ => {}
        }
      }
      case None => {}
    }
  }

  /**
   * When a receive timeout event occurs, we want to close the current comet channel.
   * And cancel the comet request. And stop the actor.
   */
  private def handleReceiveTimeout() {
    val endTime = System.currentTimeMillis
    log info ("Comet Handler timeout after {}ms", (endTime - startTime))
    closeChannel()
    handleCancellingCometRequest()
    context.stop(self)
  }

}
