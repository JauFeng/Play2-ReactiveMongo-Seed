package controllers

import javax.inject.Inject

import models.User

import play.api.Logger
import play.api.mvc._

import reactivemongo.bson._
import reactivemongo.api.collections.bson.BSONCollection

import reactivemongo.api.{FailoverStrategy, Cursor}

import reactivemongo.api.commands.{UpdateWriteResult, MultiBulkWriteResult, WriteResult}

import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents, MongoController}

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._

/**
 * Example using ReactiveMongo + BSONCollection
 *
 * @example
 * classic operations:
 * - find
 * - insert
 * - update
 * - remove
 * - save
 * - bulkInsert
 * commands operations:
 * - create
 * - rename
 * - drop
 *
 * @param reactiveMongoApi
 * @param executionContext
 *
 */
class ApplicationUsingBSON @Inject()(val reactiveMongoApi: ReactiveMongoApi)
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

  /**
   * BSONCollection.
   *
   * @return
   */
  def bsonCollection: BSONCollection = db.collection[BSONCollection]("user", failoverStrategy = failoverStrategy)

  def insert(name: String, age: Int) = Action.async { implicit request =>
    val document = BSONDocument(
      "name" -> BSONString(name),
      "age" -> BSONInteger(age)
    )

    val futureResult: Future[WriteResult] = bsonCollection.insert[BSONDocument](document)

    futureResult.map { writeResult =>
      Ok(s"Write result hasErrors: ${writeResult.hasErrors}; Effect rows: ${writeResult.n}")
    }
  }

  /**
   * Multiple insert.
   *
   * @todo
   * @return
   */
  // TODO
  def multiInsert = TODO

  //    Action.async { implicit request =>
  //    val futureBulkResult: Future[MultiBulkWriteResult] = bsonCollection.bulkInsert(ordered = false)(
  //      BSONDocument("name" -> "name1", "age" -> 20),
  //      BSONDocument("name" -> "name2", "age" -> 20),
  //      BSONDocument("name" -> "name3", "age" -> 20),
  //      BSONDocument("name" -> "name4", "age" -> 20)
  //    )
  //
  //    val users: List[User] = List(User("sean", 18))
  //
  //    users.map(implicitly[bsonCollection.Implic](_))
  //
  //    futureBulkResult.map { bulkResult =>
  //      Ok(s"bulk result: ${bulkResult.nModified}")
  //    }
  //    Future.successful(NotImplemented)
  //  }

  def update = Action.async { implicit request =>
    val selector = BSONDocument("name" -> "allen")
    val update = BSONDocument(
      // set field `age`
      "$set" -> BSONDocument("age" -> BSONInteger(99))
      // remove field `name`
      //      "$unset" -> BSONDocument("name" -> 1)
    )

    val futureResult: Future[UpdateWriteResult] =
      bsonCollection
        // `upsert`: update should concern all the documents that match selector
        // `multi`: automatically insert data if there is no existing document matching the update
        .update(selector, update, upsert = true, multi = true)

    futureResult.map { writeResult =>
      Ok(s" update result: ${writeResult.nModified}")
    }
  }

  def remove = Action.async { implicit request =>
    val query = BSONDocument("name" -> "allen")

    val futureResult: Future[WriteResult] =
      bsonCollection.remove(query, firstMatchOnly = true)

    futureResult.map { writeResult =>
      Ok(s"result hasErrors: ${writeResult.hasErrors}; effect rows: ${writeResult.n}")
    }
  }

  def findAndUpdate(name: String) = Action.async { implicit request =>
    val collection: BSONCollection = bsonCollection
    import collection.BatchCommands.FindAndModifyCommand.FindAndModifyResult
    implicit val reader: BSONDocumentReader[User] = Macros.reader[User]
    implicit val writer: BSONDocumentWriter[User] = Macros.writer[User]

    val selector = BSONDocument("name" -> name)
    val update = BSONDocument("$set" -> BSONDocument("age" -> 99))

    val futureResult: Future[FindAndModifyResult] =
      bsonCollection.findAndUpdate(selector, update,
        fetchNewObject = true,
        // insert a document one if no existing one is matching
        upsert = true)

    futureResult.map { findAndModifyResult =>
      val result = findAndModifyResult.result[User].getOrElse(None)
      Ok(s"Find result: $result")
    }
  }

  def findAndDelete(name: String) = Action.async { implicit request =>
    val collection: BSONCollection = bsonCollection
    import collection.BatchCommands.FindAndModifyCommand.FindAndModifyResult
    implicit val reader = Macros.reader[User]

    val selector = BSONDocument("name" -> name)

    val futureResult: Future[FindAndModifyResult] =
      bsonCollection.findAndRemove(selector)

    futureResult.map { findAndModifyResult =>
      val result = findAndModifyResult.result[User].getOrElse(None)
      Ok(s"Find result: $result")
    }
  }

  def list = Action.async { implicit request =>
    // Select all the documents
    val query: BSONDocument = BSONDocument()
    // Select only the fields `name` & `age`
    val filter: BSONDocument = BSONDocument("name" -> 1, "age" -> 1)

    val futureList: Future[List[BSONDocument]] =
      bsonCollection
        .find(query, filter)
        .cursor[BSONDocument]()
        .collect[List](upTo = Int.MaxValue, stopOnError = true)

    futureList map { users =>
      Ok(users map BSONDocument.pretty toString())
    }
  }

  def findById(id: String) = Action.async { implicit request =>
    val query = BSONDocument("_id" -> BSONObjectID(id = id))

    val future: Future[Option[BSONDocument]] = bsonCollection.find(query).one[BSONDocument]

    future.map { user =>
      Ok(user.map(BSONDocument pretty).getOrElse("None"))
    }
  }

  def findByName(name: String) = Action.async {
    // Select only the documents which fields `name` == `sean`
    val query: BSONDocument = BSONDocument("name" -> name)
    // Select only the fields `name` & `age`
    val filter: BSONDocument = BSONDocument("name" -> 1, "age" -> 1)

    val futureList: Future[List[BSONDocument]] = bsonCollection
      .find(query, filter)
      .cursor[BSONDocument]()
      .collect[List](upTo = Int.MaxValue, stopOnError = true)

    futureList map { users =>
      Ok(users map BSONDocument.pretty toString())
    }
  }

  def findByNameAndSort(name: String) = Action.async {
    // Select only the documents which fields `name` == `sean`
    val query: BSONDocument = BSONDocument("name" -> name)
    // Select only the fields `name` & `age`
    val filter: BSONDocument = BSONDocument("name" -> 1, "age" -> 1)
    // Sot by `age`: [1: asc | -1: desc]
    val sort = BSONDocument("age" -> 1)

    val futureList: Future[List[BSONDocument]] = bsonCollection
      .find(query, filter)
      .sort(sort)
      .cursor[BSONDocument]()
      .collect[List](upTo = Int.MaxValue, stopOnError = true)

    futureList map { users =>
      Ok(users map BSONDocument.pretty toString())
    }
  }

  def queryByCriteria(age: Int) = Action.async { implicit request =>
    val query = BSONDocument("age" -> BSONDocument("$gt" -> age))

    val futureList = bsonCollection
      .find(query)
      .cursor[BSONDocument]()
      .collect[List](upTo = Int.MaxValue, stopOnError = true)

    futureList.map { users =>
      Ok(users.map[String, List[String]](BSONDocument pretty).toString())
    }
  }

  def insertWithCaseClass(name: String, age: Int) = Action.async { implicit request =>
    val user = User(name, age)

    val futureResult: Future[WriteResult] = bsonCollection.insert[User](user)

    futureResult.map { writeResult =>
      Ok(s"Write result hasErrors: ${writeResult.hasErrors}; Effect rows: ${writeResult.n}")
    }
  }

  def listWithCaseClass = Action.async { implicit request =>
    val query = BSONDocument()
    val filter = BSONDocument("name" -> 1, "age" -> 1)

    val futureList: Future[List[User]] =
      bsonCollection
        .find(query, filter)
        .cursor[User]()
        .collect[List](upTo = Int.MaxValue, stopOnError = true)

    futureList.map { users =>
      Ok(users.toString())
    }
  }

  def findByNameWithCaseClass(name: String) = Action.async { implicit request =>
    val query = BSONDocument("name" -> name)
    val filter = BSONDocument("name" -> 1, "age" -> 1)

    val futureList = bsonCollection
      .find(query, filter)
      .cursor[User]()
      .collect[List](upTo = Int.MaxValue, stopOnError = true)

    futureList.map { users =>
      Ok(users.toString())
    }
  }

  /** **************************** Advanced Topics ******************************/

  /**
   * Macros for Case Class.
   */
  def usingMacrosForCaseClass() = {
    /** BSONHandler for both of Reader & Writer. */
    import reactivemongo.bson.BSONHandler
    implicit val personHandler: BSONHandler[BSONDocument, User] =
      Macros.handler[User]

    /** Each for reader & writer. */
    import reactivemongo.bson.{BSONDocumentReader, BSONDocumentWriter}
    implicit val reader: BSONDocumentReader[User] = Macros.reader[User]
    implicit val writer: BSONDocumentWriter[User] = Macros.writer[User]
  }

  /**
   * Read Preferences.
   *
   * @example
   * - `Primary`: read only from the primary. This is the default choice;
   * - `PrimaryPreferred`: read from the primary if it is available, or secondaries if it is not;
   * - `Secondary`: read only from any secondary;
   * - `SecondaryPreferred`: read from any secondary, or from the primary if they are not available;
   * - `Nearest`: read from the faster node (ie the node which replies faster than all others), regardless its status (primary or secondary.)
   */
  def usingReadPreference = {
    import reactivemongo.api.ReadPreference

    bsonCollection.find(BSONDocument())
      .cursor[BSONDocument](readPreference = ReadPreference.primary)
      .collect[List](upTo = Int.MaxValue, stopOnError = true)
  }


  /**
   * Stream with Play Iteratee.
   */
  def streamWithIteratee = Action.async {
    import play.api.libs.iteratee.Iteratee
    import play.api.libs.iteratee.Enumerator

    val query = BSONDocument()

    val enumerator: Enumerator[BSONDocument] =
      bsonCollection.find(query).cursor[BSONDocument]().enumerate(maxDocs = Int.MaxValue, stopOnError = true)

    val iteratee: Iteratee[BSONDocument, String] = Iteratee.fold[BSONDocument, String]("") { (s, doc) =>
      logger.info(s"found document: $doc")
      s + BSONDocument.pretty(doc)
    }

    val future: Future[String] = enumerator.|>>>(iteratee)

    future.map { s =>
      Ok(s)
    }
  }

  /**
   * Stream with Akka Stream.
   * @todo
   */
  // TODO
  def streamWithAkkaStream = TODO
}