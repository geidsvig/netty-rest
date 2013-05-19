package org.jboss.netty.channel

import org.jboss.netty.handler.codec.http.websocketx.TextWebSocketFrame
import org.jboss.netty.handler.codec.http.HttpResponse

/**
 * A channel can handle [[Any]] object type. For unit testing purposes it is easy
 * to separate the websocket and http/comet channel handling to assert results.
 */
class MockChannel extends Channel {

  var websocketContent: TextWebSocketFrame = null
  var httpContent: HttpResponse = null

  def write(x: Any, y: java.net.SocketAddress) = {
    setContent(x)
    new MockChannelFuture
  }
  def write(x: Any) = {
    setContent(x)
    new MockChannelFuture
  }
  def setContent(x: Any) = x match {
    case frame: TextWebSocketFrame => websocketContent = frame
    case http: HttpResponse => httpContent = http
  }

  def setAttachment(attach: Any) {}
  def getAttachment() = { null }
  def setReadable(x: Boolean) = { null }
  def setInterestOps(x: Int) = { null }
  def isWritable = true
  def isReadable = true
  def isConnected = true
  def isBound = true
  def isOpen = true
  def getInterestOps = 0
  def getCloseFuture = { null }
  def close = null
  def unbind = null
  def disconnect = null
  def connect(x: java.net.SocketAddress) = null
  def bind(x: java.net.SocketAddress) = null
  def getRemoteAddress = null
  def getLocalAddress = null
  def getPipeline = null
  def getConfig = null
  def getParent = null
  def getFactory = null
  def getId = 0
  def compareTo(x: Channel) = 0

}
