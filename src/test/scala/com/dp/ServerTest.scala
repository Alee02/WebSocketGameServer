package com.dp

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import com.dp.service.GameService
import org.scalatest.{FunSuite, Matchers}

class ServerTest extends FunSuite with Matchers with ScalatestRouteTest {

  def assertWebsocket(playerName: String)(assertion: WSProbe => Unit) : Unit = {
    val gameService = new GameService
    val wsClient = WSProbe()

    WS(s"/?playerName=$playerName", wsClient.flow) ~> gameService.websocketRoute ~> check {
      assertion(wsClient)
    }
  }
  test("should create empty GameService") {
    new GameService()
  }

  test("should be able to connect to the GameService websocket") {
    assertWebsocket("John")(wsClient => {
      wsClient.expectMessage("""[{"name":"John","position":{"x":0,"y":0}}]""".stripMargin)
//      wsClient.sendMessage(TextMessage("hello"))
//      wsClient.expectMessage("hello")
    })
  }

  test("should respond with correct message") {
    assertWebsocket("John")(wsClient => {
      wsClient.expectMessage("""[{"name":"John","position":{"x":0,"y":0}}]""".stripMargin)
//      wsClient.sendMessage(TextMessage("hello"))
//      wsClient.expectMessage("hello")
    })
  }

  test("should register player") {
    assertWebsocket("John")(wsClient => {
      wsClient.expectMessage("""[{"name":"John","position":{"x":0,"y":0}}]""".stripMargin)
    })
  }

  test("should register player and move it up") {
    assertWebsocket("John") { wsClient => {
      wsClient.expectMessage("""[{"name":"John","position":{"x":0,"y":0}}]""".stripMargin)
      wsClient.sendMessage("up")
      wsClient.expectMessage("""[{"name":"John","position":{"x":0,"y":1}}]""".stripMargin)
    }}
  }

  test("should register player and move around") {
    assertWebsocket("John") { wsClient => {
      wsClient.expectMessage("""[{"name":"John","position":{"x":0,"y":0}}]""".stripMargin)
      wsClient.sendMessage("up")
      wsClient.expectMessage("""[{"name":"John","position":{"x":0,"y":1}}]""".stripMargin)
      wsClient.sendMessage("left")
      wsClient.expectMessage("""[{"name":"John","position":{"x":-1,"y":1}}]""".stripMargin)
      wsClient.sendMessage("down")
      wsClient.expectMessage("""[{"name":"John","position":{"x":-1,"y":0}}]""".stripMargin)
      wsClient.sendMessage("right")
      wsClient.expectMessage("""[{"name":"John","position":{"x":0,"y":0}}]""".stripMargin)
    }}
  }

  test("should register multiple players") {
    val gameService = new GameService()
    val johnClient = WSProbe()
    val andrewClient = WSProbe()

    WS(s"/?playerName=John", johnClient.flow) ~> gameService.websocketRoute ~> check {
      johnClient.expectMessage("""[{"name":"John","position":{"x":0,"y":0}}]""".stripMargin)
    }

    WS(s"/?playerName=Andrew", andrewClient.flow) ~> gameService.websocketRoute ~> check {
      andrewClient.expectMessage("""[{"name":"John","position":{"x":0,"y":0}},{"name":"Andrew","position":{"x":0,"y":0}}]""".stripMargin)
    }


  }
}