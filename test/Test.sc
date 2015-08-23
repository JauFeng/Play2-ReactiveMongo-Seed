import play.api.libs.json.{JsLookupResult, Json}

val jsObject = Json.obj("name" -> 12)

val jsResult: JsLookupResult = jsObject.\("name")

jsObject.value