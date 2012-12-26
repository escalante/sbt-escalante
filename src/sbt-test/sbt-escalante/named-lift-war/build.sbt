import EscalanteKeys._
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.spec.WebArchive
import scala.xml.XML

version := "0.1"

escalanteSettings

liftWarName in liftWar := "named.war"

// Testing with Scala 2.9.1 throws OOME perm space (even with massive max perm size)
// Lift 2.4 not available for Scala 2.9.2, so stick to testing latest 2.5 milestone

libraryDependencies += "net.liftweb" %% "lift-webkit" % "2.5-M3" % "provided"

// Override lift version to generate a war that can be deployed in Escalante

liftVersion in liftWar := Some("2.4")

TaskKey[Unit]("check") <<= (target) map { (target) =>
  // Check contents of war file
  val war = ShrinkWrap.createFromZipFile(
      classOf[WebArchive], target / "named.war")
  assert(war.contains("WEB-INF/classes/helloworld/Main.class"),
      "Main class not present" + war.getContent())
  val liftXmlPath = "WEB-INF/lift.xml"
  assert(war.contains(liftXmlPath), "lift.xml missing: " + war.getContent())
  val liftXml = XML.load(war.get(liftXmlPath).getAsset.openStream)
  liftXml match {
    case <lift-app>{contents @ _*}</lift-app> =>
      val version = (liftXml \ "@version").toString
      assert (version == "2.4", "Unexpected version: " + version)
    case _=> assert(false, "Unexpected lift.xml contents: " + liftXml.toString)
  }
}