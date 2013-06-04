package geidsvig.netty.rest

import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef
import akka.event.LoggingAdapter
import scala.util.matching.Regex
import geidsvig.netty.socket.comet.CometManager
import geidsvig.netty.socket.ws.WebSocketManager
import geidsvig.netty.socket.comet.CometHandlerFactory
import geidsvig.netty.socket.comet.CometManagerRequirements
import geidsvig.netty.socket.ws.WebSocketSessionFactory
import geidsvig.netty.socket.ws.WebSocketManagerRequirements
import geidsvig.netty.rest.status.StatusHandler
import org.scalatest.FunSpec
import org.scalatest.GivenWhenThen
import org.jboss.netty.channel.ChannelHandlerContext
import org.jboss.netty.channel.MockChannelHandlerContext
import org.jboss.netty.channel.MockChannelHandlerContext
import org.jboss.netty.channel.MockChannel
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.DefaultHttpRequest
import org.jboss.netty.handler.codec.http.HttpMethod
import org.jboss.netty.handler.codec.http.HttpVersion
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.util.CharsetUtil
import akka.testkit.TestKit
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

/**
 * Mock classes and traits to configure a RestRoutHandler for testing.
 */
object RestRouteHandlerTest {
  val testSystem = ActorSystem("testSystem")

  def voidActor = testSystem.actorOf(Props(new Actor() {
    def receive = { case _ => {} }
  }))

  class TestStatusHandler extends StatusHandler

  val statusHandler = testSystem.actorOf(Props(new TestStatusHandler))

  /**
   * Testing matching of statusHandler to GET /status.
   */
  val testPathsAndHandlers: Set[RestPathHandler] = Set(
    RestPathHandler(HttpMethod.GET, new Regex("""/status"""), statusHandler))

  class MockCometHandlerFactory extends CometHandlerFactory {
    def createCometHandler(uuid: String): ActorRef = voidActor
  }

  trait TestCometManagerDependencies extends CometManagerRequirements {
    val cometHandlerFactory: CometHandlerFactory = new MockCometHandlerFactory
    val logger: LoggingAdapter = testSystem.log
  }

  class MockCometManager extends CometManager with TestCometManagerDependencies {
    def hasRegisteredHandler(uuid: String): Option[ActorRef] = None
    def registerHandler(uuid: String) {}
    def deregisterHandler(uuid: String) {}
  }

  class MockWebSocketHandlerFactory extends WebSocketSessionFactory {
    def createWebSocketHandler(uuid: String): ActorRef = voidActor
  }

  trait TestWebSocketManagerDependencies extends WebSocketManagerRequirements {
    val webSocketSessionFactory: WebSocketSessionFactory = new MockWebSocketHandlerFactory
    val logger: LoggingAdapter = testSystem.log
  }

  class MockWebSocketManager extends WebSocketManager with TestWebSocketManagerDependencies {
    def hasRegisteredHandler(uuid: String): Option[ActorRef] = None
    def registerHandler(uuid: String) {}
    def deregisterHandler(uuid: String) {}
  }

  trait TestRestRouteHandlerDependencies extends RestRouteHandlerRequirements {
    val logger: LoggingAdapter = testSystem.log
    val pathsAndHandlers: Set[RestPathHandler] = testPathsAndHandlers
    val cometManager: CometManager = new MockCometManager
    val webSocketManager: WebSocketManager = new MockWebSocketManager
    val cometEnabled: Boolean = true
    val websocketEnabled: Boolean = true
  }

  class TestRestRouteHandler extends RestRouteHandler with TestRestRouteHandlerDependencies

}

class RestRouteHandlerTest extends FunSpec with GivenWhenThen {
  import RestRouteHandlerTest._

  describe("A RestRouteHandler") {

    it("Should return 404 when route not found") {
      Given("a routeHandler and test context")
      val routeHandler = new TestRestRouteHandler
      val ctx: MockChannelHandlerContext = new MockChannelHandlerContext(new MockChannel)

      When("an HttpRequest for a route that does not have a defined path handler")
      val request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/unsupported");
      routeHandler.handleHttpRequest(ctx, request)

      Then("returns 404")
      val result = ctx.getChannel().httpContent
      val expected = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
      assert(result.getStatus() == expected.getStatus())
    }

    it("Should return 200 'OK' when route found for /status") {
      Given("a routeHandler and test context")
      val routeHandler = new TestRestRouteHandler
      val ctx: MockChannelHandlerContext = new MockChannelHandlerContext(new MockChannel)

      When("an HttpRequest for /status route that has a defined path handler")
      val request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/status");
      routeHandler.handleHttpRequest(ctx, request)

      Then("returns 200 'OK'")
      // because handled by an actor, we need to wait
      TestKit.awaitCond(
        ctx.getChannel().httpContent != null, Duration.create(3000, TimeUnit.MILLISECONDS),
        Duration.create(500, TimeUnit.MILLISECONDS), false)
      val result = ctx.getChannel().httpContent
      
      val expected = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
      expected.setHeader("Content-Type", "text/plain; charset=UTF-8");
      expected.setContent(ChannelBuffers.copiedBuffer("OK", CharsetUtil.UTF_8))
      println("result " + result)
      println("expected " + expected)
      assert(result.getStatus() == expected.getStatus())

    }

  }

}
