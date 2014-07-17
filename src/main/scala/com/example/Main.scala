package com.example

import akka.contrib.pattern.{ClusterSingletonProxy, ClusterSingletonManager}
import spray.routing.{Directives, Route, SimpleRoutingApp}
import spray.http._
import akka.actor.{PoisonPill, ActorRef, ActorSystem}
import spray.routing.authentication.{BasicAuth, UserPass}
import scala.concurrent.{ExecutionContextExecutor, Future}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.util.{Try, Failure}

trait RequestTimeout {
  implicit val timeout = Timeout(5 seconds)
  implicit def executionContext: ExecutionContextExecutor
}

trait UserAuthenticator extends RequestTimeout {
  def securityService: ActorRef

  def authenticator(userPass: Option[UserPass]):Future[Option[String]] =
    (securityService ? SecurityService.Authenticate(userPass)).mapTo[Option[String]]
}

trait SecurityRoute extends Directives with RequestTimeout {
  import SecurityService.JsonMarshaller._
  def securityService: ActorRef
  def securityServiceView: ActorRef

  def securityRoute: Route = {
    pathPrefix("users") {
      path(Segment) { name =>
        get {
          complete {
            (securityServiceView ? SecurityServiceView.GetUserByName(name)).mapTo[Option[SecurityServiceView.User]]
          }
        } ~
          delete {
            complete {
              securityService ! SecurityService.DeleteUserByName(name)
              StatusCodes.NoContent
            }
          }
      } ~
        get {
          complete {
            (securityServiceView ? SecurityServiceView.GetAllUsers).mapTo[List[SecurityServiceView.User]]
          }
        } ~
        put {
          entity(as[SecurityService.User]) { user =>
            complete {
              securityService ! SecurityService.AddUser(user)
              StatusCodes.NoContent
            }
          }
        }
    }
  }
}

object Main extends App with SimpleRoutingApp with UserAuthenticator with SecurityRoute {
  implicit val system = ActorSystem("ClusterSystem")
  implicit val executionContext = system.dispatcher

  // The ClusterSingletonManagers
  system.actorOf(ClusterSingletonManager.props(
    singletonProps = SecurityService.props,
    singletonName = "securityService",
    terminationMessage = PoisonPill,
    role = None),
    name = "singletonSecurityService")

  system.actorOf(ClusterSingletonManager.props(
    singletonProps = SecurityServiceView.props,
    singletonName = "securityServiceView",
    terminationMessage = PoisonPill,
    role = None),
    name = "singletonSecurityServiceView")

  // The ClusterSingletonProxies
  val securityService = system.actorOf(ClusterSingletonProxy.props("/user/singletonSecurityService/securityService", None), "securityServiceProxy")
  val securityServiceView = system.actorOf(ClusterSingletonProxy.props("/user/singletonSecurityServiceView/securityServiceView", None), "securityServiceViewProxy")

  val config = Config(system)

  startServer(interface = config.bindAddress, port = config.bindPort) {
    pathPrefix("api") {
      securityRoute
    } ~
    pathPrefix("web") {
      getFromResourceDirectory("web")
    } ~
    authenticate(BasicAuth(authenticator _, realm = "secure site")) { username =>
      path("secure") {
        complete("Welcome!")
      }
    }
  }

  // try to open the app in the default browser
  Try {
    println("Please wait, the application is launching...")
    // sleeping for my old Macbook@2009, it needs some time to get things up and running...
    Thread.sleep((5 seconds).toMillis)
    val url = "http://localhost:8080/web/index.html"
    java.awt.Desktop.getDesktop.browse(java.net.URI.create(url))
  } match {
    case Failure(cause) => println("Could not open browser: " + cause.getMessage)
    case _ =>
  }
}
