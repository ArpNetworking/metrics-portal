Metrics Portal
==============

<a href="https://raw.githubusercontent.com/ArpNetworking/metrics-portal/master/LICENSE">
    <img src="https://img.shields.io/hexpm/l/plug.svg"
         alt="License: Apache 2">
</a>
<a href="https://travis-ci.org/ArpNetworking/metrics-portal/">
    <img src="https://travis-ci.org/ArpNetworking/metrics-portal.png?branch=master"
         alt="Travis Build">
</a>
<a href="http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.arpnetworking.metrics%22%20a%3A%22metrics-portal%22">
    <img src="https://img.shields.io/maven-central/v/com.arpnetworking.metrics/metrics-portal.svg"
         alt="Maven Artifact">
</a>
<a href="https://hub.docker.com/r/arpnetworking/metrics-portal">
    <img src="https://img.shields.io/docker/pulls/arpnetworking/metrics-portal.svg" alt="Docker">
</a>

Provides a web interface for managing the Inscope Metrics stack. This includes viewing telemetry (aka streaming
statistics) from one or more hosts running [Metrics Aggregator Daemon](https://github.com/ArpNetworking/metrics-aggregator-daemon).
The web interface also provides a feature for browsing hosts reporting metrics, viewing and editing alerts, scheduling
and delivering reports and performing roll-ups in KairosDb.

Setup
-----

### Installing

#### Source

Clone the repository and build the source. The artifacts from the build are in `target/metrics-portal-${VERSION}-bin.tgz`
where `${VERSION}` is the current build version. To install, copy the artifact directories recursively into an
appropriate target directory on your Metrics Portal host(s). For example:

    metrics-portal> ./jdk-wrapper.sh ./mvnw package -Pno-docker
    metrics-portal> scp -r target/metrics-portal-${VERSION}-bin.tgz my-host.example.com:/opt/metrics-portal/

#### Tar.gz

Additionally, Metrics Portal releases a `tar.gz` package of its build artifacts which may be obtained from Github releases. To install,
download the archive and explode it. Replace `${VERSION}` with the release version of Metrics Portal you are installing.
For example, if your Metrics Portal host(s) have Internet access you can install directly:

    > ssh -c 'curl -L https://github.com/ArpNetworking/metrics-portal/releases/download/v${VERSION}/metrics-portal-${VERSION}-bin.tgz | tar -xz -C /var/tmp/metrics-portal/' my-host.example.com

Otherwise, you will need to download locally and distribute it before installing. For example:

    > curl -L https://github.com/ArpNetworking/metrics-portal/releases/download/v${VERSION}/metrics-portal-${VERSION}-bin.tgz -o /var/tmp/metrics-portal.tgz
    > scp /var/tmp/metrics-portal.tgz my-host.example.com:/var/tmp/
    > ssh -c 'tar -xzf /var/tmp/metrics-portal.tgz -C /opt/metrics-portal/' my-host.example.com

#### RPM

Alternatively, each release of Metrics Portal also creates an RPM which is available on Github releases. To install,
download the RPM and install it. For example, if your Metrics Portal host(s) have Internet access you can install
directly:

    > ssh -c 'sudo rpm -i https://github.com/ArpNetworking/metrics-portal/releases/download/v${VERSION}/metrics-portal-${VERSION}-1.noarch.rpm' my-host.example.com

Otherwise, you will need to download the RPM locally and distribute it before installing. For example:

    > curl -L https://github.com/ArpNetworking/metrics-portal/releases/download/v${VERSION}/metrics-portal-${VERSION}-1.noarch.rpm -o /var/tmp/metrics-portal.rpm
    > scp /var/tmp/metrics-portal.rpm my-host.example.com:/var/tmp/
    > ssh -c 'rpm -i /var/tmp/metrics-portal.rpm' my-host.example.com

Please note that if your organization has its own authorized package repository you will need to work with your system
administrators to install the Metrics Portal RPM into your package repository for installation on your Metrics Portal
host(s).

#### Docker

Furthermore, if you use Docker each release of Metrics Portal also publishes a [Docker image](https://hub.docker.com/r/arpnetworking/metrics-portal/)
that you can either install directly or extend.

If you install the image directly you will likely need to mount either a local directory or data volume with your
organization specific configuration.

If you extend the image you can embed your configuration file directly in your Docker image.

Regardless, you can override the provided configuration by first importing
`portal.application.conf` in your configuration file like this:

    include required("portal.application.conf")

Next set the `METRICS_PORTAL_CONFIG` environment variable to `-Dconfig.file="your_file_path"` like this:

    docker run ... -e 'METRICS_PORTAL_CONFIG=-Dconfig.file="/opt/metrics-portal/config/custom.conf"' ...

In addition to `METRICS_PORTAL_CONFIG`, you can specify:

* `LOGBACK_CONFIG` - Location of Logback configuration XML; default is `-Dlogger.file=/opt/metrics-portal/config/logback.xml`
* `JVM_XMS` - Java initial memory allocation; default is `64m`
* `JVM_XMX` - Java maximum memory allocation; default is `1024m`
* `JAVA_OPTS` - Additional Java arguments; many arguments are passed by [https://github.com/ArpNetworking/metrics-portal/blob/master/main/docker/Dockerfile](default).

### Execution

#### Non-Docker

Regardless of your installation method, in the installation's `bin` sub-directory there is a script to start the Metrics
Portal: `metrics-portal`.  This script should be executed on system start with appropriate parameters. In general:

    /opt/metrics_portal/bin/metrics-portal <JVM ARGS> -- <APP ARGS>

For example:

    /opt/metrics_portal/bin/metrics-portal -Xms512m -- /opt/metrics-portal

Arguments before the `--` are interpreted by the JVM while arguments after `--` are passed to Metrics Portal.

##### Reporting

If you have reporting enabled (`reports.enabled = true` in `portal.application.conf`), and you want to render web-based reports, you will need to have Chrome or Chromium installed alongside Metrics Portal, and set the `chromePath` configuration for those renderers to point to the appropriate executable file.

#### Docker

If you installed Metrics Portal using a Docker image then execution is very simple. In general:

    docker run -p 8080:8080 <DOCKER ARGS> arpnetworking/metrics-portal

For example:

    docker run -p 8080:8080 -e 'JAVA_OPTS=-Xms512m' arpnetworking/metrics-portal

The section above on Docker installation covers how to pass arguments in more detail.

### Configuration

Aside from the JVM command line arguments, you may provide two additional configuration files.

#### Logback

The first is the [LogBack](http://logback.qos.ch/) configuration file.  To use a custom logging configuration simply
pass the following argument to the JVM:

    -Dlogger.file=/opt/metrics-portal/custom-logger.xml

Where `/opt/metrics_portal/custom-logger.xml` is the path to your logging configuration file. Please refer to
[LogBack](http://logback.qos.ch/) documentation for more information on how to author a configuration file.

Installation via RPM or Docker will use the [production file logging configuration file](main/config/logback.xml) by
default. However, other installation methods will use the [debugging logging configuration file](conf/logback.xml) by
default and users are *strongly* recommended to override this behavior.

Metrics Portal ships with a second [production console logging configuration file](main/config/logback-console.xml)
which outputs to standard out instead of to a rotated and gzipped file.

#### Application

The second configuration file is for the application. To use a custom configuration simply pass the following argument to
the JVM:

    -Dconfig.file=/opt/metrics_portal/custom.conf

Where `/opt/metrics_portal/custom.conf` is the path to your application configuration file.

Installation via RPM or Docker will use the included [default application configuration file](conf/portal.application.conf).
This configuration documents and demonstrates many of the configuration options available.

To use the default application configuration file for non-RPM and non-Docker installations use a command like this:

    /opt/metrics_portal/bin/metrics-portal -Dconfig.resource=conf/portal.application.conf -- /opt/metrics-portal

Metrics Portal ships with two additional application configuration files, `[postgresql.application.conf](conf/postgresql.application.conf)`
for using [Postgresql](https://www.postgresql.org) as the data store and another `[cassandra.application.conf](conf/cassandra.application.conf)`
for using [Cassandra](http://cassandra.apache.org/) as the data store. You can specify one of these by adding the following
to argument to the JVM:

For Postgresql:
    -Dconfig.resource=conf/postgresql.application.conf

For Cassandra:
    -Dconfig.resource=conf/cassandra.application.conf

Both of these configuration files derive from the base configuration file, and it is recommended that you use one of these
as your base configuration. Additionally, both support overrides for locating the specific data store instance. Please
refer to these files when configuring your Metrics Portal instance.

Finally, while it is possible to leverage the provided configuration files, it is *strongly* recommended that users author
a custom application configuration and that you inherit from the default application configuration file and provide any
desired configuration as overrides. Please refer to [Play Framework](https://www.playframework.com/documentation/2.6.x/ProductionConfiguration)
documentation for more information on how to author a configuration file.

### Extension

The Metrics Portal project intentionally uses a custom default application configuration and custom default routes
specification. This allows projects extending the Metrics Portal to supplement functionality more easily with the
standard default application configuration and routes. To use these files as extensions rather than replacements you
should make the following changes.

First, add dependencies on the Metrics Portal code and assets in __conf/Build.scala__:

    "com.arpnetworking.metrics" %% "metrics-portal" % "VERSION"

Second, your extending project's application configuration should include one of the custom default configuration in __conf/application.conf__:

Base:

    include "portal.application.conf"
    
Postgresql:

    include "postgresql.application.conf"
    
Cassandra:

    include "cassandra.application.conf"

Third, your extending project's application configuration should restore the default router in __conf/application.conf__:

    application.router = null

Finally, your extending project's routes specification should include the custom default routes in __conf/routes__:

    -> / portal.Routes

### Building

Prerequisites:
* [Docker](http://www.docker.com/) (for [Mac](https://docs.docker.com/docker-for-mac/))
* [Node](https://nodejs.org/en/download/)

Building:

    metrics-portal> ./jdk-wrapper.sh ./mvnw verify

Building without Docker (will disable integration tests):

    metrics-portal> ./jdk-wrapper.sh ./mvnw -Pno-docker verify

To control which verification targets (e.g. Checkstyle, Findbugs, Coverage, etc.) are run please refer to the
[parent-pom](https://github.com/ArpNetworking/arpnetworking-parent-pom) for parameters (e.g. `-DskipAllVerification=true`).

When launching Metrics Portal via Play (e.g. `play2:run`) there is limited support for automatic recompiling and
reloading of assets (e.g. HTML, Typescript, etc.).

To run the server on port 8080 and its dependencies launched via Docker:

    metrics-portal> ./jdk-wrapper.sh ./mvnw docker:start

To stop the server and its dependencies run; this is recommended in place of `docker kill` as it will also remove the
container and avoids name conflicts on restart:

    metrics-portal> ./jdk-wrapper.sh ./mvnw docker:stop

To run the server on port 8080 _without_ dependencies via Play; you need to configure/provide/launch dependencies manually (see below):

    metrics-portal> ./jdk-wrapper.sh ./mvnw play2:run -Dconfig.resource=postgresql.application.conf -Dpostgres.port=6432

To debug on port 9002 with the server on port 8080 and its dependencies launched via Docker:

    metrics-portal> ./jdk-wrapper.sh ./mvnw -Ddebug=true docker:start

To debug on port 9002 with the server on port 8080 via Play; you need to configure/provide/launch dependencies manually (see below):

    metrics-portal> MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=9002" ./jdk-wrapper.sh ./mvnw play2:run -Dconfig.resource=postgresql.application.conf -Dpostgres.port=6432

To launch dependencies only via Docker:

    metrics-portal> ./jdk-wrapper.sh ./mvnw docker:start -PdependenciesOnly -Dpostgres.port=6432

To execute unit performance tests:

    metrics-portal> ./jdk-wrapper.sh ./mvnw -PperformanceTest test
    ^ TODO(ville): This is not yet implemented.

To execute integration performance tests:

    metrics-portal> ./jdk-wrapper.sh ./mvnw -PperformanceTest verify
    ^ TODO(ville): This is not yet implemented.

To use the local version as a dependency in your project you must first install it locally:

    metrics-portal> ./jdk-wrapper.sh ./mvnw install

### Testing

* Unit tests (`test/java/**/*Test.java`) may be run or debugged directly from your IDE.
* Integration tests may be run or debugged directly from your IDE provided an instance of Metrics Portal and its
dependencies are running locally on the default ports.
* To debug Metrics Portal while executing an integration test against it simply launch Metrics Portal for debug,
then attach your IDE and finally run/debug the integration test from your IDE.
* To run tests in your IDE which rely on EBean classes, you must first run `./jdk-wrapper.sh ./mvnw process-classes` on
the command line to enhance the Ebean classes.

### Debugging

(See also the list of debug flags in [the Building section](#building).)

* _Debugging Chrome-based reports._ With the default options in `portal.application.conf`, Chrome offers a remote debugger on port 48928, which you can access by visiting <chrome://inspect> in another Chrome instance and adding `localhost:48928` under "Discover network targets".

### Releasing

If you have write-access to this repository, you should just be able to cut a release by running `git checkout master && git pull && mvn release:prepare`, and accepting the default version-names it proposes.


### IntelliJ

The project can be imported normally using "File / New / Project From Existing Sources..." using the Maven aspect.
However, you will first need to mark the `target/twirl/main` directory as a generated source directory. Next, to reflect
changes to the templates within IntelliJ you will need to generate them from the command line using `./jdk-wrapper.sh ./mvnw compile`
(do so now). Finally, under "Module Settings", then under "Platform Settings" / "Global Libraries", you need to click "+", choose
"Scala SDK" and choose "Maven 2.11.12" and click "OK" and "OK" again. This should enable discovery of the generated code and
its compilation using `scalac` for use in the IDE (e.g. for running tests).

License
-------

Published under Apache Software License 2.0, see LICENSE

&copy; Groupon Inc., 2014
