package com.dp

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
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

class GameService() extends Directives {

  implicit val actorSystem = ActorSystem()

  implicit val actorMaterializer = ActorMaterializer()

  val gameAreaActor = actorSystem.actorOf(Props(new GameAreaActor()))

  val websocketRoute = (get & parameter("playerName")){ playerName =>
    handleWebSocketMessages(flow(playerName))
  }

  val playerActor = Source.actorRef[GameEvent](5, OverflowStrategy.fail)

  def flow(playerName: String): Flow[Message, Message, Any] = {
    Flow.fromGraph(GraphDSL.create(playerActor){ implicit builder => playerActor => {
      import GraphDSL.Implicits._

      val materialization = builder.materializedValue.map(playerActor =>  PlayerJoined(Player(playerName, Position(0,0)), playerActor))
      val merge = builder.add(Merge[GameEvent](2))

      val messagesToGameEventFlow = builder.add(Flow[Message].collect{
        case TextMessage.Strict(txt) => PlayerMoveRequest(playerName, txt)
      })

      val gameEventsToMessageFlow = builder.add(Flow[GameEvent].map{
        case PlayersChanged(players) =>
          import spray.json._
          import DefaultJsonProtocol._
          import DefaultJsonProtocol._
          implicit val positionFormat: RootJsonFormat[Position] = jsonFormat2(Position)
          implicit val playerFormat: RootJsonFormat[Player] = jsonFormat2(Player)
          TextMessage(players.toJson.toString)
      })
      // gameAreaActor will be a sink because once the flow has been completed, the PlayerLeft message will be emitted
      val gameAreaActorSink = Sink.actorRef[GameEvent](gameAreaActor, PlayerLeft(playerName))

      materialization ~> merge ~> gameAreaActorSink
      messagesToGameEventFlow ~> merge

      playerActor ~> gameEventsToMessageFlow

      FlowShape(messagesToGameEventFlow.in, gameEventsToMessageFlow.out)
    }
    })
  }

}

class GameAreaActor extends Actor {

  val players = collection.mutable.LinkedHashMap[String, PlayerWithActor]()

  override def receive: Receive = {
    case PlayerJoined(player, actor) => {
      players += (player.name -> PlayerWithActor(player, actor))
      notifyPlayersChanged()
    }
    case PlayerLeft(playerName) => {
      players -= playerName
      notifyPlayersChanged()
    }
    case pmr@PlayerMoveRequest(playerName, direction) => {
      val offset = direction match {
        case "up" => Position(0,1)
        case "down" => Position(0,-1)
        case "right" => Position(1,0)
        case "left" => Position(-1,0)
      }
      val oldPlayerWithActor = players(playerName)
      val oldPlayer = oldPlayerWithActor.player
      val actor = oldPlayerWithActor.actor
      players(playerName) = PlayerWithActor(Player(playerName, oldPlayer.position + offset), actor)
      notifyPlayersChanged()
    }
  }
  def notifyPlayersChanged(): Unit = {
    players.values.foreach(_.actor ! PlayersChanged(players.values.map(_.player)))
  }
}

trait GameEvent
case class PlayerJoined(player: Player, actorRef: ActorRef) extends GameEvent
case class PlayerLeft(player: String) extends GameEvent
case class PlayerMoveRequest(player: String, direction: String) extends GameEvent
case class PlayersChanged(players: Iterable[Player]) extends GameEvent

case class Player(name: String, position: Position)
case class PlayerWithActor(player: Player, actor: ActorRef)

case class Position(x: Int, y: Int) {
  def + (other: Position) = {
    Position(x+other.x, y+other.y)
  }
}