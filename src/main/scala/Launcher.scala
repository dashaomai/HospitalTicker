import actors.IndexActor
import akka.actor.ActorSystem
import messages.BookDate

/**
  * Created by Bob Jiang on 2017/4/29.
  */
object Launcher extends App {
  val system = ActorSystem("HospitalTicker")

  system.actorOf(IndexActor.props(
    "13717659906", "7758gocome",
    "北京大学第三医院", "眼科", "眼科门诊（特需）",
    "2017-05-06", BookDate.AFTERNOON
  ), "index")

  Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
    override def run(): Unit = system.terminate()
  }))
}
