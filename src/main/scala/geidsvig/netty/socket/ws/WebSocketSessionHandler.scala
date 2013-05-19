package geidsvig.netty.socket.ws

import akka.actor.Actor
import akka.actor.PoisonPill
import akka.actor.ActorLogging
import akka.actor.actorRef2Scala

trait WebSocketSessionHandlerRequirements {
  val websocketHandshaker: WebSocketHandshaker
  val webSocketSessionHandlerManager: WebSocketManager
}

/**
 * The session handler delegates websocket setup to a WebSocketHandler, 
 * and provides hooks for that handler to let this actor know when events have occurred.
 *
 * @param uuid a unique user id for the system. Expecting only 1 session handler per uuid.
 */
abstract class WebSocketSessionHandler(uuid: String) extends Actor with ActorLogging {
  this: WebSocketSessionHandlerRequirements =>

    var webSocketHandler: WebSocketHandler = null

    override def preStart() {
      webSocketSessionHandlerManager.registerHandler(uuid)
      webSocketHandler = new WebSocketHandler(log, websocketHandshaker, self)
    }

  def receive = {
    case WebSocketRequest(ctx, request) => webSocketHandler.handleWebsocketHandshake(ctx, request)
    case WebSocketPayload(payload) => handlePayload(payload)
    case WebSocketClosed => handleCloseSocket()
    case other => {
      log warning ("Unsupported operation {}", other.toString())
    }
  }

  def handlePayload(payload: Array[Byte]) = {
    log info ("Payload received : {}", payload.toString)
    //TODO write payload to channel
  }

  def handleCloseSocket() = {
    webSocketSessionHandlerManager.deregisterHandler(uuid)
    self ! PoisonPill
  }

}
