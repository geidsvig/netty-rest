import sbt._
import Keys._
import akka.sbt.AkkaKernelPlugin
import akka.sbt.AkkaKernelPlugin.{ Dist, outputDirectory, distJvmOptions }

object RestKernelBuild extends Build {
  val Organization = "geidsvig"
  val Version = "1.0"
  val ScalaVersion = "2.10.1"

  val appDependencies = Dependencies.restKernel
  libraryDependencies ++= Dependencies.restKernel

  lazy val NettyRestLib = Project(
    id = "netty-rest-lib",
    base = file("."),
    settings = defaultSettings ++ AkkaKernelPlugin.distSettings ++ Seq(
      libraryDependencies ++= Dependencies.restKernel))

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := Organization,
    version := Version,
    scalaVersion := ScalaVersion,
    crossPaths := false,
    organizationName := "geidsvig",
    organizationHomepage := Some(url("https://github.com/geidsvig")))

  lazy val defaultSettings = buildSettings ++ Seq(
    resolvers ++= Seq("repo.novus snaps" at "http://repo.novus.com/snapshots/",
      "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"),

    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
    javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation"),
    libraryDependencies ++= Dependencies.restKernel)
}

object Dependencies {
  import Dependency._

  val restKernel = Seq(
    akkaKernel, akkaSlf4j, akkaTest, netty, scalaTest)
}

object Dependency {
  val akkaVersion = "2.1.2"
  val akkaKernel    = "com.typesafe.akka"     % "akka-kernel_2.10"  % akkaVersion
  val akkaSlf4j     = "com.typesafe.akka"     % "akka-slf4j_2.10"   % akkaVersion
  val akkaTest      = "com.typesafe.akka"     % "akka-testkit_2.10" % akkaVersion % "test"
  val netty         = "io.netty"              % "netty"             % "3.6.5.Final"
  val scalaTest     = "org.scalatest"         % "scalatest_2.10"    % "1.9.1" % "test"
}

