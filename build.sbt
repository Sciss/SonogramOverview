name := "SonogramOverview"

version := "0.17"

organization := "de.sciss"

description := "Sonogram view component for Scala/Swing, calculating offline from audio files"

homepage := Some( url( "https://github.com/Sciss/SonogramOverview" ))

licenses := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))

scalaVersion := "2.9.1"

libraryDependencies ++= Seq(
   "de.sciss" %% "scalaaudiofile" % "0.20",
   "de.sciss" % "scissdsp" % "0.11" from "http://scala-tools.org/repo-releases/de/sciss/scissdsp/0.11/scissdsp-0.11.jar"
)

retrieveManaged := true

scalacOptions ++= Seq( "-deprecation", "-unchecked" )

// ---- publishing ----

publishTo <<= version { (v: String) =>
   Some( "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/".+(
      if( v.endsWith( "-SNAPSHOT")) "snapshots/" else "releases/"
   ))
}

pomExtra :=
<licenses>
  <license>
    <name>GPL v2+</name>
    <url>http://www.gnu.org/licenses/gpl-2.0.txt</url>
    <distribution>repo</distribution>
  </license>
</licenses>

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")


// ---- ls.implicit.ly ----

seq( lsSettings :_* )

(LsKeys.tags in LsKeys.lsync) := Seq( "swing", "audio", "spectrum", "dsp", "sonogram" )

(LsKeys.ghUser in LsKeys.lsync) := Some( "Sciss" )

(LsKeys.ghRepo in LsKeys.lsync) := Some( "SonogramOverview" )

// bug in ls -- doesn't find the licenses from global scope
(licenses in LsKeys.lsync) := Seq( "GPL v2+" -> url( "http://www.gnu.org/licenses/gpl-2.0.txt" ))
