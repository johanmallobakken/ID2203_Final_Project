name := "project20"
organization in ThisBuild := "se.kth.id2203"
version in ThisBuild := "1.2.1-SNAPSHOT"
scalaVersion in ThisBuild := "2.13.1"

// PROJECTS

lazy val global = project
  .in(file("."))
  .settings(settings)
  .aggregate(
    common,
    server,
    client
  )

lazy val common = (project in file("common"))
  .settings(
    name := "common",
    settings,
    libraryDependencies ++= commonDependencies
  )

lazy val server = (project in file("server"))
  .settings(
    name := "server",
    settings,
    assemblySettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      deps.logback,
      deps.kSim % "test"
    )
  )
  .dependsOn(
    common
  )

lazy val client = (project in file("client"))
  .settings(
    name := "client",
    settings,
    assemblySettings,
    libraryDependencies ++= commonDependencies ++ Seq(
      deps.log4j,
      deps.log4jSlf4j,
      deps.jline,
      deps.fastparse
    )
  )
  .dependsOn(
    common
  )

// DEPENDENCIES

lazy val deps =
  new {
    val logbackV        = "1.2.+"
    val scalaLoggingV   = "3.9.+"
    val scalatestV      = "3.1.0"
    val kompicsV        = "1.2.1"
    val kompicsScalaV   = "2.0.+"
    val commonUtilsV    = "2.1.0"
    val scallopV        = "3.3.0"
    val jlineV          = "3.5.1"
    val log4jV          = "1.2.+"
    val slf4jV          = "1.7.+"
    val fastparseV      = "2.1.3"
    val googleGuavaV    = "30.1-jre"

    val logback        = "ch.qos.logback"             %  "logback-classic"                 % logbackV
    val scalaLogging   = "com.typesafe.scala-logging" %% "scala-logging"                   % scalaLoggingV
    val scalatest      = "org.scalatest"              %% "scalatest"                       % scalatestV
    val kompics        = "se.sics.kompics"            %% "kompics-scala"                   % kompicsScalaV
    val kNetwork       = "se.sics.kompics.basic"      %  "kompics-port-network"            % kompicsV
    val nettyNetwork   = "se.sics.kompics.basic"      %  "kompics-component-netty-network" % kompicsV
    val kTimer         = "se.sics.kompics.basic"      %  "kompics-port-timer"              % kompicsV
    val javaTimer      = "se.sics.kompics.basic"      %  "kompics-component-java-timer"    % kompicsV
    val kSim           = "se.sics.kompics"            %% "kompics-scala-simulator"         % kompicsScalaV
    val commonUtils    = "com.larskroll"              %% "common-utils-scala"              % commonUtilsV
    val scallop        = "org.rogach"                 %% "scallop"                         % scallopV
    val jline          = "org.jline"                  %  "jline"                           % jlineV
    val log4j          = "log4j"                      %  "log4j"                           % log4jV
    val log4jSlf4j     = "org.slf4j"                  %  "slf4j-log4j12"                   % slf4jV
    val fastparse      = "com.lihaoyi"                %% "fastparse"                       % fastparseV
    val googleGuava    = "com.google.guava"           % "guava"                            % googleGuavaV
  }

lazy val commonDependencies = Seq(
  deps.scalaLogging,
  deps.kompics,
  deps.kNetwork,
  deps.nettyNetwork,
  deps.kTimer,
  deps.javaTimer,
  deps.commonUtils,
  deps.scallop,
  deps.scalatest  % "test",
  deps.googleGuava
)

// SETTINGS
lazy val compilerOptions = Seq(
  "-unchecked",
  "-feature",
  "-language:existentials",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-language:postfixOps",
  "-deprecation",
  "-encoding",
  "utf8"
)

lazy val settings = Seq(
  scalacOptions ++= compilerOptions,
  resolvers ++= Seq(
    Resolver.jcenterRepo,
    Resolver.bintrayRepo("kompics", "Maven"),
    Resolver.bintrayRepo("lkrollcom", "maven"),
    Resolver.mavenLocal
  ),
  updateOptions := updateOptions.value.withCachedResolution(false)
)

lazy val assemblySettings = Seq(
  assemblyJarName in assembly := name.value + ".jar",
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case _                             => MergeStrategy.first
  }
)
