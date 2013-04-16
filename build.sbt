name := "SonogramOverview"

version := "1.5.0-SNAPSHOT"

organization := "de.sciss"

description := "Sonogram view component for Scala/Swing, calculating offline from audio files"

homepage <<= name { n => Some(url("https://github.com/Sciss/" + n)) }

licenses := Seq("GPL v2+" -> url("http://www.gnu.org/licenses/gpl-2.0.txt"))

scalaVersion := "2.10.1"

libraryDependencies ++= Seq(
  "de.sciss" %% "scalaaudiofile"   % "1.4.+",
  "de.sciss" %% "scissdsp"         % "1.2.+",
  "de.sciss" %  "intensitypalette" % "1.0.0",
  "de.sciss" %% "processor"        % "0.1.+",
  "de.sciss" %% "filecache"        % "0.2.+",
  "de.sciss" %% "model"            % "0.2.2+",
  "de.sciss" %% "span"             % "1.2.+",
  "de.sciss" %% "desktop"          % "0.2.+" % "test"
)

retrieveManaged := true

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature")

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

publishTo <<= version { (v: String) =>
   Some(if (v endsWith "-SNAPSHOT")
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
   else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
   )
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

pomExtra <<= name { n =>
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

(LsKeys.tags in LsKeys.lsync) := Seq("swing", "audio", "spectrum", "dsp", "sonogram")

(LsKeys.ghUser in LsKeys.lsync) := Some("Sciss")

(LsKeys.ghRepo in LsKeys.lsync) <<= name(Some(_))
