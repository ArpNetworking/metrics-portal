scalaVersion := "2.12.15"

lazy val main = (project in file("."))
  .settings(
    libraryDependencies ++= Seq(),
    ivyLoggingLevel := UpdateLogging.Quiet,
    target := baseDirectory.value / "target/sbt"
  )
