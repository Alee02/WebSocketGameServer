package com.dp.service

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.{ActorMaterializer, FlowShape, OverflowStrategy}
import com.dp.actor._

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
