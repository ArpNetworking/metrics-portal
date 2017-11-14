// Copyright 2014 Brandon Arp
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Comment to get more information during initialization
logLevel := Level.Info

// The Typesafe repository
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

// The SBT Community repository
resolvers += "SBT Community repository" at "http://dl.bintray.com/sbt/sbt-plugin-releases/"

addSbtPlugin("uk.co.josephearl" % "sbt-findbugs" % "2.4.3")

addSbtPlugin("com.lightbend.sbt" % "sbt-aspectj" % "0.11.0")

addSbtPlugin("com.typesafe.sbt" %% "sbt-digest" % "1.1.3")

addSbtPlugin("com.typesafe.sbt" %% "sbt-gzip" % "1.0.2")

addSbtPlugin("com.typesafe.sbt" % "sbt-play-ebean" % "4.0.4")

addSbtPlugin("com.typesafe.sbt" % "sbt-play-enhancer" % "1.1.0")

addSbtPlugin("com.typesafe.play" %% "sbt-plugin" % "2.6.6")

addSbtPlugin("com.typesafe.sbt" %% "sbt-rjs" % "1.0.10")

addSbtPlugin("com.arpnetworking" %% "sbt-typescript" % "0.3.5")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.6")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "1.1")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

addSbtPlugin("com.simplytyped" % "sbt-antlr4" % "0.7.11")

libraryDependencies ++= Seq(
    "com.puppycrawl.tools" % "checkstyle" % "8.4"
)
