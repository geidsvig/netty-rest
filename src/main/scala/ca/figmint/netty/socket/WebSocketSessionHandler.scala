package ca.figmint.netty.socket

import akka.actor.Actor
import akka.event.LoggingAdapter
import validation.WebSocketRequestValidator
import akka.actor.PoisonPill

trait WebSocketSessionHandlerRequirements {
    val webSocketRequestValidator: WebSocketRequestValidator
    val websocketHandshaker: WebSocketHandshaker
    val webSocketSessionHandlerManager: WebSocketSessionHandlerManager
}

/**
 * The session handler delegates websocket setup to a WebSocketHandler, and provides hooks for that handler to let this actor know when events have occured.
 * 
 * @param uuid a unique user id for the system. Expecting only 1 session handler per uuid.
 */
abstract class WebSocketSessionHandler(uuid: String) extends Actor {
    this: WebSocketSessionHandlerRequirements =>
        
    val logger: LoggingAdapter = context.system.log
        
    val webSocketHandler = new WebSocketHandler(logger, webSocketRequestValidator, websocketHandshaker, self)

    def receive = {
        case WebSocketHandshake(ctx, request) => webSocketHandler.handleWebsocketHandshake(ctx, request)
        case WebSocketPayload(payload) => handlePayload(payload)
        case WebSocketClosed => handleCloseSocket()
        case other => {
            logger warning ("Unsupported operation {}", other.toString())
        }
    }
    
    def handlePayload(payload: String) = {
        logger info("Payload received : {}", payload)
    }
    
    def handleCloseSocket() = {
       webSocketSessionHandlerManager.removeWebSocketSessionHandler(uuid)
       self ! PoisonPill
    }
    
}
