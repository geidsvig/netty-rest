package geidsvig.netty.socket.comet

import akka.actor.ActorRef

abstract class CometHandlerFactory {

  /**
   * 
   * @returns ActorRef for a CometHandler
   */ 
  def createCometHandler(): ActorRef

}