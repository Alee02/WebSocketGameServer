package com.dp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import com.dp.service.GameService

import scala.io.StdIn

object Firestarter extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()

  implicit val executionContext = system.dispatcher

  val gameService: GameService = new GameService()

  val bindingFuture = Http().bindAndHandle(gameService.websocketRoute, "localhost", 8080)
  println(s"Serve onlin at http://localhost:8080/\nPress RETURN to stop...")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())

}
