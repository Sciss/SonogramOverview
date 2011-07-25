import sbt._

class SonogramOverviewProject( info: ProjectInfo ) extends DefaultProject( info ) {
   val scalaAudioFile   = "de.sciss" %% "scalaaudiofile" % "0.16"
   val scissDSP         = "de.sciss" % "scissdsp" % "0.11"
}