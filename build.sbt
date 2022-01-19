import com.arpnetworking.sbt.typescript.Import.TypescriptKeys

scalaVersion := "2.12.15"

lazy val main = (project in file("."))
  .enablePlugins(SbtWeb)
  .settings(
    ivyLoggingLevel := UpdateLogging.Quiet,
    Assets / sourceDirectory := baseDirectory.value / "app/assets",
    Assets / resourceDirectory  := baseDirectory.value / "public",
    target := baseDirectory.value / "target/sbt",
    gzip / includeFilter := "*",
    TypescriptKeys.configFile := "tsconfig.json",
    pipelineStages := Seq(digest, gzip)
  )

// add development mode run hook which starts webpack file watcher (./project/webpack.scala)
PlayKeys.playRunHooks += Webpack(baseDirectory.value)
// include webpack output js files in the public assets
Assets / unmanagedResourceDirectories += target.value / "webpack"
