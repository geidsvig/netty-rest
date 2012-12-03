package ca.figmint.netty.socket

import akka.actor.ActorRef

abstract class WebSocketHandlerFactory {

    // returns ActorRef for WebSocketHandler type
    def createWebSocketHandler(request: WebSocketRequest): ActorRef
    
}
