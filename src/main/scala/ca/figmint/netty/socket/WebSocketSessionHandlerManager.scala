package ca.figmint.netty.socket

import akka.actor.ActorRef

trait WebSocketSessionHandlerManagerRequirements {
    val webSocketHandlerFactory: WebSocketHandlerFactory
}

abstract class WebSocketSessionHandlerManager {
    this: WebSocketSessionHandlerManagerRequirements =>
    
    /**
     * Implementation can vary. 
     * examples:
     * 1) singleton can store a key-value map of uuid and actorRefs
     * 2) distrubuted system can use guardian ring to distribute handlers
     */
    def findOrCreateWebSocketSessionHandler(request: WebSocketRequest): ActorRef
    
    /**
     * Regardless of implementations, the uuid and actorRef for WebSocketSessionHandler should be removed.
     * 
     * @param uuid
     */
    def removeWebSocketSessionHandler(uuid: String)
    
}