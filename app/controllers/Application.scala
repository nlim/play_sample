package controllers

import play.api.mvc._
import util.Random
import concurrent.{Await, Future, ExecutionContext}

import play.libs.Akka
import play.api.libs.ws.WS
import java.util.concurrent.TimeUnit

object Application extends Controller {

  //val url = "http://localhost:9000/"
  val url = "http://www.google.com/"

  def index = Action(Ok("Yo"))

  def bad = Action {
    // Using the default thread pool to run the intensive computation ties up
    // threads for handling the incoming requests and returning the results
    implicit val context = play.api.libs.concurrent.Execution.defaultContext
    val futureInt = scala.concurrent.Future { intensiveComputation() }
    Async {
      futureInt.map(i => Ok("Got result: " + i))
    }
  }

  def good = Action {
    //Using another thread pool to run the intensive computation, which does not
    //tie up threads in the default thread pool, So these threads can sleep whilst
    //the Result handling threads can return when any Future has been filled
    implicit val context = Contexts.myContext
    val futureInt: Future[Int] = scala.concurrent.Future { intensiveComputation() }
    Async {
      futureInt.map(i => Ok("Got result: " + i))
    }
  }

  def add = Action {
    implicit val context = Contexts.myContext
    // Passes off two intensive computations the my-context thread pool in parallel
    val futureInt:  Future[Int] = scala.concurrent.Future(intensiveComputation())
    val futureInt2: Future[Int] = scala.concurrent.Future(intensiveComputation())
    // Compose the results of the two futures
    Async {
      for {
        int1 <- futureInt
        int2 <- futureInt2
      } yield Ok("Got added result: " + (int1 + int2))
    }
  }

  def ws = Action {
    implicit val context = Contexts.myContext
    val responseFuture = WS.url(url).get()
    Async {
      responseFuture.map(response => Ok("Response from: " + url + ": " + response.status))
    }
  }


  private def intensiveComputation() = {
    Thread.sleep(30);
    Random.nextInt(10)
  }
}

object Contexts {
  val myContext: ExecutionContext = Akka.system.dispatchers.lookup("play.akka.actor.my-context")
}
