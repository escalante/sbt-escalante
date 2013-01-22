import sbt._
import sbt.Keys._
import ScriptedPlugin._

sbtPlugin := true

name := "sbt-escalante"

organization := "io.escalante.sbt"

version := "0.1.0"

resolvers += "JBoss repository" at "http://repository.jboss.org/nexus/content/groups/public/"

resolvers += "Project Odd repository" at "http://repository-projectodd.forge.cloudbees.com/upstream/"

libraryDependencies ++= Seq(
	"org.jboss.shrinkwrap" % "shrinkwrap-api" % "1.0.1",
	"org.jboss.shrinkwrap" % "shrinkwrap-impl-base" % "1.0.1",
   "io.escalante" % "escalante-dist" % "0.2.0-SNAPSHOT" artifacts(Artifact("escalante-dist", "zip", "zip")),
   "org.jboss.as" % "jboss-as-arquillian-container-embedded" % "7.x.incremental.546",
   "org.jboss.arquillian.core" % "arquillian-core-api" % "1.0.0.Final",
   "org.jboss.arquillian.core" % "arquillian-core-spi" % "1.0.0.Final",
   "org.jboss.arquillian.core" % "arquillian-core-impl-base" % "1.0.0.Final",
   "org.jboss.arquillian.container" % "arquillian-container-spi" % "1.0.0.Final",
   "org.jboss.arquillian.container" % "arquillian-container-impl-base" % "1.0.0.Final",
   "org.jboss.arquillian.test" % "arquillian-test-impl-base" % "1.0.0.Final",
   "org.yaml" % "snakeyaml" % "1.8"
)

ScriptedPlugin.scriptedSettings

// Force logging Scripted test output for both successful and failing tests
scriptedBufferLog := false

// Tests require more resources than those by default
scriptedLaunchOpts ++= Seq("-Xmx1024M", "-XX:MaxPermSize=256M")

publishTo <<= version { (v: String) =>
  val nexus = "https://repository.jboss.org/nexus/"
  if (v.trim.endsWith("SNAPSHOT"))
    Some("JBoss Snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("JBoss Releases"  at nexus + "service/local/staging/deploy/maven2")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { x => false }

pomExtra := (
  <url>http://escalante.io</url>
  <licenses>
    <license>
      <name>Apache License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <connection>scm:git:git://github.com/escalante/sbt-escalante.git</connection>
    <developerConnection>scm:git:ssh://git@github.com/escalante/sbt-escalante.git</developerConnection>
    <url>http://github.com/escalante/sbt-escalante</url>
  </scm>
  <developers>
    <developer>
      <id>escalante-team</id>
      <name>The Escalante Team</name>
      <email>escalante-dev@escalante.io</email>
    </developer>
  </developers>
)