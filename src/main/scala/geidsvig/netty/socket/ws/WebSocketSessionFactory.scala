package geidsvig.netty.socket.ws

import akka.actor.ActorRef

abstract class WebSocketSessionFactory {

  /**
   * 
   * @param uuid a unique user id for the system. Expecting only 1 session handler per uuid.
   * @returns ActorRef for a WebSocketHandler
   */ 
  def createWebSocketHandler(uuid: String): ActorRef

}