package ca.figmint.netty.rest.security

import org.jboss.netty.handler.codec.http.HttpRequest

trait SecurityHandler {

    def authorize(request: HttpRequest): Boolean
    
}