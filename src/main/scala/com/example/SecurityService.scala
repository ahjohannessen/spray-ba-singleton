package com.example

import java.util.Date
import spray.routing.authentication.UserPass
import akka.actor._
import org.apache.shiro.crypto.SecureRandomNumberGenerator
import org.apache.shiro.util.ByteSource
import org.apache.shiro.crypto.hash.Sha512Hash
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import akka.persistence.{PersistentActor, PersistentView}
import scala.util.Failure
import scala.concurrent.duration._

object SecurityService {
  // business types
  case class User(username: String, password: String)

  object JsonMarshaller extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val secServiceUserFormat = jsonFormat2(SecurityService.User)
    implicit val secServiceViewUserFormat = jsonFormat1(SecurityServiceView.User)
  }

  // commands
  case class DeleteUserByName(username: String)
  case class AddUser(user: SecurityService.User)
  case class Authenticate(userPass: Option[UserPass])

  // events
  case class UserCreated(timestamp: Long, user: SecurityService.User)

  def props = Props(new SecurityService)
}

class SecurityService extends PersistentActor with ActorLogging {
  import SecurityService._

  override def persistenceId: String =  "SecurityService"

  def handleCreateUser(event: UserCreated): ActorRef = {
    val UserCreated(_, User(username, _)) = event
    context.actorOf(AppUser.props(username), username)
  }

  def handleCreateUserAndSendCommand(event: UserCreated) {
    val UserCreated(_, user) = event
    handleCreateUser(event) ! AppUser.CreateUser(user)
  }

  override def receiveRecover: Receive = {
    case evt: UserCreated => handleCreateUser(evt)
  }

  override def receiveCommand: Receive = {
    case msg @ Authenticate(userPass) => userPass match {
      case Some(up) => context.child(up.user) match {
        case Some(user) => user forward msg
        case None => sender ! None
      }
      case None => sender ! None
    }

    case DeleteUserByName(username) =>
      context.child(username) match {
        case Some(user) => user ! AppUser.Delete
        case None => sender ! Failure
      }

    case AddUser(user) => context.child(user.username) match {
      case Some(userActor) => userActor ! AppUser.UpdateUser(user)
      case None => persist(SecurityService.UserCreated(new Date().getTime, user))(handleCreateUserAndSendCommand)
    }
  }
}

object AppUser {
  // commands
  case object Delete
  case class UpdateUser(user: SecurityService.User)
  case class CreateUser(user: SecurityService.User)

  // events
  case class Deleted(timestamp: Long, username: String)
  case class UserCreated(timestamp: Long, user: SecurityService.User)
  case class UserUpdated(timestamp: Long, user: SecurityService.User)

  def props(username: String) = Props(new AppUser(username))

  def generateSalt: String = {
    val rng = new SecureRandomNumberGenerator
    val byteSourceSalt: ByteSource = rng.nextBytes
    byteSourceSalt.toHex
  }

  def generateHashedPassword(passwordText: String, passwordSalt: String, iterations: Int = 512) =  new Sha512Hash(passwordText, passwordSalt, iterations).toHex

  def generatePassword(username: String, password: String): (String, String) = {
    val passwordSalt = generateSalt
    val hashedPassword = generateHashedPassword(password, passwordSalt)
    (hashedPassword, passwordSalt)
  }
}

class AppUser(username: String) extends PersistentActor with ActorLogging {
  import AppUser._
  log.info("Creating AppUser: {}", username)
  override def persistenceId: String = username

  var updated: Long = _
  var deleted = false
  var passwordHash: String = _
  var passwordSalt: String = _

  def updateUser(timestamp: Long, password: String) {
    log.info("Updating user")
    val generatedPassword = generatePassword(username, password)
    updated = timestamp
    passwordHash = generatedPassword._1
    passwordSalt = generatedPassword._2
  }

  def handleUserCreated(evt: AppUser.UserCreated) {
    val AppUser.UserCreated(date, SecurityService.User(_, password)) = evt
    updateUser(date, password)
  }
  
  def handleUserUpdated(evt: AppUser.UserUpdated) {
    val AppUser.UserUpdated(date, SecurityService.User(_, password)) = evt
    updateUser(date, password)
  }
  
  def handleDeleted(evt: AppUser.Deleted) {
    val AppUser.Deleted(date, _) = evt
    updated = date
    deleted = true
  }
  
  override def receiveRecover: Receive = {
    case evt: AppUser.Deleted => handleDeleted(evt)
    case evt: AppUser.UserCreated => handleUserCreated(evt)
    case evt: AppUser.UserUpdated => handleUserUpdated(evt)
  }

  override def receiveCommand: Receive = {
    case AppUser.Delete => persist(AppUser.Deleted(new Date().getTime, username))(handleDeleted)
    case AppUser.CreateUser(user) => persist(AppUser.UserCreated(new Date().getTime, user))(handleUserCreated)
    case AppUser.UpdateUser(user) => persist(AppUser.UserUpdated(new Date().getTime, user))(handleUserUpdated)
    case SecurityService.Authenticate(userPass) =>
      sender ! userPass.flatMap { up =>
        log.info("Authenticating: {}", up.user)
        val hashedPwd = generateHashedPassword(up.pass, passwordSalt)
        if(hashedPwd == passwordHash) Some(up.user) else None
      }
  }
}

object SecurityServiceView {
  // business types
  case class User(username: String)

  // commands
  case object GetAllUsers
  case object DumpUsers
  case class GetUserByName(username: String)

  def props = Props(new SecurityServiceView)
}

class SecurityServiceView extends PersistentView with ActorLogging {
  import SecurityServiceView._
  log.info("Creating SecurityServiceView")
  override def persistenceId: String = "SecurityService"
  override def viewId: String = "SecurityServiceView"

  var users = List.empty[SecurityServiceView.User]

  override def receive: Actor.Receive = {
    case SecurityService.UserCreated(date, user) if isPersistent =>
      log.info("Creating UserView: {}", user.username)
      context.actorOf(Props(new UserView(user.username)), user.username)
      users = SecurityServiceView.User(user.username) :: users
    case AppUser.UserCreated(_, _) if isPersistent =>
    case AppUser.UserUpdated(_, _) if isPersistent =>
    case AppUser.Deleted(_, username) if isPersistent =>
      log.info("Deleting user: {}", username)
      users = users.filterNot { user => user.username.equals(username) }

    case GetAllUsers => sender ! users
    case GetUserByName(username) => sender ! users.find { user => user.username.equals(username) }
    case DumpUsers => log.info("Dump: {}", users.mkString(","))
    case msg @ _ => log.warning("Could not handle message: {}", msg)
  }
}

class UserView(username: String) extends PersistentView with ActorLogging {
  log.info("Creating User PersistentView: {}", username)
  override def persistenceId: String = username
  override def viewId: String = s"$username-view"

  override def receive: Actor.Receive = {
    case msg @ _ if isPersistent =>
      log.info("Forwarding message: {}", msg)
      context.parent forward msg
  }
}