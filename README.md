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

#### Manual

The artifacts from the build are in *metrics-portal/target/universal/stage* and should be copied to an appropriate
directory on the Metrics Portal host(s).

#### Docker

If you use Docker, we publish a [base docker image](https://hub.docker.com/r/arpnetworking/metrics-portal/) that makes
it easy for you to layer configuration on top of.  Create a Docker image based on the image arpnetworking/metrics-portal.
Configuration files are currently embedded in the jar as resources.  You can override the configuration by importing
portal.application.conf in your configuration file and then setting the CONFIG_FILE environment variable to
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


### IntelliJ

The project can be imported normally using "File / New / Project From Existing Sources..." with the Maven aspect.
However, you will need to mark the `target/twirl` directory as a generated source directory. Further, to reflect
changes to the templates within IntelliJ you will need to generate them from the command line using `mvn compile`.

### Building

Prerequisites:
* [Docker](http://www.docker.com/) (for [Mac](https://docs.docker.com/docker-for-mac/))
* [Node](https://nodejs.org/en/download/)

Building:

    metrics-portal> ./jdk-wrapper.sh ./mvnw verify

To debug the server during run on port 8080:

    metrics-portal> ./jdk-wrapper.sh ./mvnw -Ddebug=true docker:start

To debug the server during integration tests on port 8080:

    metrics-portal> ./jdk-wrapper.sh ./mvnw -Ddebug=true verify

To execute performance tests:

    metrics-portal> ./jdk-wrapper.sh ./mvnw -PperformanceTest test

To use the local version in your project you must first install it locally:

    metrics-portal> ./jdk-wrapper.sh ./mvnw install

License
-------

Published under Apache Software License 2.0, see LICENSE

&copy; Groupon Inc., 2014
