// Repositories
resolvers ++= Seq(
   "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
   "JBoss repository" at "http://repository.jboss.org/nexus/content/groups/public/",
   "Project Odd repository" at "http://repository-projectodd.forge.cloudbees.com/upstream/"
)

// Use the Play sbt plugin for Play projects
addSbtPlugin("play" % "sbt-plugin" % "2.1.1")

// Add Escalante plugin
addSbtPlugin("io.escalante.sbt" % "sbt-escalante" % "0.2.0-SNAPSHOT")