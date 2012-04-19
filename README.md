## SonogramOverview

### statement

An offline sonogram swing component for Scala or Java. (C)opyright 2010&ndash;2012 by Hanns Holger Rutz. All rights reserved. It is released under the [GNU General Public License](http://github.com/Sciss/SonogramOverview/blob/master/licenses/SonogramOverview-License.txt) and comes with absolutely no warranties. To contact the author, send an email to `contact at sciss.de`. SonogramOverview is based on the `AudioFile` class of [ScalaAudioFile](http://github.com/Sciss/ScalaAudioFile) and the Constant-Q spectral transform of [ScissDSP](http://github.com/Sciss/ScissDSP).

### requirements / installation

Compiles against Scala 2.9.2 and Java 1.6. Builds with xsbt (sbt 0.11). A demo can be launched via `sbt run`.

To use this library in your project:

    "de.sciss" %% "sonogramoverview" % "0.17"

### creating an IntelliJ IDEA project

To develop the library under IntelliJ IDEA, you can set up a project with the sbt-idea plugin. If you haven't globally installed the sbt-idea plugin yet, create the following contents in `~/.sbt/plugins/build.sbt`:

    resolvers += "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"

    addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.0.0")

Then to create the IDEA project, run the following two commands from the xsbt shell:

    > set ideaProjectName := "SonogramOverview"
    > gen-idea

### download

The current version can be downloaded from [github.com/Sciss/SonogramOverview](http://github.com/Sciss/SonogramOverview).