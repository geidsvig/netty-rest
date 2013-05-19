package org.jboss.netty.channel

import java.util.concurrent.TimeUnit

class MockChannelFuture extends ChannelFuture {

  def addListener(futureListener: ChannelFutureListener) {}
  def await(duration: Long): Boolean = true
  def await(duration: Long, timeout: TimeUnit): Boolean = true
  def await(): ChannelFuture = null
  def awaitUninterruptibly(duration: Long): Boolean = true
  def awaitUninterruptibly(duration: Long, timeout: TimeUnit): Boolean = true
  def awaitUninterruptibly(): ChannelFuture = null
  def cancel(): Boolean = true
  def getCause(): Throwable = null
  def getChannel(): Channel = null
  def isCancelled(): Boolean = false
  def isDone(): Boolean = true
  def isSuccess(): Boolean = true
  def removeListener(futureListener: ChannelFutureListener) {}
  def rethrowIfFailed(): ChannelFuture = null
  def setFailure(t: Throwable): Boolean = true
  def setProgress(a: Long, b: Long, c: Long): Boolean = true
  def setSuccess(): Boolean = true
  def sync(): ChannelFuture = null
  def syncUninterruptibly(): ChannelFuture = null

}