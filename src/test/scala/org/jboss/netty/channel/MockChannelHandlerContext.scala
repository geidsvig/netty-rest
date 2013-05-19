package org.jboss.netty.channel

class MockChannelHandlerContext(mockChannel: MockChannel) extends ChannelHandlerContext {
  def getChannel() = mockChannel

  def getName() = ""
  def getPipeline() = null
  def canHandleDownstream(): Boolean = false
  def canHandleUpstream(): Boolean = false
  def sendDownstream(event: ChannelEvent) {}
  def sendUpstream(event: ChannelEvent) {}
  def sendDownstream(attachment: Any) {}
  def getHandler(): ChannelHandler = null
  def setAttachment(attachment: Any) {}
  def getAttachment() = null
}