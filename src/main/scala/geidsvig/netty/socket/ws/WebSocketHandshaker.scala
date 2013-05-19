package geidsvig.netty.socket.ws

import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpHeaders
import scala.util.matching.Regex

abstract class WebSocketHandshaker {

  val handshakerFactories = scala.collection.mutable.Map[String, WebSocketServerHandshakerFactory]()

  val secureDomain: Regex

  def getHandshaker(request: HttpRequest) = getWebSocketLocation(request).newHandshaker(request)

  protected def getWebSocketLocation(request: HttpRequest) = {

    val location = "%s%s%s".format(
      (Option(request.getHeader("Origin")) map { origin =>
        if (secureDomain findFirstIn origin isDefined) "wss://" else "ws://"
      }).getOrElse("ws://"),
      request.getHeader(HttpHeaders.Names.HOST),
      request.getUri())

    handshakerFactories.get(location) match {
      case None => {
        /*
         * location
         * subprotocols = null
         * allowExtensions = true
         */
        handshakerFactories(location) = new WebSocketServerHandshakerFactory(location, null, true)
        handshakerFactories(location)
      }
      case Some(factory) => factory
    }
  }

}
