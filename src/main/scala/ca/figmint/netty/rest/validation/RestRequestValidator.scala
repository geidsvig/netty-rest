package ca.figmint.netty.rest.validation

import org.jboss.netty.handler.codec.http.HttpRequest

abstract class RestRequestValidator {

    /** 
     * I suggest to include SecurityHandler authorize in the validate implementation.
     */
    def validate(request: HttpRequest): Boolean
    
}