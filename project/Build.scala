/**
 * Copyright 2014 Brandon Arp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.arpnetworking.sbt.typescript.Import.TypescriptKeys
import com.typesafe.sbt.SbtAspectj._
import com.typesafe.sbt.SbtAspectj.AspectjKeys._
import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.gzip.Import._
import com.typesafe.sbt.jse.JsEngineImport.JsEngineKeys
import com.typesafe.sbt.pgp.PgpKeys._
import com.typesafe.sbt.rjs.Import._
import com.typesafe.sbt.web.Import._
import com.typesafe.sbt.web.js.JS
import uk.co.josephearl.sbt.findbugs.FindBugsPlugin.autoImport._
import play.routes.compiler.InjectedRoutesGenerator
import play.sbt.PlayImport.PlayKeys._
import play.sbt.routes.RoutesKeys.routesGenerator
import RjsKeys._
import sbt._
import Keys._
import play.sbt.PlayImport._
import com.typesafe.sbt.packager.rpm._
import com.typesafe.sbt.packager.rpm.RpmPlugin.autoImport._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleasePlugin.autoImport.ReleaseStep
import sbtrelease.ReleaseStateTransformations._
import xerial.sbt.Sonatype.SonatypeKeys._

object ApplicationBuild extends Build {

    val appName = "metrics-portal"
    val akkaVersion = "2.4.2"
    val jacksonVersion = "2.7.4"

    val s = CheckstyleSettings.checkstyleTask ++ aspectjSettings

    val appDependencies = Seq(
      javaWs,
      "cglib" % "cglib" % "3.2.1",
      "com.arpnetworking.build" % "build-resources" % "1.0.5",
      "com.arpnetworking.commons" % "commons" % "1.6.0",
      "com.arpnetworking.logback" % "logback-steno" % "1.15.0",
      "com.arpnetworking.metrics.extras" % "jvm-extra" % "0.4.2",
      "com.arpnetworking.metrics" % "metrics-client" % "0.4.5",
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-guava" % jacksonVersion,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion,
      "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % jacksonVersion,
      "com.google.code.findbugs" % "annotations" % "3.0.1",
      "com.google.guava" % "guava" % "19.0",
      "com.google.inject" % "guice" % "4.0",
      "com.h2database" % "h2" % "1.4.192",
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      "com.typesafe.play" %% "play-ebean" % "1.0.0",
      "net.sf.oval" % "oval" % "1.82",
      "org.elasticsearch" % "elasticsearch" % "1.7.2",
      "org.flywaydb" % "flyway-play_2.11" % "2.2.1",
      "org.java-websocket" % "Java-WebSocket" % "1.3.0",
      "org.postgresql" % "postgresql" % "9.4-1206-jdbc42",
      "org.webjars" % "bean" % "1.0.14",
      "org.webjars" % "bootstrap" % "3.2.0",
      "org.webjars" % "d3js" % "3.4.8",
      "org.webjars" % "durandal" % "2.1.0",
      "org.webjars" % "flotr2" % "d43f8566e8",
      "org.webjars" % "font-awesome" % "4.3.0-2",
      "org.webjars" % "jQRangeSlider" % "5.7.0",
      "org.webjars" % "jquery" % "2.1.1",
      "org.webjars" % "jquery-ui" % "1.11.1",
      "org.webjars" % "jquery-ui-themes" % "1.11.0",
      "org.webjars" % "knockout" % "3.4.0",
      "org.webjars" % "requirejs-text" % "2.0.10-1",
      "org.webjars" % "typeaheadjs" % "0.10.4-1",
      "org.webjars" % "underscorejs" % "1.6.0-3",
      "junit" % "junit" % "4.12" % "test",
      "org.mockito" % "mockito-core" % "1.10.19" % "test",
      "org.hamcrest" % "java-hamcrest" % "2.0.0.0" % "test"
    )

    val main = Project(appName, file("."), settings = s).enablePlugins(play.sbt.PlayJava, play.ebean.sbt.PlayEbean, RpmPlugin).settings(

      organization := "com.arpnetworking.metrics",
      organizationName := "Arpnetworking Inc",
      organizationHomepage := Some(new URL("https://github.com/ArpNetworking")),

      publishMavenStyle := true,
      publishTo in publishLocal := Some(Resolver.file("file",  new File(Path.userHome.absolutePath+"/.m2/repository"))),
      publishTo <<= version { v: String =>
        val nexus = "https://oss.sonatype.org/"
        if (v.trim.endsWith("SNAPSHOT"))
          Some("snapshots" at nexus + "content/repositories/snapshots")
        else
          Some("releases" at nexus + "service/local/staging/deploy/maven2")
      },
      pomIncludeRepository := { _ => false },
      pomExtra := (
        <licenses>
          <license>
            <name>Apache License, Version 2.0</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
            <distribution>repo</distribution>
            <comments>A business-friendly OSS license</comments>
          </license>
        </licenses>
          <url>https://github.com/arpnetworking/metrics-portal</url>

          <developers>
            <developer>
              <id>alanshield</id>
              <name>Alan Shield</name>
              <email>ashield@groupon.com</email>
              <organization>Groupon</organization>
              <organizationUrl>http://www.groupon.com</organizationUrl>
              <roles>
                <role>developer</role>
              </roles>
            </developer>
            <developer>
              <id>brandonarp</id>
              <name>Brandon Arp</name>
              <email>brandonarp@gmail.com</email>
              <organization>ArpNetworking</organization>
              <organizationUrl>http://www.arpnetworking.com</organizationUrl>
              <roles>
                <role>developer</role>
              </roles>
            </developer>
            <developer>
              <id>deepikamisra</id>
              <name>Deepika Misra</name>
              <email>deepika@groupon.com</email>
              <organization>Groupon</organization>
              <organizationUrl>http://www.groupon.com</organizationUrl>
              <roles>
                <role>developer</role>
              </roles>
            </developer>
            <developer>
              <id>matthewhayter</id>
              <name>Matthew Hayter</name>
              <email>mhayter@groupon.com</email>
              <organization>Groupon</organization>
              <organizationUrl>http://www.groupon.com</organizationUrl>
              <roles>
                <role>developer</role>
              </roles>
            </developer>
            <developer>
              <id>ruchitavenugopal</id>
              <name>Ruchita Venugopal</name>
              <email>rvenugopal@groupon.com</email>
              <organization>Groupon</organization>
              <organizationUrl>http://www.groupon.com</organizationUrl>
              <roles>
                <role>developer</role>
              </roles>
            </developer>
            <developer>
              <id>ryanascheman</id>
              <name>Ryan Ascheman</name>
              <email>rascheman@groupon.com</email>
              <organization>Groupon</organization>
              <organizationUrl>http://www.groupon.com</organizationUrl>
              <roles>
                <role>developer</role>
              </roles>
            </developer>
            <developer>
              <id>tingtu</id>
              <name>Ting Tu</name>
              <email>tingtu@groupon.com</email>
              <organization>Groupon</organization>
              <organizationUrl>http://www.groupon.com</organizationUrl>
              <roles>
                <role>developer</role>
              </roles>
            </developer>
            <developer>
              <id>villekoskela</id>
              <name>Ville Koskela</name>
              <email>ville.koskela@inscopemetrics.com</email>
              <organization>Inscope Metrics</organization>
              <organizationUrl>http://www.inscopemetrics.com</organizationUrl>
              <roles>
                <role>developer</role>
              </roles>
            </developer>
          </developers>

          <scm>
            <connection>scm:git:git@github.com:ArpNetworking/metrics-portal.git</connection>
            <developerConnection>scm:git:git@github.com:ArpNetworking/metrics-portal.git</developerConnection>
            <url>https://github.com/arpnetworking/metrics-portal</url>
            <tag>HEAD</tag>
          </scm>
        ),

      releasePublishArtifactsAction := publishSigned.value,

      // Export assets artifact
      packagedArtifacts := {
        val artifacts: Map[sbt.Artifact, java.io.File] = (packagedArtifacts).value
        val assets: java.io.File = (playPackageAssets in Compile).value
        artifacts + (Artifact(moduleName.value, "jar", "jar", "assets") -> assets)
      },

      // Extract build resources
      compile in Compile <<= (compile in Compile).dependsOn(Def.task {
        val jar = (update in Compile).value
          .select(configurationFilter("compile"))
          .filter(_.name.contains("build-resources"))
          .head
        IO.unzip(jar, (target in Compile).value / "build-resources")
        Seq.empty[File]
      }),

      // Compiler warnings as errors
      javacOptions ++= Seq(
        "-Xlint:all",
        "-Werror",
        "-Xlint:-path",
        "-Xlint:-try"
      ),

      devSettings := Seq(("config.resource", "portal.application.conf")),
      javaOptions += "-Dconfig.file=conf/portal.application.conf",

      JsEngineKeys.engineType := JsEngineKeys.EngineType.Node,
      routesGenerator := InjectedRoutesGenerator,

      TypescriptKeys.moduleKind := "AMD",

      mainConfig := "start_app",
      mainModule := "start_app",
      buildProfile := JS.Object("wrapShim" -> true),
      pipelineStages := Seq(rjs, digest, gzip),
      modules += JS.Object("name" -> "classes/shell"),

      scalaVersion := "2.11.6",
      resolvers += Resolver.mavenLocal,

      libraryDependencies ++= appDependencies,

      // AspectJ
      binaries in Aspectj <++= update map { report =>
        report.matching(moduleFilter(organization = "com.arpnetworking.logback", name = "logback-steno"))
      },
      inputs in Aspectj <+= compiledClasses,
      products in Compile <<= products in Aspectj,
      products in Runtime <<= products in Compile,

      credentials += Credentials("Sonatype Nexus Repository Manager",
        "oss.sonatype.org",
        System.getenv("OSSRH_USER"),
        System.getenv("OSSRH_PASS")),

      useGpg := true,
      pgpPassphrase in Global := Option(System.getenv("GPG_PASS")).map(_.toCharArray),

      rpmVendor := "ArpNetworking",
      rpmLicense := Option("ASL 2.0"),
      rpmUrl := Option("https://github.com/ArpNetworking/metrics-portal"),
      releaseProcess := Seq[ReleaseStep](
        checkSnapshotDependencies,
        inquireVersions,
        runTest,
        setReleaseVersion,
        commitReleaseVersion,
        tagRelease,
        setNextVersion,
        commitNextVersion,
        pushChanges
      ),

      sonatypeProfileName := "com.arpnetworking",

      // Findbugs
      findbugsFailOnError := true,
      findbugsReportType := Some(FindBugsReportType.Xml),
      findbugsReportPath := Some(target.value / "findbugs" / "findbugs.html"),
      findbugsPriority := FindBugsPriority.Low,
      findbugsEffort := FindBugsEffort.Maximum,
      findbugsExcludeFilters := Some(
        <FindBugsFilter>
          <Match>
            <Class name="~views\.html\..*"/>
          </Match>
          <Match>
            <Class name="~models.ebean.*"/>
          </Match>
          <Match>
            <Class name="~router.Routes.*"/>
          </Match>
          <Match>
            <Class name="~_routes_.*"/>
          </Match>
          <Match>
            <Class name="~portal.Routes.*"/>
          </Match>
          <Match>
            <Class name="~controllers\.routes.*"/>
          </Match>
          <Match>
            <Class name="~controllers\.Reverse.*"/>
          </Match>
        </FindBugsFilter>
      )
    )
}
