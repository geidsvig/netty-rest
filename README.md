Netty-Rest
==========

This library is intended to reduce all of the boiler-plate code that goes along with creating actor based Comet, WebSocket, and HTTP handlers.
The library uses Scala, Akka, and Netty to handle the low level connection management, and leaves the business side of things to you.


Usage
-----

1) WebSockets - Extend the WebSocketHandler and implement the abstract methods to handle inbound payloads.
2) Comet - Extend the CometHandler and implement the abstract methods to handle requests.
3) HTTP - Create an Akka actor and handle a ChannelWithRequest case class in the receive block.
4) Configure your routes with your own implementation of the RestRoutehandler class.


Examples
--------

Check out the Netty-Rest-Server project. It uses an SBT dist setup and shows a very simple configuration of comet, websocket, and http handlers in the ApplicationContext file.


Left For You
------------

- security
- persistence
- and your business logic
