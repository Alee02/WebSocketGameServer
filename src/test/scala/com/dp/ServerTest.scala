package com.dp

import akka.actor.{Actor, ActorRef, ActorSystem}
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

  test("should register multiple players") {
    val gameService = new GameService()
    val johnClient = WSProbe()
    val andrewClient = WSProbe()

    WS(s"/?playerName=john", johnClient.flow) ~> gameService.websocketRoute ~> check {
      johnClient.expectMessage("[{\"name\":\"john\"}")
    }

    WS(s"/?playerName=andrew", andrewClient.flow) ~> gameService.websocketRoute ~> check {
      andrewClient.expectMessage("[{\"name\":\"john\"},{\"name\":\"andrew\"}]")
    }
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

class GameAreaActor extends Actor {

  val players = collection.mutable.LinkedHashMap[String, PlayerWithActor]()

  override def receive: Receive = {
    case PlayerJoined(player, actor) => players += (player.name -> PlayerWithActor(player, actor))
    case PlayerLeft(playerName) => players -= playerName
    case PlayerMoveRequest(playerName, direction) =>
  }

  def notifyPlayersChanged: Unit = {
    players.values.foreach(_.actor ! players.values.map(_.player))
  }

}

trait GameEvent
case class PlayerJoined(player: Player, actorRef: ActorRef) extends GameEvent
case class PlayerLeft(player: String) extends GameEvent
case class PlayerMoveRequest(player: String, direction: String) extends GameEvent
case class Player(name: String)

case class PlayerWithActor(player: Player, actor: ActorRef)