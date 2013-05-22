/*
 * Copyright 2013 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License version 1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

import java.io.FileInputStream
import sbt._
import Keys._
import play.Project._
import io.escalante.sbt.EscalantePlugin._
import org.yaml.snakeyaml.Yaml

object ApplicationBuild extends Build {

  val appName         = "play-modules-gen"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    // Add your project dependencies here,
    jdbc,
    anorm
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
    val playFile = target / "play-modules-gen.yml"
    val content = scala.io.Source.fromFile(playFile).mkString
    println("Play descriptor contents: %s".format(content))

    // 2. Verify contents of descriptor
    // 2. Check descriptor content
    val descriptor = asMap(new Yaml().load(new FileInputStream(playFile)))
    assert(descriptor.containsKey("play"))
    val playDescriptor = asMap(descriptor.get("play"))
    val path = playDescriptor.get("path").toString
    assert(path.equals(baseDir.getAbsolutePath), "Unexpected path: '" + path + "'")
    val modules = asList(playDescriptor.get("modules"))
    assert(modules.size() == 2, "Should have had 2 modules in: " + modules)
    assert(modules.contains("play-jdbc"))
    assert(modules.contains("anorm"))
  }

  private def asMap(o: Object): java.util.Map[String, Object] =
    o.asInstanceOf[java.util.Map[String, Object]]

  private def asList(o: Object): java.util.List[String] =
    o.asInstanceOf[java.util.List[String]]

}
