package messages

/**
  * Created by Bob Jiang on 2017/4/30.
  */
class Hospital {

}

final case class Contact
(
  name: String,
  gender: String,
  idType: String,
  idNumber: String,
  mobile: String
)

final object BookDate {
  val MORNING: Int = 0
  val AFTERNOON: Int = 1
  val EVENING: Int = 2
}
