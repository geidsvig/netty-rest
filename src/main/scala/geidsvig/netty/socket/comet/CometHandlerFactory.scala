package geidsvig.netty.socket.comet

import akka.actor.ActorRef

abstract class CometHandlerFactory {

  /**
   * 
   * @param uuid a unique user id for the system. Expecting only 1 session handler per uuid.
   * @returns ActorRef for a CometHandler
   */ 
  def createCometHandler(uuid: String): ActorRef

}