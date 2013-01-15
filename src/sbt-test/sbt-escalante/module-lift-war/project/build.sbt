resolvers ++= Seq(
   "JBoss repository" at "http://repository.jboss.org/nexus/content/groups/public/",
   "Project Odd repository" at "http://repository-projectodd.forge.cloudbees.com/upstream/"
)

addSbtPlugin("io.escalante" % "sbt-escalante" % "0.1.0-+")