package controllers

import models.User

import javax.inject.Inject

import play.api.Logger
import play.api.libs.json._
import play.api.mvc.{Action, Controller}

import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.{ReactiveMongoComponents, MongoController, ReactiveMongoApi}

import reactivemongo.api.{FailoverStrategy, Cursor}
import reactivemongo.api.commands.WriteResult

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._

/**
 * Example using ReactiveMongo + Play JSON library.(using case classes that can be turned into JSON using Reads and Writes.)
 *
 * There are two approaches demonstrated in this controller:
 * - using JsObjects directly
 * - using case classes that can be turned into JSON using Reads and Writes.
 *
 * This controller uses case classes and their associated Reads/Writes
 * to read or write JSON structures.
 *
 * Instead of using the default Collection implementation (which interacts with
 * BSON structures + BSONReader/BSONWriter), we use a specialized
 * implementation that works with JsObject + Reads/Writes.
 *
 * Of course, you can still use the default Collection implementation
 * (BSONCollection.) See ReactiveMongo examples to learn how to use it.
 */
class ApplicationUsingCaseClass @Inject()(val reactiveMongoApi: ReactiveMongoApi)
                                         (implicit executionContext: ExecutionContext) extends Controller with MongoController with ReactiveMongoComponents {
  val logger = Logger(this.getClass)

  /**
   * Failover Strategy.
   */
  val failoverStrategy = FailoverStrategy(
    // the initial delay between the first failed attempt and the next one.
    initialDelay = 500 milliseconds,
    // number of retries to do before giving up.
    retries = 5,
    // a function that takes the current iteration and returns a factor to be applied to the initialDelay.
    delayFactor = n => 1
  )

  def collection: JSONCollection = db.collection[JSONCollection]("user", failoverStrategy)

  def create(name: String, age: Int) = Action.async { implicit request =>

    //    val user: User = User(name, age)

    val jsUser: JsObject = Json.obj("name" -> name, "age" -> age)


    jsUser.validate[User].map { user =>

      val futureResult: Future[WriteResult] = collection.insert[JsObject](jsUser)
      futureResult.map { writeResult =>
        Ok(s"Mongo LastError: ${writeResult.hasErrors}")
      }
    }.getOrElse(Future.successful(BadRequest("invalid json")))
  }

  def findByName(name: String) = Action.async { implicit request =>

    val cursor: Cursor[User] = collection.find[JsObject](Json.obj("name" -> name)).cursor[User]()

    val futureUserList: Future[List[User]] = cursor.collect[List](upTo = Int.MaxValue, stopOnError = true)

    futureUserList.map { users =>
      Ok(users.toString())
    }
  }

  def findAll = Action.async { implicit request =>

    val cursor: Cursor[User] = collection.find[JsObject](Json.obj()).cursor[User]()

    val futureList = cursor.collect[List]()

    futureList map { users =>

      Ok(users.toString())
    }

  }

}
