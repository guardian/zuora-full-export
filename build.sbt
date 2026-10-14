ThisBuild / scalaVersion     := "3.9.0"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.gu"
ThisBuild / organizationName := "The Guardian"

resolvers += "Guardian GitHub Releases" at "https://guardian.github.io/maven/repo-releases"

lazy val root = (project in file("."))
  .settings(
    name := "zuora-full-export",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-xml" % "2.4.0",
      "com.codacy" %% "scalaj-http" % "2.5.0",
      "com.lihaoyi" %% "upickle" % "4.4.3",
      "com.github.pathikrit" %% "better-files" % "3.9.2",
      "com.gu" %% "spy" % "0.1.1" cross CrossVersion.for3Use2_13,
      "ch.qos.logback" % "logback-classic" % "1.5.38",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6"
    )
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
