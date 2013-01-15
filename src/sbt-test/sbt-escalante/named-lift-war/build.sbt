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

liftWarName in liftWar := "named.war"

scalaVersion := "2.9.1"

liftVersion in liftWar := Some("2.4")

libraryDependencies <+= (liftVersion in liftWar) { lv: Option[String] =>
  "net.liftweb" %% "lift-webkit" % lv.get % "provided"
}

TaskKey[Unit]("check") <<= (target) map { (target) =>
  // 1. Open war file and print contents
  val war = ShrinkWrap.createFromZipFile(
      classOf[WebArchive], target / "named.war")
  val separator = System.getProperty("line.separator")
  println("War contents: %s%s".format(separator,
    war.getContent.values().mkString(separator)))
  // 2. Check classes and descriptor present
  assert(war.contains("WEB-INF/classes/helloworld/Main.class"),
      "Main class not present" + war.getContent())
  val descriptorPath = "META-INF/escalante.yml"
  assert (war.contains(descriptorPath), "escalante.yml missing: %s%s"
      .format(separator, war.getContent.values().mkString(separator)))
  // 3. Check lift and scala versions in descriptor
  val descriptor = new Yaml().load(war.get(descriptorPath).getAsset.openStream)
      .asInstanceOf[java.util.Map[String, Object]]
  assert (descriptor.containsKey("lift"))
  val liftDescriptor = descriptor.get("lift")
      .asInstanceOf[java.util.Map[String, Object]]
  val liftVersion = liftDescriptor.get("version").toString
  assert (liftVersion == "2.4", "Unexpected version: " + liftVersion)
  assert (descriptor.containsKey("scala"))
  val scalaDescriptor = descriptor.get("scala")
    .asInstanceOf[java.util.Map[String, Object]]
  val scalaVersion = scalaDescriptor.get("version").toString
  assert (scalaVersion == "2.9.1",
    "Unexpected Scala version: " + scalaVersion)
}
