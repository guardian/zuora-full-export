ThisBuild / scalaVersion     := "2.13.15"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.gu"
ThisBuild / organizationName := "The Guardian"

lazy val root = (project in file("."))
  .settings(
    name := "zuora-full-export",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
      "org.scalaj" %% "scalaj-http" % "2.4.2",
      "com.lihaoyi" %% "upickle" % "4.4.0",
      "com.github.pathikrit" %% "better-files" % "3.9.2",
      "com.gu" %% "spy" % "0.1.1",
      "ch.qos.logback" % "logback-classic" % "1.5.20",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5"
    )
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
