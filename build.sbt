import com.arpnetworking.sbt.typescript.Import.TypescriptKeys

scalaVersion := "2.11.12"

lazy val main = (project in file("."))
  .enablePlugins(SbtWeb)
  .settings(
    libraryDependencies ++= Seq(
      "org.webjars" % "bean" % "1.0.14",
      "org.webjars" % "bootstrap" % "3.3.7",
      "org.webjars" % "durandal" % "2.2.0",
      "org.webjars" % "Eonasdan-bootstrap-datetimepicker" % "4.17.47",
      "org.webjars" % "flotr2" % "d43f8566e8",
      "org.webjars" % "font-awesome" % "4.3.0-2",
      "org.webjars" % "jQRangeSlider" % "5.7.0",
      "org.webjars" % "jquery" % "3.2.1",
      "org.webjars" % "jquery-ui" % "1.12.1",
      "org.webjars" % "jquery-ui-themes" % "1.12.1",
      "org.webjars" % "knockout" % "3.4.0",
      "org.webjars" % "requirejs-text" % "2.0.10-1",
      "org.webjars" % "typeaheadjs" % "0.10.4-1",
      "org.webjars" % "underscorejs" % "1.8.3",
      "org.webjars.npm" % "d3" % "4.11.0",
      "org.webjars.npm" % "github-com-auth0-jwt-decode" % "2.1.0",
      "org.webjars.npm" % "graceful-readlink" % "1.0.1",
      "org.webjars.npm" % "iconv-lite" % "0.4.24",
      "org.webjars.npm" % "moment" % "2.24.0",
      "org.webjars.npm" % "moment-timezone" % "0.5.23"
    ),
    ivyLoggingLevel := UpdateLogging.Quiet,
    sourceDirectory in Assets := baseDirectory.value / "app/assets",
    resourceDirectory in Assets := baseDirectory.value / "public",
    target := baseDirectory.value / "target/sbt",
    includeFilter in gzip := "*",
    TypescriptKeys.configFile := "tsconfig.json",
    pipelineStages := Seq(digest, gzip)
  )
