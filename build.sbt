scalaVersion := "2.11.12"

lazy val main = (project in file("."))
  .settings(
    libraryDependencies ++= Seq(),
    ivyLoggingLevel := UpdateLogging.Quiet,
    target := baseDirectory.value / "target/sbt"
  )
