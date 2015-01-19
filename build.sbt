name         := "SonogramOverview"

version      := "1.9.0"

organization := "de.sciss"

description  := "Sonogram view component for Scala/Swing, calculating offline from audio files"

homepage     := Some(url("https://github.com/Sciss/" + name.value))

licenses     := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt"))

scalaVersion := "2.11.5"

crossScalaVersions := Seq("2.11.5", "2.10.4")

libraryDependencies ++= Seq(
  "de.sciss" %% "scalaaudiofile"    % "1.4.4",
  "de.sciss" %% "scissdsp"          % "1.2.1",
  "de.sciss" %  "intensitypalette"  % "1.0.0",
  "de.sciss" %% "processor"         % "0.4.0",
  "de.sciss" %% "filecache-mutable" % "0.3.2",
  "de.sciss" %% "span"              % "1.3.0",
  "de.sciss" %% "desktop"           % "0.6.0" % "test"
)

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Xfuture", "-encoding", "utf8")

scalacOptions ++= Seq("-Xelide-below", "INFO")     // elide debug logging!

// ---- build info ----

buildInfoSettings

sourceGenerators in Compile <+= buildInfo

buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
  BuildInfoKey.map(homepage) { case (k, opt)           => k -> opt.get },
  BuildInfoKey.map(licenses) { case (_, Seq((lic, _))) => "license" -> lic }
)

buildInfoPackage := "de.sciss.sonogram"

// ---- publishing ----

publishMavenStyle := true

publishTo :=
  Some(if (isSnapshot.value)
    "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  else
    "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  )

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra := { val n = name.value
<scm>
  <url>git@github.com:Sciss/{n}.git</url>
  <connection>scm:git:git@github.com:Sciss/{n}.git</connection>
</scm>
<developers>
  <developer>
    <id>sciss</id>
    <name>Hanns Holger Rutz</name>
    <url>http://www.sciss.de</url>
  </developer>
</developers>
}

// ---- ls.implicit.ly ----

seq(lsSettings :_*)

(LsKeys.tags   in LsKeys.lsync) := Seq("swing", "audio", "spectrum", "dsp", "sonogram")

(LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")

(LsKeys.ghRepo in LsKeys.lsync) := Some(name.value)
