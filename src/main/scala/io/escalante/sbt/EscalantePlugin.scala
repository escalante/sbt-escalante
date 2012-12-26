// TODO: Missing package...

import collection.JavaConversions._
import java.util.jar.JarFile
import org.jboss.arquillian.container.spi.client.deployment.TargetDescription
import org.jboss.arquillian.container.spi.ContainerRegistry
import org.jboss.arquillian.container.spi.event.{StartContainer, SetupContainer}
import org.jboss.arquillian.core.impl.loadable.LoadableExtensionLoader
import org.jboss.arquillian.core.spi.{Manager, ManagerBuilder}
import org.jboss.as.controller.client.helpers.standalone.ServerDeploymentHelper
import org.jboss.as.controller.client.ModelControllerClient
import org.jboss.shrinkwrap.api.asset.{StringAsset, Asset}
import sbt._
import org.jboss.shrinkwrap.api.exporter.ZipExporter
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.spec.WebArchive
import scala.Some
import tools.nsc.io.Directory
import xml.Elem
import java.io.{OutputStream, InputStream, FileOutputStream, FileInputStream}

object EscalantePlugin extends Plugin {

  object EscalanteKeys {
    val liftWar = TaskKey[File]("escalante-lift-war")
    val liftWarName = SettingKey[String]("escalante-lift-war-name")
    val liftOutputPath = SettingKey[File]("escalante-lift-war-output-path")
    val liftVersion = SettingKey[Option[String]]("escalante-lift-version")
    val liftWebAppResources = SettingKey[File]("escalante-lift-webapp-resources")

    val escalanteVersion = SettingKey[String]("escalante-version")
    val escalanteRun = TaskKey[Unit]("escalante-run")
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
    // Lift assembly should be executed after test
    Keys.test in liftWar <<= (Keys.test in sbt.Test),
    // Check library dependencies to figure out building instructions
    Keys.libraryDependencies in liftWar <<= Keys.libraryDependencies,
    // Enables overriding lift version to write to descriptor
    // instead of relying on Lift version from dependencies
    liftVersion := None,
    // Default web app resources directory
    liftWebAppResources in liftWar <<= Keys.sourceDirectory / "main/webapp",
    // Build Escalante-friendly Lift war
    liftWar <<= (Keys.test in liftWar,
        Keys.classDirectory in liftWar,
        liftWarName in liftWar,
        liftOutputPath in liftWar,
        Keys.libraryDependencies in liftWar,
        liftVersion in liftWar,
        liftWebAppResources in liftWar) map {
      (test, classDir, warName, out, deps, liftVersion, webAppDir) =>
            buildLiftWar(classDir, warName, out, deps, liftVersion, webAppDir)
    },
    escalanteVersion := "0.2.0-SNAPSHOT",
    // Escalante run should be executed after lift was has been generated
    liftWar in escalanteRun <<= (liftWar in liftWar),
    escalanteRun <<= (liftWar in escalanteRun,
        escalanteVersion in escalanteRun,
        liftOutputPath in liftWar) map {
      (test, version, deployment) => runEscalante(version, deployment)
    }
  )

  private def buildLiftWar(classDir: File, warName: String,
        targetWar: File, deps: Seq[ModuleID],
        liftVersion: Option[String], webAppDir: File): File = {
    // Use plugin's classloader to load ShrinkWrap extensions
    val pluginClassLoader = classOf[ShrinkWrap].getClassLoader
    println("ShrinkWrap class loader is: " + pluginClassLoader)
    println("Lift version override: " + liftVersion)

    withClassLoader(pluginClassLoader) { Unit =>
      val war = ShrinkWrap.create(classOf[WebArchive], warName)
      addWebInfClasses(war, classDir)
      addWebResources(war, webAppDir)
      val liftXml = liftVersion match {
        case Some(v) => <lift-app version={v} />
        case None =>
          // Inspect libraries and generate Escalante descriptor
          deps.filter(_.organization == "net.liftweb").headOption match {
            case Some(liftArtifact) =>
                <lift-app version={liftArtifact.revision} />
            case None =>
              // TODO: Temporary, must handle other Escalante compatible apps
                <lift-app />
          }
      }
      war.addAsWebResource(xml(liftXml), "WEB-INF/lift.xml")

      println("Exporting " + targetWar)
      war.as(classOf[ZipExporter]).exportTo(targetWar, true)
      targetWar
    }
  }

  private def xml(e: Elem): Asset = new StringAsset(e.toString())

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

  def runEscalante(version: String, deployment: File) {
    // 1. With the AS7 zip dependency in place, unzip it
    val tmpDir = System.getProperty("java.io.tmpdir")
    val home = System.getProperty("user.home")
    val escalanteVersion = "escalante-" + version
    val jbossHome = tmpDir + escalanteVersion
    val jbossHomeDir = new File(jbossHome)
    val jbossCfg = new File(jbossHome
      + "/standalone/configuration/standalone.xml")

    if (!jbossCfg.exists())
      unzip(new File(home + "/.ivy2/cache/io.escalante/escalante-dist/zips/" +
        "escalante-dist-0.2.0-SNAPSHOT.zip/"),
        new File(tmpDir))

    // TODO: ALR, why not use 'jboss.home.dir' ? ServerEnvironment.HOME_DIR
    System.setProperty("jboss.home", jbossHomeDir.getAbsolutePath)

    val pluginClassLoader = classOf[Manager].getClassLoader
    println("Arquillian class loader is: " + pluginClassLoader)

    withClassLoader(pluginClassLoader) { Unit =>
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

  private def withClassLoader[T](cl: ClassLoader)(block: Unit => T): T = {
    val ctxClassLoader = Thread.currentThread().getContextClassLoader
    Thread.currentThread().setContextClassLoader(cl)
    try {
      block()
    } finally {
      Thread.currentThread().setContextClassLoader(ctxClassLoader)
    }
  }

  private def unzip(file: File, target: File) {
    val zip = new JarFile(file)
    enumerationAsScalaIterator(zip.entries).foreach {
      entry =>
        val entryPath = entry.getName
        println("Extracting to " + target.getCanonicalPath + "/" + entryPath)
        if (entry.isDirectory) {
          new File(target, entryPath).mkdirs
        } else {
          copy(zip.getInputStream(entry),
            new FileOutputStream(new File(target, entryPath)))
        }
    }
  }

  private def copy(in: InputStream, out: OutputStream) {
    try {
      try {
        val buffer = new Array[Byte](1024)
        Iterator.continually(in.read(buffer))
          .takeWhile(_ != -1)
          .foreach {
          out.write(buffer, 0, _)
        }
      } finally {
        out.close()
      }
    } finally {
      in.close()
    }
  }

}