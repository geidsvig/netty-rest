package ca.figmint.netty.socket.validation

import org.jboss.netty.handler.codec.http.HttpRequest

abstract class WebSocketRequestValidator {

    /** 
     * I suggest to include SecurityHandler.authorize in the validate implementation.
     */
    def validate(request: HttpRequest): Boolean
    
}
