import EscalanteKeys._
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.spec.WebArchive
import org.yaml.snakeyaml.Yaml
import scala.collection.JavaConversions._

version := "0.1"

escalanteSettings

scalaVersion := "2.9.2"

libraryDependencies += "net.liftweb" %% "lift-webkit" % "2.5-M3" % "provided"

TaskKey[Unit]("check") <<= (target) map { (target) =>
  // 1. Open war file and print contents
  val war = ShrinkWrap.createFromZipFile(
      classOf[WebArchive], target / "ROOT.war")
  val separator = System.getProperty("line.separator")
  println("War contents: %s%s".format(separator,
    war.getContent.values().mkString(separator)))
  // 2. Check classes and descriptor present
  assert (war.contains("WEB-INF/classes/Main.class"),
      "Main class not present: " + war.getContent())
  val descriptorPath = "META-INF/escalante.yml"
  assert (war.contains(descriptorPath), "escalante.yml missing: %s%s"
      .format(separator, war.getContent.values().mkString(separator)))
  // 3. Check lift and scala versions in descriptor
  val descriptor = new Yaml().load(war.get(descriptorPath).getAsset.openStream)
      .asInstanceOf[java.util.Map[String, Object]]
  assert (descriptor.containsKey("lift"))
  val liftDescriptor = descriptor.get("lift")
      .asInstanceOf[java.util.Map[String, Object]]
  val liftVersion = liftDescriptor.get("version")
  assert (liftVersion.equals("2.5-M3"), "Unexpected version: " + liftVersion)
}
