/**
  * Created by Bob Jiang on 2017/4/30.
  */
package messages

import scala.collection.mutable

/**
  * 医院信息
  * @param name
  * @param url
  * @param level
  * @param refresh
  * @param contact
  * @param address
  * @param catalogsMap
  */
final case class Hospital
(
  // 名称
  name: String,
  url: String,
  level: String,

  refresh: Time,
  contact: String,
  address: String,

  catalogsMap: mutable.HashMap[String, Catalog]
)

/**
  * 时间
  * @param hour
  * @param minute
  */
final case class Time
(
  hour: Int,
  minute: Int

)

object Time {
  def apply(input: String): Time = {
    val values = input.split(":")
    if (values.length > 1) {
      new Time(values(0).toInt, values(1).toInt)
    } else {
      throw new IllegalArgumentException("输入的时间文本格式不正确：" + input)
    }
  }
}

/**
  * 科室分类
  * @param name
  * @param departmentsMap
  */
final case class Catalog
(
  name: String,
  departmentsMap: mutable.HashMap[String, Department]
)

/**
  * 科室
  * @param hospitalId
  * @param departmentId
  * @param name
  * @param url
  * @param booksMap
  */
final case class Department
(
  hospitalId: Int,
  departmentId: Int,

  name: String,
  url: String,

  booksMap: mutable.HashMap[String, BookDate]
)

/**
  * 预约日期
  * @param week
  * @param date
  * @param informations
  */
final case class BookDate
(
  week: String,
  date: String,

  informations: List[BookInformation]
)

/**
  * 预约信息
  * @param remain
  * @param code
  */
final case class BookInformation
(
  remain: Int,
  code: Int
)

/**
  * 联系人
  * @param name
  * @param gender
  * @param idType
  * @param idNumber
  * @param mobile
  */
final case class Contact
(
  name: String,
  gender: String,
  idType: String,
  idNumber: String,
  mobile: String
)

object BookDate {
  val MORNING: Int = 0
  val AFTERNOON: Int = 1
  val EVENING: Int = 2
}
