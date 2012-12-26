import EscalanteKeys._
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.spec.WebArchive
import scala.xml.XML
import java.net.URL
import java.io.{BufferedInputStream, StringWriter}

version := "0.1"

escalanteSettings

liftWarName in liftWar := "helloworld-lift.war"

// Testing with Scala 2.9.1 throws OOME perm space (even with massive max perm size)
// Lift 2.4 not available for Scala 2.9.2, so stick to testing latest 2.5 milestone

libraryDependencies ++= Seq(
  "net.liftweb" %% "lift-webkit" % "2.5-M3" % "provided"
)

// Override lift version to generate a war that can be deployed in Escalante

liftVersion in liftWar := Some("2.4")

TaskKey[Unit]("check") <<= (target) map { (target) =>
  // Check contents of war file
  val war = ShrinkWrap.createFromZipFile(
      classOf[WebArchive], target / "helloworld-lift.war")
  assert(war.contains("WEB-INF/classes/bootstrap/liftweb/Boot.class"),
      "Boot class not present" + war.getContent())
  assert(war.contains("WEB-INF/classes/io/escalante/sbt/lift/helloworld/snippet/HelloWorld.class"),
      "HelloWorld class not present" + war.getContent())
  assert(war.contains("index.html"), "index.html not present" + war.getContent())
  assert(war.contains("templates-hidden/default.html"),
      "templates-hidden/default.html not present" + war.getContent())
  val writer = new StringWriter
  try {
    val url = new URL("http://localhost:8080/helloworld-lift/index.html")
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
