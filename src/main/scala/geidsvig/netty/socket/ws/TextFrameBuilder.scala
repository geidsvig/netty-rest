package geidsvig.netty.socket.ws

trait TextFrameBuilder {
  
  /**
   * Intended as the only outbound websocket response frame type.
   * When a request comes through the websocket, or on a handshake, if any responses need to be sent back with status codes, this is the text frame type to use.
   * 
   * @param statusCode an Http status code value
   * @param message a simple text response, similar to an Http status code message
   */
  case class ResponseTextFrame(statusCode: Int, message: String) {
    def toJson() = s"""{"response":{"status":${statusCode},"message":${message}}}"""
  }
  
  /**
   * Intended as the root string based text frame for outbound websocket events.
   * These frames have an eventType indicating their purpose, and an inner eventJson that the client should know how to handle.
   * 
   * <p>format:
   * {"event":{"the-eventType":the-eventJson}}
   * </p>
   * <p>example:
   * eventType = "chat-message"
   * eventJson = {"name":"Garrett Eidsvig","room":"room123","text":"Hello world!"}
   * {"event":{"chat-message":{"name":"Garrett Eidsvig","room":"room123","text":"Hello world!"}}
   * </p>
   * 
   * @param eventType
   * @param eventJson
   */
  case class EventTextFrame(eventType: String, eventJson: String) {
    def toJson() = s"""{"event":{"${eventType}":${eventJson}}}"""
  }

}