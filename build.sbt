lazy val baseName  = "SonogramOverview"
lazy val baseNameL = baseName.toLowerCase

lazy val projectVersion = "2.0.0-SNAPSHOT"
lazy val mimaVersion    = "2.0.0"

lazy val deps = new {
  val main = new {
    val audioFile        = "2.0.0"
    val dsp              = "2.0.0"
    val intensityPalette = "1.0.2"
    val processor        = "0.4.3"
    val fileCache        = "1.0.0-SNAPSHOT"
    val span             = "2.0.0"
  }
  val test = new {
    val desktop          = "0.10.7"
  }
}

lazy val root = project.withId(baseNameL).in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name         := baseName,
    version      := projectVersion,
    organization := "de.sciss",
    description  := "Sonogram view component for Scala/Swing, calculating offline from audio files",
    homepage     := Some(url(s"https://git.iem.at/sciss/${name.value}")),
    licenses     := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt")),
    scalaVersion := "2.13.3",
    crossScalaVersions := Seq("2.13.3", "2.12.12"),
    mimaPreviousArtifacts := Set("de.sciss" %% baseNameL % mimaVersion),
    libraryDependencies ++= Seq(
      "de.sciss" %% "audiofile"         % deps.main.audioFile,
      "de.sciss" %% "scissdsp"          % deps.main.dsp,
      "de.sciss" %  "intensitypalette"  % deps.main.intensityPalette,
      "de.sciss" %% "processor"         % deps.main.processor,
      "de.sciss" %% "filecache-mutable" % deps.main.fileCache,
      "de.sciss" %% "span"              % deps.main.span,
      "de.sciss" %% "desktop"           % deps.test.desktop % Test
    ),
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xlint", "-Xsource:2.13"),
    scalacOptions ++= Seq("-Xelide-below", "INFO"),     // elide debug logging!
    // ---- build info ----
    buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
      BuildInfoKey.map(homepage) { case (k, opt) => k -> opt.get },
      BuildInfoKey.map(licenses) { case (_, Seq( (lic, _) )) => "license" -> lic }
    ),
    buildInfoPackage := "de.sciss.sonogram"
  )
  .settings(publishSettings)

// ---- publishing ----
lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    )
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  pomExtra := { val n = name.value
<scm>
  <url>git@git.iem.at:sciss/{n}.git</url>
  <connection>scm:git:git@git.iem.at:sciss/{n}.git</connection>
</scm>
<developers>
  <developer>
    <id>sciss</id>
    <name>Hanns Holger Rutz</name>
    <url>http://www.sciss.de</url>
  </developer>
</developers>
  }
)
