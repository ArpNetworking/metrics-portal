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
<a href="http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.arpnetworking.metrics%22%20a%3A%22metrics-portal_2.11%22">
    <img src="https://img.shields.io/maven-central/v/com.arpnetworking.metrics/metrics-portal_2.11.svg"
         alt="Maven Artifact">
</a>

Provides a web interface for managing the Inscope Metrics stack. This includes viewing telemetry (aka streaming
statistics) from one or more hosts running [Metrics Aggregator Daemon](https://github.com/ArpNetworking/metrics-aggregator-daemon).
The web interface also provides a feature for browsing hosts reporting metrics, viewing and editing alerts, scheduling
and delivering reports and performing roll-ups in KairosDb.

Setup
-----

### Installing

#### Source

First, build the project. The artifacts from the build are in `target/dist/metrics-portal-${VERSION}` where `${VERSION}`
is the current build version. To install, copy the artifact directories recursively into an appropriate target directory
on your Metrics Portal host(s). For example:

    metrics-portal> ./jdk-wrapper.sh ./mvnw package -Pno-docker
    metrics-portal> scp -r target/dist/metrics-portal-0.9.0-SNAPSHOT/* my-host.example.com:/opt/metrics-portal/

#### Tar.gz

Metrics portal releases a `tar.gz` package of its build artifacts which may be obtained from Github releases. To install,
download the archive and explode it. For example, if your Metrics Portal host(s) have Internet access you can install
directly:

    > ssh -c 'curl -f -k -L https://github.com/ArpNetworking/metrics-portal/releases/latest/<> | tar -xz -C /opt/metrics-portal/' my-host.example.com

>>> TODO: What's the release path for the tar.gz?

Otherwise, you will need to download locally and distribute it before installing. For example:

    > curl -f -k -L https://github.com/ArpNetworking/metrics-portal/releases/latest/<> -o /var/tmp/metrics-portal.tgz
    > scp /var/tmp/metrics-portal.tgz my-host.example.com:/var/tmp/
    > ssh -c 'tar -xzf /var/tmp/metrics-portal.tgz -C /opt/metrics-portal/' my-host.example.com

>>> TODO: What's the release path for the tar.gz?

#### RPM

Alternatively, each release of Metrics Portal also creates an RPM which is available on Github releases. To install,
download the RPM and install it. For example, if your Metrics Portal host(s) have Internet access you can install
directly:

    > ssh -c 'curl -f -k -L https://github.com/ArpNetworking/metrics-portal/releases/latest/<>' my-host.example.com

>>> TODO: What's the release path for the rpm?

Otherwise, you will need to download the RPM locally and distribute it before installing. For example:

    > curl -f -k -L https://github.com/ArpNetworking/metrics-portal/releases/latest/<> -o /var/tmp/metrics-portal.rpm
    > scp /var/tmp/metrics-portal.rpm my-host.example.com:/var/tmp/
    > ssh -c 'rpm -i /var/tmp/metrics-portal.rpm' my-host.example.com

>>> TODO: What's the release path for the rpm?
>>> TODO: Confirm the RPM installation command.

Finally, if your organization has its own authorized package repository you will need to work with your system
administrators to install our RPM into your package repository for installation on your Metrics Portal host(s).

#### Docker

Alternatively, if you use Docker each release of Metrics Portal also publishes a [Docker image](https://hub.docker.com/r/arpnetworking/metrics-portal/)
that you can either install directly or extend.

If you install the image directly you will likely need to mount either a local directory or data volume with your
configuration.

If you extend the image you can embed your configuration directly in your Docker image.

Regardless, you can override the provided configuration by first importing
`portal.application.conf` in your configuration file.

Next set the CONFIG_FILE environment variable to
-Dconfig.file="your_file_path".  In addition, you can specify CONFIG_FILE (defaults to
-Dconfig.resource=portal.application.conf) and PARAMS (defaults to $CONFIG_FILE) environment variables to control
startup.

### Execution

In the installation's *bin* directory there are scripts to start the Metrics Portal: *metrics-portal* (Linux/Mac) and
*metrics-portal.bat* (Windows).  One of these should be executed on system start with appropriate parameters. For example:

    /usr/local/lib/metrics_portal/bin/metrics_portal -J-Xmn150m -J-XX:+UseG1GC -Dpidfile.path=/usr/local/var/METRICS_PORTAL_PID

### Configuration

Aside from the JVM command line arguments, you may provide two additional configuration files. The first is the
[LogBack](http://logback.qos.ch/) configuration file.  To use a custom logging configuration simply add the following
argument to the command line above:

    -Dlogger.file=/usr/local/lib/metrics_portal/logger.xml

Where */usr/local/lib/metrics_portal/logger.xml* is the path to your logging configuration file. The included [default logging configuration file](conf/logger.xml) is automatically applied if one is not specified. Please refer to [LogBack](http://logback.qos.ch/) documentation for more information on how to author a configuration file.

The second configuration is for the application. To use a custom configuration simply add the following argument to the command line above:

    -Dconfig.file=/usr/local/lib/metrics_portal/application.custom.conf

Where */usr/local/lib/metrics_portal/application.custom.conf* is the path to your application configuration file.  The included [default application configuration file](conf/portal.application.conf) in the project documents and demonstrates many of the configuration options available. To use the default application configuration file it needs to be specified on start-up:

    -Dconfig.resource=conf/portal.application.conf

To author a custom application configuration it is recommended you inherit from the default application configuration file and provide any desired configuration as overrides. Please refer to [Play Framework](https://www.playframework.com/documentation/2.4.x/ProductionConfiguration) documentation for more information on how to author a configuration file.

### Extension

The Metrics Portal project intentionally uses a custom default application configuration and custom default routes specification. This allows projects extending the Metrics Portal to supplement functionality more easily with the standard default application configuration and routes. To use these files as extensions rather than replacements you should make the following changes.

First, add dependencies on the Metrics Portal code and assets in __conf/Build.scala__:

    "com.arpnetworking.metrics" %% "metrics-portal" % "VERSION",
    "com.arpnetworking.metrics" %% "metrics-portal" % "VERSION" classifier "assets",

Second, your extending project's application configuration should include the custom default configuration in __conf/application.conf__:

    include "portal.application.conf"

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

To control which verification targets (e.g. Checkstyle, Findbugs, Coverage, etc.) are run please refer to the
[parent-pom](https://github.com/ArpNetworking/arpnetworking-parent-pom) for parameters (e.g. `-DskipAllVerification=true`).


To run the server on port 8080 and its dependencies launched via Docker:

    metrics-portal> ./jdk-wrapper.sh ./mvnw docker:start

To stop the server and its dependencies run:

    metrics-portal> ./jdk-wrapper.sh ./mvnw docker:stop

To run the server on port 8080  _without_ dependencies via Play; you need to configure/provide/launch dependencies manually (see below):

    metrics-portal> ./jdk-wrapper.sh ./mvnw play2:run -Dconfig.resource=portal.application.conf

To debug on port 9002 with the server on port 8080 and its dependencies launched via Docker:

    metrics-portal> ./jdk-wrapper.sh ./mvnw -Ddebug=true docker:start

To debug on port 9002 with the server on port 8080 via Play; you need to configure/provide/launch dependencies manually (see below):

    metrics-portal> MAVEN_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=9002 ./jdk-wrapper.sh ./mvnw play2:run -Dconfig.resource=portal.application.conf

To launch dependencies only via Docker:

    metrics-portal> ./jdk-wrapper.sh ./mvnw docker:start -DdockerDependenciesOnly=true

To execute performance tests:

    metrics-portal> ./jdk-wrapper.sh ./mvnw -PperformanceTest test

To use the local version as a dependency in your project you must first install it locally:

    metrics-portal> ./jdk-wrapper.sh ./mvnw install

### Testing

* Unit tests (`test/java/**/*Test.java`) may be run or debugged directly from your IDE.
* Integration tests may be run or debugged directly from your IDE provided an instance of Metrics Portal and its
dependencies are running locally on the default ports.
* To debug Metrics Portal while executing an integration test against it simply launch Metrics Portal for debug,
then attach your IDE and finally run the integration test from your IDE.

### IntelliJ

The project can be imported normally using "File / New / Project From Existing Sources..." with the Maven aspect.
However, you will need to mark the `target/twirl` directory as a generated source directory. Further, to reflect
changes to the templates within IntelliJ you will need to generate them from the command line using `./jdk-wrapper.sh ./mvnw compile`.

License
-------

Published under Apache Software License 2.0, see LICENSE

&copy; Groupon Inc., 2014
