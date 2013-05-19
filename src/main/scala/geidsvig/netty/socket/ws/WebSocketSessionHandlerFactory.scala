package geidsvig.netty.socket.ws

import akka.actor.ActorRef

abstract class WebSocketSessionHandlerFactory {

  /**
   * 
   * @returns ActorRef for a WebSocketSessionHandler
   */ 
  def createWebSocketSessionHandler(): ActorRef

}