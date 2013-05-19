package geidsvig.netty.socket.comet

import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpResponse
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ReceiveTimeout
import akka.actor.Cancellable
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.Implicits.global
 
/**
 * Use this case class when initializing the Comet socket connection.
 */
case class CometRequest(ctx: ChannelHandlerContext, request: HttpRequest)

/**
 * Use this case class when you need to send the response message.
 */
case class CometResponse(response: Array[Byte])

trait CometHandlerRequirements {
  val receiveTimeout: Long
  val responseTimeout: Long
}

// has receive timeout. configured timeout value
// holds connection until either timeout, or message received and send to client
abstract class CometHandler extends Actor with ActorLogging {
  this: CometHandlerRequirements =>

  case class CancellableCometRequest(cancellable: Cancellable, ctx: ChannelHandlerContext, request: HttpRequest)
  case object CometResponseTimeout

  var cancellableCometRequest: Option[CancellableCometRequest] = None

  context.setReceiveTimeout(Duration.create(receiveTimeout, TimeUnit.MILLISECONDS))
  def receive = {
    case CometRequest(ctx, request) => handleCometRequest(ctx, request)
    case CometResponse(response) => handleCometResponse(response)
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
   * Sends a timeout message to the client.
   *  
   */
  def handleResponseTimeout() {
    handleCometResponse("RESPONSE TIMEOUT".getBytes)
  }

  /**
   * Using the cancellableCometRequest to get the channel to response to,
   * send the response message.
   * 
   * @param response
   * @returns true if response send successfully, false otherwise.
   */
  private def handleCometResponse(response: Array[Byte]): Boolean = {
    var sentResponse = false
    cancellableCometRequest match {
      case Some(ccr) => {
        Option(ccr.ctx.getChannel) match {
          case Some(chan) if (chan.isOpen) => {
            chan.write(response)
            sentResponse = true
          }
          case Some(chan) => log warning ("Trying to respond {} with closed channel", new String(response))
          case None => log warning ("Trying to respond with no channel. Dropping")
        }
        handleCancellingCometRequest()
      }
      case None => log warning ("Trying to respond without a comet connection. Dropping.")
    }
    sentResponse
  }

  /**
   * Attempt to cancel the cancellable scheduled event of cancellableCometRequest.
   * Then sets cancellableCometRequest to None.
   */
  private def handleCancellingCometRequest() {
    cancellableCometRequest match {
      case Some(ccr) => {
        try {
          // TODO check if canceling a completed scheduled event will cause an exception
          ccr.cancellable.cancel()
        } catch { case _: Throwable => {} }
        cancellableCometRequest = None
      }
      case None => {}
    }
  }

  /**
   * When a receive timeout event occurs, we want to close the current comet channel.
   * And cancel the comet request. And stop the actor.
   */
  private def handleReceiveTimeout() {
    closeChannel()
    handleCancellingCometRequest()
    context.stop(self)
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

}