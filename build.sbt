lazy val baseName  = "SonogramOverview"
lazy val baseNameL = baseName.toLowerCase

lazy val projectVersion = "1.10.0"
lazy val mimaVersion    = "1.10.0"

lazy val deps = new {
  val main = new {
    val audioFile        = "1.5.0"
    val dsp              = "1.3.0"
    val intensityPalette = "1.0.0"
    val processor        = "0.4.1"
    val fileCache        = "0.4.0"
    val span             = "1.4.0"
  }
  val test = new {
    val desktop          = "0.9.0"
  }
}

lazy val root = project.withId(baseNameL).in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name         := baseName,
    version      := projectVersion,
    organization := "de.sciss",
    description  := "Sonogram view component for Scala/Swing, calculating offline from audio files",
    homepage     := Some(url(s"https://github.com/Sciss/${name.value}")),
    licenses     := Seq("LGPL v2.1+" -> url("http://www.gnu.org/licenses/lgpl-2.1.txt")),
    scalaVersion := "2.12.5",
    crossScalaVersions := Seq("2.12.5", "2.11.12"),
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
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-Xfuture", "-encoding", "utf8", "-Xlint"),
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
)
