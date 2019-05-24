package com.dp

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.stream.ActorMaterializer
import org.scalatest.{FunSuite, Matchers}

class ServerTest extends FunSuite with Matchers with ScalatestRouteTest {
  test("should create empty GameService") {
    new GameService()
  }

  test("should be able to connect to the GameService websocket") {
    val gameService = new GameService()
    val wsClient = WSProbe()

    WS("/", wsClient.flow) ~> gameService.websocketRoute ~> check {
      isWebSocketUpgrade shouldEqual true
    }
  }

  test("should respond with correct message") {
    val gameService = new GameService
    val wsClient = WSProbe()

    WS("/", wsClient.flow) ~> gameService.websocketRoute ~> check {
      wsClient.sendMessage(TextMessage("hello"))
      wsClient.expectMessage("hello")
    }
  }
}

class GameService() extends Directives {

  implicit val actorSystem = ActorSystem()

  implicit val actorMaterializer = ActorMaterializer()

  val websocketRoute = get {
    handleWebSocketMessages(greeter)
  }

  def greeter: Flow[Message, Message, Any] =
    Flow[Message].collect {
      case TextMessage.Strict(text) => TextMessage(text)
    }
}
