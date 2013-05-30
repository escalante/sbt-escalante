# sbt-escalante

Escalante SBT plugin

## User Guide

First of all, install latest SBT following steps
[here](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html).
Escalante requires SBT version `0.12.x` or higher.

### Set up Escalante SBT plugin in your project

To add Escalante SBT plugin to your build, add the following
to `project/build.sbt`:

    import sbt._
    import sbt.Keys._

    resolvers ++= Seq(
       "JBoss repository" at "http://repository.jboss.org/nexus/content/groups/public/",
       "Project Odd repository" at "http://repository-projectodd.forge.cloudbees.com/upstream/"
    )

    addSbtPlugin("io.escalante.sbt" % "sbt-escalante" % "0.2.0")

Now, add the following to your root `build.sbt` file:

    import sbt._
    import sbt.Keys._
    import io.escalante.sbt.EscalantePlugin._
    import io.escalante.sbt.EscalantePlugin.EscalanteKeys._

    escalanteSettings

### Build a Escalante Lift war

To build a Escalante Lift war, simply run the following from the SBT console:

    > escalante-lift-war

By default, this will generate a WAR file called `ROOT.war` including
compiled classes and resources in `src/webapp`. This war file will also
include a `META-INF/escalante.yml` file which, unless present already in the
webapp resource, it will be generated from the build metadata information.

The following options are configurable:

* `liftWarName := "ROOT.war"`:
defines the name of the WAR file to generate (default value shown).
* `liftVersion`:
defines the Lift version of this application, which is
primarily used to populate the correct Lift version in the generated
`META-INF/escalante.yml` descriptor. The default value is extracted from the
Lift dependencies defined in the build.

### Deploy and Run a Escalante Lift war

To run a Escalante Lift application, simply run the following from the SBT
console:

    > escalante-run

This task will first compile classes, call `escalante-lift-war` to generate
the Escalante Lift war archive, and finally it'll start an Escalante instance
where it will deploy the war archive.

The following options are configurable:

* `escalanteVersion := "0.2.0"`:
defines the Escalante version in which to run the Lift application
(default value shown).

## Contributor Guide

### Getting started as a Contributor

This section focuses on the steps required to build Escalante SBT plugin:

1. Install latest SBT following steps
[here](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html).

2. Execute SBT from root of Escalante SBT source code:

    <pre><code>[g@:~/sbt-escalante.git]% sbt</code></pre>

3. If you want to import source code into IntelliJ, type:

    <pre><code>&gt; gen-idea</code></pre>

### Running unit tests

Escalante SBT plugin contains a bunch unit tests which can be run executing
the following from the SBT console:

    > scripted

Alternatively, you can run individual unit tests this way:

    > scripted sbt-escalante/run-escalante

### Publishing Plugin Snapshots

To publish a SNAPSHOT of this SBT plugin you need to have credentials to be
able to push to JBoss Nexus repository. Once you have these credentials,
create a file in `.ivy2/.credentials` with these contents:

    realm=Sonatype Nexus Repository Manager
    host=repository.jboss.org
    user=<YOUR_USER_NAME>
    password=<YOUR_PASSWORD>

Once that file is in place, execute the following from the SBT console:

    > publish

### Forcing applications to use new Plugin Snapshots

TODO (should just work... https://github.com/sbt/sbt/issues/646)
