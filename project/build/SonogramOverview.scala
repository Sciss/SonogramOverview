import sbt._

class SonogramOverviewProject( info: ProjectInfo ) extends DefaultProject( info ) {
   val scalaAudioFile   = "de.sciss" %% "scalaaudiofile" % "0.14"
   val scissDSP         = "de.sciss" %% "scissdsp" % "0.10" from "http://github.com/downloads/Sciss/ScissDSP/ScissDSP-0.10.jar"
}