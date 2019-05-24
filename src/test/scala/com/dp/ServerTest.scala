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

    WS("/greeter", wsClient.flow) ~> gameService.websocketRoute ~> check {
      isWebSocketUpgrade shouldEqual true
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
    Flow[Message].mapConcat {
      case tm: TextMessage  =>
        TextMessage(Source.single("Hello ") ++
          tm.textStream ++
          Source.single("!")) :: Nil
      case bm: BinaryMessage =>
        bm.dataStream.runWith(Sink.ignore)
        Nil
    }
}
