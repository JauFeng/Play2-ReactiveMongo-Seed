package models

import play.api.Logger
import play.api.libs.json.{Format, Json}
import reactivemongo.bson._


case class User(name: String, age: Int)

object User {
  val logger = Logger(this.getClass)

  implicit val userFormat: Format[User] = Json.format[User]

  implicit object UserReader extends BSONDocumentReader[User] {
    override def read(bson: BSONDocument): User = {

      logger.info(bson.toString())

      val id = bson.getAs[BSONObjectID]("_id").get
      val name: String = bson.getAs[String]("name").getOrElse("")
      val age: Double = bson.getAs[BSONValue]("age") match {
        case Some(BSONInteger(i)) => i
        case Some(BSONLong(l)) => l
        case Some(BSONDouble(d)) => d
        case _ => -1
      }

      User(name, age.toInt)
    }
  }

  implicit object UserWriter extends BSONDocumentWriter[User] {
    override def write(t: User): BSONDocument = {
      BSONDocument(
        "name" -> BSONString(t.name),
        "age" -> BSONInteger(t.age)
      )
    }
  }

}

