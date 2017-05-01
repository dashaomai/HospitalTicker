package messages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

/**
  * Created by Bob Jiang on 2017/4/30.
  */
final case class LoginResponse(data: List[String], hasError: Boolean, code: Int, msg: String)
final case class Login2Response(code: String, msg: String, userid: String, username: String)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val loginResponse = jsonFormat4(LoginResponse)
  implicit val login2Response = jsonFormat4(Login2Response)
}
