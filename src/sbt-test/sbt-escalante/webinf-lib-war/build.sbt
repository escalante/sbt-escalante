import sbt._
import sbt.Keys._
import io.escalante.sbt.EscalantePlugin._
import io.escalante.sbt.EscalantePlugin.EscalanteKeys._
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.spec.WebArchive
import org.yaml.snakeyaml.Yaml
import scala.collection.JavaConversions._

version := "0.1"

escalanteSettings

scalaVersion := "2.9.1"

liftWarName in liftWar := "with-libs.war"

liftVersion in liftWar := Some("2.4")

libraryDependencies <++= (liftVersion in liftWar) { lv: Option[String] => Seq(
  "net.liftweb" %% "lift-webkit" % lv.get % "provided",
  "org.squeryl" %% "squeryl" % "0.9.5-6" % "compile",
  "org.fusesource.scalamd" % "scalamd_2.9" % "1.6")
}

TaskKey[Unit]("check") <<= (target) map { (target) =>
  // 1. Open war file and print contents
  val war = ShrinkWrap.createFromZipFile(
      classOf[WebArchive], target / "with-libs.war")
  val separator = System.getProperty("line.separator")
  println("War contents: %s%s".format(separator,
      war.getContent.values().iterator().map(_.toString).toSeq.sorted.mkString(separator)))
  // 2. Check classes and descriptor present
  assert (war.contains("WEB-INF/classes/Main.class"),
      "Main class not present: " + war.getContent())
  val descriptorPath = "META-INF/escalante.yml"
  assert (war.contains(descriptorPath), "escalante.yml missing: %s%s"
      .format(separator, war.getContent.values().mkString(separator)))
  // 2a. Check libraries which should and should not be present
  assert (war.contains("WEB-INF/lib/cglib-nodep-2.2.jar"),
      "cglib-nodep-2.2.jar not present: " + war.getContent())
  assert (war.contains("WEB-INF/lib/scalamd_2.9-1.6.jar"),
      "scalamd_2.9-1.6.jar not present: " + war.getContent())
  assert (war.contains("WEB-INF/lib/squeryl_2.9.1-0.9.5-6.jar"),
      "squeryl_2.9.1-0.9.5-6.jar not present: " + war.getContent())
  assert (!war.contains("WEB-INF/lib/scala-library-2.9.1.jar"),
      "scala-library-2.9.1.jar present: " + war.getContent())
  assert (!war.contains("WEB-INF/lib/scala-compiler-2.9.1.jar"),
      "scala-compiler-2.9.1.jar present: " + war.getContent())
  assert (!war.contains("WEB-INF/lib/scalap-2.9.1.jar"),
      "scalap-2.9.1.jar present: " + war.getContent())
  // 3. Check lift and scala versions in descriptor
  val descriptor = new Yaml().load(war.get(descriptorPath).getAsset.openStream)
      .asInstanceOf[java.util.Map[String, Object]]
  assert (descriptor.containsKey("lift"))
  val liftDescriptor = descriptor.get("lift")
      .asInstanceOf[java.util.Map[String, Object]]
  val liftVersion = liftDescriptor.get("version").toString
  assert (liftVersion.equals("2.4"), "Unexpected version: '" + liftVersion + "'")
}
