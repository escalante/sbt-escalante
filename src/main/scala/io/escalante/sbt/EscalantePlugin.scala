package io.escalante.sbt

import org.jboss.arquillian.container.spi.client.deployment.TargetDescription
import org.jboss.arquillian.container.spi.ContainerRegistry
import org.jboss.arquillian.container.spi.event.{StartContainer, SetupContainer}
import org.jboss.arquillian.core.impl.loadable.LoadableExtensionLoader
import org.jboss.arquillian.core.spi.{Manager, ManagerBuilder}
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper
import org.jboss.as.controller.client.ModelControllerClient
import org.jboss.shrinkwrap.api.asset.StringAsset
import sbt._
import org.jboss.shrinkwrap.api.exporter.ZipExporter
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.spec.WebArchive
import tools.nsc.io.Directory
import java.io.FileInputStream
import scala.xml.{Elem, NodeSeq, Node, XML}
import sbt.File
import scala.xml.transform.{RuleTransformer, RewriteRule}
import scala.xml.Node

object EscalantePlugin extends Plugin {

  final val NotProvidedByServerRegex =
    "^(?!.*(scala-compiler|scala-library|scalap)).*$".r

  object EscalanteKeys {
    val liftWar = TaskKey[File]("escalante-lift-war")
    val liftWarName = SettingKey[String]("escalante-lift-war-name")
    val liftOutputPath = SettingKey[File]("escalante-lift-war-output-path")
    val liftVersion = SettingKey[Option[String]]("escalante-lift-version")
    val liftWebAppResources = SettingKey[File]("escalante-lift-webapp-resources")
    val liftCopyDependencies = TaskKey[Unit]("escalante-lift-copy-dependencies")

    val escalanteRun = TaskKey[Unit]("escalante-run")
    val escalanteVersion = SettingKey[String]("escalante-version")
  }

  import EscalanteKeys._

  lazy val escalanteSettings: Seq[sbt.Project.Setting[_]] =
    baseEscalanteSettings

  lazy val baseEscalanteSettings: Seq[sbt.Project.Setting[_]] = Seq(
    // Assign default war name when missing
    liftWarName := "ROOT.war",
    // Target folder used by Lift assembly
    Keys.target in liftWar <<= Keys.target,
    // Class folder to add to war file
    Keys.classDirectory in liftWar <<= (Keys.classDirectory in sbt.Compile),
    // Default output path is target / warName
    liftOutputPath in liftWar <<=
      (Keys.target in liftWar, liftWarName in liftWar) {
        (t, s) => t / s
      },
    // Default copy-dependencies output path is target/lib
    liftOutputPath in liftCopyDependencies <<= (Keys.target in liftWar) { t => t / "lib"} ,
    // Copies runtime library-dependencies for packaging in war
    liftCopyDependencies in liftWar <<= (Keys.update, liftOutputPath in liftCopyDependencies)  map {
      (updateReport, target) => copyDependencies(updateReport, target)
    },
    // Lift assembly should be executed after test
    Keys.test in liftWar <<= (Keys.test in sbt.Test),
    // Check library dependencies to figure out building instructions
    Keys.libraryDependencies in liftWar <<= Keys.libraryDependencies,
    // Enables overriding lift version to write to descriptor
    // instead of relying on Lift version from dependencies
    liftVersion := None,
    // Default web app resources directory
    liftWebAppResources in liftWar <<= Keys.sourceDirectory / "main/webapp",
    // Scala version
    Keys.scalaVersion in liftWar <<= Keys.scalaVersion,
    // Build Escalante-friendly Lift war
    liftWar <<= (Keys.test in liftWar,
        liftCopyDependencies in liftWar,
        liftOutputPath in liftCopyDependencies,
        Keys.classDirectory in liftWar,
        liftWarName in liftWar,
        liftOutputPath in liftWar,
        Keys.libraryDependencies in liftWar,
        liftVersion in liftWar,
        liftWebAppResources in liftWar,
        Keys.scalaVersion in liftWar) map {
      (test, copyDeps, libsDir, classDir, warName, out, deps, liftVersion, webAppDir, scalaVersion) =>
            buildLiftWar(classDir, libsDir, warName, out, deps, liftVersion, webAppDir, scalaVersion)
    },
    escalanteVersion := "0.3.0-SNAPSHOT",
    // Escalante run should be executed after lift war has been generated
    liftWar in escalanteRun <<= (liftWar in liftWar),
    escalanteRun <<= (liftWar in escalanteRun,
        escalanteVersion in escalanteRun,
        liftOutputPath in liftWar) map {
      (test, version, deployment) => runEscalante(version, deployment)
    }
  )

  private def buildLiftWar(classDir: File, libsDir: File, warName: String,
        targetWar: File, deps: Seq[ModuleID],
        liftVersionOverride: Option[String], webAppDir: File,
        scalaVersion: String): File = {
    // Use plugin's classloader to load ShrinkWrap extensions
    val pluginClassLoader = classOf[ShrinkWrap].getClassLoader
    println("ShrinkWrap class loader is: " + pluginClassLoader)
    println("Lift version override: " + liftVersionOverride)

    withClassLoader(pluginClassLoader) { () =>
      val war = ShrinkWrap.create(classOf[WebArchive], warName)
      addWebInfClasses(war, classDir)
      addWebResources(war, webAppDir)
      addWebInfLibs(war, libsDir)
      val extraModules = extractExtraModules(deps)
      val liftVersion = liftVersionOverride match {
        case Some(version) => liftVersionOverride
        case None =>
          // Inspect libraries dependencies and retrieve Lift version
          deps.filter(_.organization == "net.liftweb")
              .headOption.map(_.revision)
      }

      val srcDescriptor = new File(webAppDir, "META-INF/escalante.yml")
      println("Check if Escalante descriptor present in: %s".format(srcDescriptor))
      if (srcDescriptor.exists()) {
        println("Using Escalante descriptor in: %s".format(srcDescriptor))
      } else {
        val descriptor = getDescriptor(liftVersion, scalaVersion, extraModules)
        war.addAsWebResource(new StringAsset(descriptor), "META-INF/escalante.yml")
        print("""Generated Escalante descriptor:
                | %s""".format(descriptor).stripMargin)
        println() // Extra line to clear end of descriptor white space
      }

      println("Exporting " + targetWar)
      war.as(classOf[ZipExporter]).exportTo(targetWar, true)
      targetWar
    }
  }

  private def extractExtraModules(deps: Seq[ModuleID]): Seq[String] = {
    deps.filter(_.organization == "net.liftweb")
      .filter(_.name != "lift-webkit")
      .map(_.name.substring("lift-".length)).toSeq
  }

  private def getDescriptor(
      liftVersion: Option[String],
      scalaVersion: String,
      extraModules: Seq[String]): String = {
    val separator = System.getProperty("line.separator")
    val modulesAsString = extraModules.map { moduleName =>
      """|     - %s""".format(moduleName).stripMargin
    }

    (liftVersion, extraModules) match {
      case (None, Nil) =>
        """
          | scala:
          |   version: %s
        """.format(scalaVersion).stripMargin
      case (Some(lv), Nil) =>
        """
          | scala:
          |   version: %s
          | lift:
          |   version: %s
        """.format(scalaVersion, lv).stripMargin
      case (None, List(_*)) =>
        """
          | scala:
          |   version: %s
          | lift:
          |   modules: %s
        """.format(scalaVersion,
          modulesAsString.mkString).stripMargin
      case (Some(lv), List(_*)) =>
        """
          | scala:
          |   version: %s
          | lift:
          |   version: %s
          |   modules: %s
        """.format(scalaVersion, lv,
          separator + modulesAsString.mkString(separator)).stripMargin
    }
  }

  private def addWebResources(war: WebArchive, webAppDir: File) {
    println("Web resources dir is: " + webAppDir)
    Directory(webAppDir).deepFiles.foreach { file =>
      println("Add web resource: " + file.jfile)
      war.addAsWebResource(file.jfile, file.jfile.getPath.substring(
        webAppDir.getAbsolutePath.length))
    }
  }

  private def addWebInfClasses(war: WebArchive, classDir: File) {
    println("Class dir is: " + classDir)
    Directory(classDir).deepFiles.foreach { file =>
      println("Add file: " + file.jfile)
      war.addAsWebResource(file.jfile, "WEB-INF/classes" +
              file.jfile.getPath.substring(classDir.getAbsolutePath.length))
    }
  }

  private def addWebInfLibs(war: WebArchive, libDir: File) {
    println("Lib dir is: " + libDir)
    Directory(libDir).deepFiles.foreach { file =>
      println("Add library: " + file.jfile)
      war.addAsLibrary(file.jfile)
    }
  }

  def copyDependencies(updateReport: UpdateReport, libDir: File) {
    updateReport.select(configurationFilter("runtime"))
      .filter(f => NotProvidedByServerRegex.findFirstIn(f.getName).isDefined)
      .foreach(f => IO.copyFile(f, libDir / f.getName, preserveLastModified = true))
  }

  def runEscalante(version: String, deployment: File) {
    // 1. With the AS7 zip dependency in place, unzip it
    val tmpDir = new File(System.getProperty("java.io.tmpdir"))
    val home = System.getProperty("user.home")
    val escalanteVersion = "escalante-" + version
    val jbossHomeDir = new File(tmpDir, escalanteVersion)
    val jbossCfg = new File(jbossHomeDir, "/standalone/configuration/standalone.xml")

    if (!jbossCfg.exists())
      unzipEscalante(home, version)

    // Remove Logging extension and subsystem to avoid issues with SBT logging
    removeEscalanteLogging(jbossCfg)

    println("Run Escalante %s from %s".format(version, jbossHomeDir.getAbsolutePath))

    // TODO: ALR, why not use 'jboss.home.dir' ? ServerEnvironment.HOME_DIR
    System.setProperty("jboss.home", jbossHomeDir.getAbsolutePath)

    val pluginClassLoader = classOf[Manager].getClassLoader
    println("Arquillian class loader is: " + pluginClassLoader)

    withClassLoader(pluginClassLoader) { () =>
      // 1. Start arquillian manager
      val manager = ManagerBuilder.from()
        .extension(classOf[LoadableExtensionLoader]).create()
      manager.start()
      // 2. Resolve container registry
      val registry = manager.resolve(classOf[ContainerRegistry])
      if (registry == null)
        throw new IllegalStateException("No ContainerRegistry found in Context. " +
          "Something is wrong with the classpath.....")

      if (registry.getContainers.size() == 0)
        throw new IllegalStateException("No Containers in registry. " +
          "You need to add the Container Adaptor dependencies to the plugin dependency section")

      // 3. Get container
      val container = registry.getContainer(TargetDescription.DEFAULT)
      println("to container: " + container.getName)

      // 4. Fire events to setup and start
      manager.fire(new SetupContainer(container))
      manager.fire(new StartContainer(container))

      // 5. Deploy war archive
      val deployer = new ServerDeploymentHelper(
        ModelControllerClient.Factory.create("127.0.0.1", 9999))
      deployer.deploy(deployment.getName, new FileInputStream(deployment))
    }

  }

  private def unzipEscalante(home: String, version: String) {
    val tmpDir = new File(System.getProperty("java.io.tmpdir"))
    val ivyEscalanteZip = new File(
      "%s/.ivy2/cache/io.escalante/escalante-dist/zips/escalante-dist-%s.zip/"
        .format(home, version))

    if (ivyEscalanteZip.exists()) {
      println("Unzip Escalante distribution in " + ivyEscalanteZip)
      IO.unzip(ivyEscalanteZip, tmpDir)
    } else {
      if (version.endsWith("-SNAPSHOT")) {
        println("Escalante version %s not available locally, download...".format(version))
        val nexusRoot = "https://repository.jboss.org/nexus/content/groups/public"
        val escalanteNexusRoot = "%s/io/escalante/escalante-dist/%s".format(nexusRoot, version)
        val mavenMetadataFile = new File(tmpDir, "maven-metadata.xml")
        val mavenMetadataUrl = new URL(
          "%s/maven-metadata.xml".format(escalanteNexusRoot))

        IO.download(mavenMetadataUrl, mavenMetadataFile)

        val xml = XML.loadFile(mavenMetadataFile)
        val timestamp = xml \\ "metadata" \\ "versioning" \\ "snapshot" \\ "timestamp"
        val buildNumber = xml \\ "metadata" \\ "versioning" \\ "snapshot" \\ "buildNumber"
        val snapshotLessVersion = version.substring(0, version.lastIndexOf('-'))
        val escalanteSnapshotUrl = new URL("%s/escalante-dist-%s-%s-%s.zip"
          .format(escalanteNexusRoot, snapshotLessVersion, timestamp.text, buildNumber.text))

        println("Download and unzip Escalante snapshot from: " + escalanteSnapshotUrl)
        IO.unzipURL(escalanteSnapshotUrl, tmpDir)
      }
    }
  }

  def removeEscalanteLogging(jbossCfg: File) {
    val xmlCfg = XML.loadFile(jbossCfg)

    val removeLoggingExtension = new RewriteRule {
      override def transform(n: Node): NodeSeq = n match {
        case e: Elem if e.label == "extension"
          && (e \ "@module").text == "org.jboss.as.logging" => NodeSeq.Empty
        case n => n
      }
    }

    val removeLoggingSubsystem = new RewriteRule {
      override def transform(n: Node): NodeSeq = n match {
        case e: Elem if e.label == "subsystem"
          && e.namespace.contains("jboss:domain:logging") => NodeSeq.Empty
        case n => n
      }
    }

    val loggingRemovedXml = new RuleTransformer(
      removeLoggingExtension, removeLoggingSubsystem).transform(xmlCfg).head

    XML.save(jbossCfg.getCanonicalPath, loggingRemovedXml, xmlDecl = true, doctype = null)
  }

  private def withClassLoader[T](cl: ClassLoader)(block: () => T): T = {
    val ctxClassLoader = Thread.currentThread().getContextClassLoader
    Thread.currentThread().setContextClassLoader(cl)
    try {
      block()
    } finally {
      Thread.currentThread().setContextClassLoader(ctxClassLoader)
    }
  }

}