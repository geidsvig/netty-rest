package ca.figmint.netty.socket

import org.jboss.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory
import org.jboss.netty.handler.codec.http.HttpRequest
import org.jboss.netty.handler.codec.http.HttpHeaders
import scala.util.matching.Regex

abstract class WebSocketHandshaker {

	val handshakerFactories = scala.collection.mutable.Map[String, WebSocketServerHandshakerFactory]()

	val secureDomain: Regex
	
	protected def getWebSocketLocation(request: HttpRequest) = {
		 
			val location = (Option(request.getHeader("Origin")) map { origin => 
				if (secureDomain findFirstIn origin isDefined)
					"wss://"
				else
					"ws://"
			}).getOrElse("ws://") + request.getHeader(HttpHeaders.Names.HOST) + request.getUri()
			
			
		handshakerFactories.get(location) match {
			case None => {
				handshakerFactories(location) = new WebSocketServerHandshakerFactory(location,
						null, /* subprotocols */
						true /* allowExtensions */ )
				handshakerFactories(location)
			}
			case Some(factory) => factory
		}
	}
	
	def getHandshaker(request: HttpRequest) = getWebSocketLocation(request).newHandshaker(request)
	
}
