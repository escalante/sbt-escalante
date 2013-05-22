/*
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package helloworld.project

import sbt._
import Keys._
import play.Project._
import io.escalante.sbt.EscalantePlugin._
import org.yaml.snakeyaml.Yaml
import java.io.FileInputStream
import java.io.{BufferedInputStream, StringWriter}
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper
import org.jboss.as.controller.client.ModelControllerClient

object ApplicationBuild extends Build {

  val appName         = "play-run"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    // Add your project dependencies here,
  )

  val check = TaskKey[Unit]("check")

  val main = play.Project(appName, appVersion, appDependencies).settings(escalanteSettings : _*)
    .settings(
      // Add your own project settings here
      check <<= (baseDirectory, target) map ( (dir, t) => checkTask(dir, t)),
      javacOptions ++= Seq("-source", "1.6", "-target", "1.6", "-encoding", "UTF-8")
    )

  def checkTask(baseDir: File, target: File) {
    // 1. Open descriptor and print contents
    val playFile = target / "play-run.yml"
    val content = scala.io.Source.fromFile(playFile).mkString
    println("Play descriptor contents: %s".format(content))

    // 2. Check descriptor content
    val descriptor = new Yaml().load(new FileInputStream(playFile))
        .asInstanceOf[java.util.Map[String, Object]]
    assert(descriptor.containsKey("play"))
    val playDescriptor = descriptor.get("play")
      .asInstanceOf[java.util.Map[String, Object]]
    val path = playDescriptor.get("path").toString
    assert(path != null && !path.isEmpty, "Unexpected path: '" + path + "'")

    // 3. Check HTTP request
    val writer = new StringWriter
    try {
      val url = new URL("http://localhost:9000/play-run")
      println("Reading response from " + url + ":")
      val con = url.openConnection
      val is = new BufferedInputStream(con.getInputStream)
      try {
        var i = is.read
        while (i != -1) {
          writer.write(i.asInstanceOf[Char])
          i = is.read
        }
        assert(writer.toString.indexOf("Hello (Escalante) Play!") > -1)
        println("OK")
      } finally {
        is.close()
      }
    } finally {
      writer.close()

      // Undeploy to avoid issues
      val deployer = new ServerDeploymentHelper(
        ModelControllerClient.Factory.create("127.0.0.1", 9999))
      deployer.undeploy("play-run.yml")
    }
  }

}
