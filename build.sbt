ThisBuild / scalaVersion := "2.13.7"

val http4sVersion     = "0.23.7"
val pureConfigVersion = "0.17.1"

libraryDependencies ++= Seq(
  "io.estatico"           %% "newtype"                % "0.4.4",
  "org.typelevel"         %% "cats-effect"            % "3.1.1",
  "org.http4s"            %% "http4s-dsl"             % http4sVersion,
  "org.http4s"            %% "http4s-ember-server"    % http4sVersion,
  "org.http4s"            %% "http4s-ember-client"    % http4sVersion,
  "org.http4s"            %% "http4s-circe"           % http4sVersion,
  "io.circe"              %% "circe-generic"          % "0.14.1",
  "org.tpolecat"          %% "skunk-core"             % "0.2.0",
  "org.slf4j"             % "slf4j-simple"            % "1.7.32",
  "com.github.pureconfig" %% "pureconfig"             % pureConfigVersion,
  "com.github.pureconfig" %% "pureconfig-cats-effect" % pureConfigVersion
)

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-feature",     // Emit warning and location for usages of features that should be imported explicitly.
  "-language:implicitConversions",
  "-Ymacro-annotations"
)
