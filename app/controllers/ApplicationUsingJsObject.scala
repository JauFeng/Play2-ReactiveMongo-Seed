package controllers

import javax.inject.Inject

import play.api._
import play.api.mvc._
import play.api.libs.json._

import play.modules.reactivemongo.json._
import play.modules.reactivemongo.json.collection.JSONCollection
import play.modules.reactivemongo.{ReactiveMongoComponents, MongoController, ReactiveMongoApi}

import reactivemongo.api.{FailoverStrategy, Cursor}

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._

/**
 * Example using ReactiveMongo + Play JSON library.(using JsObjects directly)
 *
 * There are two approaches demonstrated in this controller:
 * - using JsObjects directly.
 * - using case classes that can be turned into JSON using Reads and Writes.
 *
 * This controller uses JsObjects directly.
 *
 * Instead of using the default Collection implementation (which interacts with
 * BSON structures + BSONReader/BSONWriter), we use a specialized
 * implementation that works with JsObject + Reads/Writes.
 *
 * Of course, you can still use the default Collection implementation
 * (BSONCollection.) See ReactiveMongo examples to learn how to use it.
 *
 * @param reactiveMongoApi
 * @param executionContext
 */
class ApplicationUsingJsObject @Inject()(val reactiveMongoApi: ReactiveMongoApi)
                           (implicit executionContext: ExecutionContext) extends Controller with MongoController with ReactiveMongoComponents {
  val logger = Logger(this.getClass)

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }


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

  /**
   * Get a JSONCollection (a Collection implementation that is designed to work
   * with JsObject, Reads and Writes.)
   *
   * @note
   * the `collection` is not a `val`, but a `def`. We do _not_ store the collection reference to avoid potential problems in development with Play hot-reloading.
   */
  def collection: JSONCollection = db.collection[JSONCollection]("user", failoverStrategy)

  /**
   * Create user method.
   *
   * @param name name of User
   * @param age age of User
   * @return
   */
  def create(name: String, age: Int) = Action.async { implicit request =>

    val userJson: JsObject = Json.obj(
      "name" -> name,
      "age" -> age
    )
    collection.insert[JsObject](userJson).map { writeResult =>
      Ok(s"Mongo LastError: ${writeResult.hasErrors}")
    }
  }

  /**
   * Find user by name.
   *
   * @param name name of User
   * @return
   */
  def findByName(name: String) = Action.async { implicit request =>
    val cursor: Cursor[JsObject] =
      collection
        //  find all user with name which is `name`
        .find(Json.obj("name" -> name))
        //  sort user by age
        .sort(Json.obj("age" -> -1))
        //  perform the query and get a cursor of JsObject
        .cursor[JsObject]()

    //  gather all the JsObjects in a list
    val futureUserList: Future[List[JsObject]] = cursor.collect[List]()

    //  transform the list into a JsArray
    val futureUserJsonArray: Future[JsArray] = futureUserList.map { users =>
      Json.arr(users)
    }

    //  everything is Ok! Let's reply with the array
    futureUserJsonArray.map {
      users =>
        Ok(users)
    }
  }
}