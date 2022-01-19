import sbt._
import play.sbt.PlayRunHook
import scala.sys.process.Process

object Webpack {
  def apply(base: File): PlayRunHook = {

    object WebpackProcess extends PlayRunHook {
      var process: Option[Process] = None

      override def beforeStarted(): Unit = {
        process = Some(Process("npm run start", base).run)
      }

      override def afterStopped(): Unit = {
        process.map(p => p.destroy())
        process = None
      }
    }
    WebpackProcess
  }
}
