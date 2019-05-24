package com.dp

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.stream.{ActorMaterializer, FlowShape}
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
      wsClient.expectMessage("welcome John")
      wsClient.sendMessage(TextMessage("hello"))
      wsClient.expectMessage("hello")
    })
  }

  test("should respond with correct message") {
    assertWebsocket("John")(wsClient => {
      wsClient.expectMessage("welcome John")
      wsClient.sendMessage(TextMessage("hello"))
      wsClient.expectMessage("hello")
    })
  }

  test("should register player") {
    assertWebsocket("John")(wsClient => {
      wsClient.expectMessage("welcome John")
    })
  }

}

class GameService() extends Directives {

  implicit val actorSystem = ActorSystem()

  implicit val actorMaterializer = ActorMaterializer()

  val websocketRoute = (get & parameter("playerName")){ playerName =>
    handleWebSocketMessages(flow(playerName))
  }

  def flow(playerName: String): Flow[Message, Message, Any] = {
    import GraphDSL.Implicits._
    Flow.fromGraph(GraphDSL.create(){ implicit builder => {
      val materialization = builder.materializedValue.map(_ => TextMessage(s"welcome $playerName"))
      val messagePassingFlow = builder.add(Flow[Message].map(identity))
      val merge = builder.add(Merge[Message](2))

      materialization ~> merge.in(0)
      merge ~> messagePassingFlow

      FlowShape(merge.in(1), messagePassingFlow.out)
    }
    })
  }

}
