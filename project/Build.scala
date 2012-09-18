import sbt._
import Keys._
import akka.sbt.AkkaKernelPlugin
import akka.sbt.AkkaKernelPlugin.{ Dist, outputDirectory, distJvmOptions }

object HootBombRestKernelBuild extends Build {
	val Organization = "ca.figmintgames"
	val Version = "0.1"
	val ScalaVersion = "2.9.1"

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
		organizationName := "Figmint Games",
		organizationHomepage := Some(url("http://www.figmint.ca")))

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
		akkaKernel,
		akkaSlf4j,
		netty)
}

object Dependency {
	val akkaKernel = "com.typesafe.akka" % "akka-kernel" % "2.0.3"
	val akkaSlf4j = "com.typesafe.akka" % "akka-slf4j" % "2.0.3"
	val netty = "io.netty" % "netty" % "3.5.4.Final"
}

