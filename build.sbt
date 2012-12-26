import ScriptedPlugin._

sbtPlugin := true

name := "sbt-escalante"

organization := "io.escalante"

version := "0.1.0-SNAPSHOT"

resolvers += "JBoss repository" at "http://repository.jboss.org/nexus/content/groups/public/"

resolvers += "Project Odd repository" at "http://repository-projectodd.forge.cloudbees.com/upstream/"

// TODO: Add excludes where necessary
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
   "org.jboss.arquillian.test" % "arquillian-test-impl-base" % "1.0.0.Final"
)

ScriptedPlugin.scriptedSettings

// Force logging Scripted test output for both successful and failing tests
scriptedBufferLog := false

// Tests require more resources than those by default
scriptedLaunchOpts ++= Seq("-Xmx1024M", "-XX:MaxPermSize=256M")

// To run all tests, execute: 'scripted'
// To run an individual test, execute: 'scripted sbt-escalante/default-lift-war'