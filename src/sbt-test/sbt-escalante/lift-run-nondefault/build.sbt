import sbt._
import sbt.Keys._
import io.escalante.sbt.EscalantePlugin._
import io.escalante.sbt.EscalantePlugin.EscalanteKeys._
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.spec.WebArchive
import java.net.URL
import java.io.{BufferedInputStream, StringWriter}
import scala.collection.JavaConversions._
escalanteSettings

version := "0.1"

scalaVersion := "2.10.1"

escalanteVersion := "0.3.0-SNAPSHOT"

liftWarName in liftWar := "helloworld-nondefault-lift.war"

liftVersion in liftWar := Some("2.5-RC4")

libraryDependencies <++= (liftVersion in liftWar) { lv: Option[String] => Seq(
    "net.liftweb" %% "lift-webkit" % lv.get % "provided"
  )
}

TaskKey[Unit]("check") <<= (target) map { (target) =>
  // 1. Open war file and print contents
  val war = ShrinkWrap.createFromZipFile(
      classOf[WebArchive], target / "helloworld-nondefault-lift.war")
  val separator = System.getProperty("line.separator")
  println("War contents: %s%s".format(separator,
      war.getContent.values().iterator().map(_.toString).toSeq.sorted.mkString(separator)))
  // 2. Check classes and descriptor present
  assert(war.contains("WEB-INF/classes/bootstrap/liftweb/Boot.class"),
      "Boot class not present" + war.getContent())
  assert(war.contains("WEB-INF/classes/io/escalante/sbt/lift/helloworld/snippet/HelloWorld.class"),
      "HelloWorld class not present" + war.getContent())
  assert(war.contains("index.html"), "index.html not present" + war.getContent())
  assert(war.contains("templates-hidden/default.html"),
      "templates-hidden/default.html not present" + war.getContent())
  // 3. Check HTTP request
  val writer = new StringWriter
  try {
    val url = new URL("http://localhost:8080/helloworld-nondefault-lift/index.html")
    println("Reading response from " + url + ":")
    val con = url.openConnection
    val is = new BufferedInputStream(con.getInputStream)
    try {
      var i = is.read
      while (i != -1) {
        writer.write(i.asInstanceOf[Char])
        i = is.read
      }
      assert(writer.toString.indexOf("Hello World!") > -1)
      println("OK")
    } finally {
      is.close
    }
  } finally {
    writer.close()
  }
}
