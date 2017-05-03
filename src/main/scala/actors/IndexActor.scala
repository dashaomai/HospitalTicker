package actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import messages.{Contact, JsonSupport}
import org.jsoup.Jsoup
import spray.json.JsonParser

import scala.collection.immutable
import scala.collection.mutable.ListBuffer

/**
  * Created by Bob Jiang on 2017/4/29.
  */
class IndexActor
(
  loginName: String ,
  loginPass: String,
  hospitalName: String,
  catalogName: String,
  departmentName: String,
  date: String,
  time: Int
) extends Actor with ActorLogging with JsonSupport {

  import akka.pattern.pipe
  import context.dispatcher

  final private implicit val materializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  private val http = Http(context.system)

  override def preStart(): Unit = {
    login(loginName, loginPass)
  }

  override def receive: Receive = {
    case _ =>
  }

  /***** 登陆处理（1/2） *****/
  private def login(loginName: String, loginPass: String): Unit = {
    log.info("开始第一次登陆")

    context.become(loginProcess)

    val params = Map[String, String](
      "mobileNo"  ->  loginName,
      "password"  ->  loginPass,
      "yzm"       ->  "",
      "isAjax"    ->  "true"
    )

    http.singleRequest(
      HttpRequest(
        HttpMethods.POST,
        "http://www.bjguahao.gov.cn/quicklogin.htm",
        entity = FormData(params).toEntity
      )
    ).pipeTo(self)
  }

  private def loginProcess: Receive = {
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
        log.debug("[login] Got response, body: {}", body.utf8String)

        val cookies = headers.collect {
          case c: `Set-Cookie` => HttpCookiePair(c.cookie.name, c.cookie.value)
        }

        val setCookies = immutable.Seq[HttpHeader](Cookie(cookies))

        val jsonAst = JsonParser(body.utf8String)
        val response = loginResponse.read(jsonAst)

        if (!response.hasError) {
          log.info("[login] 登陆成功：{}", response.msg)

          login2(setCookies)
        } else {
          log.error("[login] 登陆发生错误：{}", response)
        }
      }

    case resp @ HttpResponse(code, _, _, _) =>
      log.warning("[login] Request failed, response code:{}", code)
      resp.discardEntityBytes()

    case _ =>
  }

  /***** 登陆处理（2/2） *****/
  private def login2(cookies: immutable.Seq[HttpHeader]): Unit = {
    log.info("开始第二次登陆")

    context.become(login2Process(cookies))

    val params = Map[String, String](
      "isAjax"    ->  "true"
    )

    http.singleRequest(
      HttpRequest(
        HttpMethods.POST,
        "http://www.bjguahao.gov.cn/islogin.htm",
        cookies,
        FormData(params).toEntity
      )
    ).pipeTo(self)
  }

  private def login2Process(cookies: immutable.Seq[HttpHeader]): Receive = {
    case HttpResponse(StatusCodes.OK, _, entity, _) =>
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
        log.debug("[login2] Got response, body: {}", body.utf8String)

        val jsonAst = JsonParser(body.utf8String)
        val response = login2Response.read(jsonAst)

        if ("200".equals(response.code)) {
          log.info("[login2] 登陆全部成功，userid: {}, username: {}", response.userid, response.username)

          listName(cookies, response.userid, response.username)
        }
      }

    case resp @ HttpResponse(code, _, _, _) =>
      log.warning("[login2] Request failed, response code:{}", code)
      resp.discardEntityBytes()

    case _ =>
  }

  /***** 列出就诊者信息 *****/
  private def listName(cookies: immutable.Seq[HttpHeader], userId: String, username: String): Unit = {
    get(
      "/p/info.htm",
      cookies = cookies,
      receiver = listNameProcess(cookies, userId)
    )
  }

  private def listNameProcess(cookies: immutable.Seq[HttpHeader], userId: String): Receive = {
    case HttpResponse(StatusCodes.OK, _, entity, _) =>
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
        val htmlString = body.utf8String

//        val contacts = ListBuffer[ListBuffer[String]]()

//        val xml = XML.loadString(body.utf8String)
//        log.debug("{}", xml\\"div"\"@class=\"grzx_right_content2\"")
        /*val clean = new HtmlCleaner()
        val tagNode = clean.clean(htmlString)
        val rootNode = tagNode.findElementByAttValue("class", "grzx_right_content2", false, true)

        rootNode.traverse(new TagNodeVisitor {
          override def visit(tagNode: TagNode, htmlNode: HtmlNode): Boolean = {
            htmlNode match {
              case tgNode: TagNode =>
                tgNode.getName match {
                  case "tr" =>
                    contacts += ListBuffer[String]()

                  case "th" | "td" =>
                    contacts.last += tgNode.getText.toString
                }
            }

            true
          }
        })

        log.info("获取联系人列表：{}", contacts)*/
        val contacts = ListBuffer[Contact]()

        val jsoup = Jsoup.parse(htmlString)
        val element = jsoup.getElementsByClass("grzx_right_content2")
        val table = element.first().child(0)

        val lines = table.select("tr")

        for (i <- 1 until lines.size()) {
          val line = lines.get(i)
          val props = line.select("td")

          val name = props.get(0).text()
          val gender = props.get(1).text()
          val idType = props.get(2).text()
          val idNumber = props.get(3).text()
          val mobile = props.get(4).text()

          contacts += Contact(name, gender, idType, idNumber, mobile)
        }

        log.info("读取联系人信息列表：{}", contacts)

        listHospital(1, 1, cookies, userId)
      }

    case resp @ HttpResponse(code, _, _, _) =>
      log.warning("[httpReceive] Request failed, response code:{}", code)
      resp.discardEntityBytes()

    case _ =>
  }

  /***** 列出医院信息 *****/
  private def listHospital(currentPage: Int, totalPage: Int, cookies: immutable.Seq[HttpHeader], userId: String): Unit = {
    log.info("列出第 {}/{} 页的医院信息", currentPage, totalPage)

    get(
      s"/hp/$currentPage,0,0,0.htm",
      cookies = cookies,
      receiver = listHospitalProcess(currentPage, totalPage, cookies, userId)
    )
  }

  private def listHospitalProcess(currentPage: Int, totalPage: Int, cookies: immutable.Seq[HttpHeader], userId: String): Receive = {
    case HttpResponse(StatusCodes.OK, _, entity, _) =>
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
        val htmlString = body.utf8String

        val jsoup = Jsoup.parse(htmlString)
        val hospitalElements = jsoup.select("dd.yiyuan_co_dd")

        for (i <- 0 until hospitalElements.size()) {
          val element = hospitalElements.get(i)

          
        }

        if (0 <= htmlString.indexOf("<div id=\"yiyuan_list\">")) {


          listHospital(currentPage + 1, totalPage, cookies, userId)
        } else {
          log.error("解析医院页 {}/{} 返回值时未检测到医院列表结构")
        }

      }

    case resp @ HttpResponse(code, _, _, _) =>
      log.warning("[httpReceive] Request failed, response code:{}", code)
      resp.discardEntityBytes()

    case _ =>
  }

  private def get
  (
    path: String,
    params: Map[String, String] = Map.empty[String, String],
    cookies: immutable.Seq[HttpHeader],
    receiver: Receive
  ): Unit = {
    context.become(receiver)

    http.singleRequest(
      HttpRequest(
        HttpMethods.GET,
        s"http://www.bjguahao.gov.cn$path",
        cookies,
        FormData(params).toEntity
      )
    ).pipeTo(self)
  }

  /*private def httpReceive(cookies: immutable.Seq[HttpHeader], receiver: Receive): Receive = {
    case HttpResponse(StatusCodes.OK, _, entity, _) =>
      entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
        log.debug("[httpReceive] Got response, body: {}", body.utf8String)
      }

    case resp @ HttpResponse(code, _, _, _) =>
      log.warning("[httpReceive] Request failed, response code:{}", code)
      resp.discardEntityBytes()

    case _ =>
  }*/
}

object IndexActor {
  def props
  (
    loginName: String ,
    loginPass: String,
    hospitalName: String,
    catalogName: String,
    departmentName: String,
    date: String,
    time: Int
  ): Props = Props(
    classOf[IndexActor],
    loginName: String ,
    loginPass: String,
    hospitalName: String,
    catalogName: String,
    departmentName: String,
    date: String,
    time: Int
  )
}
