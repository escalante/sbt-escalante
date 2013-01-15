# sbt-escalante

Escalante SBT plugin

## User

...

## Contributor

### Getting started as a Contributor

This section focuses on the steps required to build Escalante SBT plugin:

1. Install latest SBT following steps
[here|http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html].

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

