lazy val baseName  = "SonogramOverview"
lazy val baseNameL = baseName.toLowerCase

lazy val projectVersion = "2.2.1"
lazy val mimaVersion    = "2.2.0"

lazy val deps = new {
  val main = new {
    val audioFile        = "2.3.2"
    val dsp              = "2.2.1"
    val intensityPalette = "1.0.2"
    val processor        = "0.5.0"
    val fileCache        = "1.1.1"
    val span             = "2.0.0"
  }
  val test = new {
    val desktop          = "0.11.3"
  }
}

// sonatype plugin requires that these are in global
ThisBuild / version      := projectVersion
ThisBuild / organization := "de.sciss"

lazy val root = project.withId(baseNameL).in(file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name         := baseName,
//    version      := projectVersion,
//    organization := "de.sciss",
    description  := "Sonogram view component for Scala/Swing, calculating offline from audio files",
    homepage     := Some(url(s"https://git.iem.at/sciss/${name.value}")),
    licenses     := Seq("AGPL v3+" -> url("http://www.gnu.org/licenses/agpl-3.0.txt")),
    scalaVersion := "2.13.4",
    crossScalaVersions := Seq("3.0.0-M2", "2.13.4", "2.12.12"),
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
    scalacOptions ++= { if (isDotty.value) Nil else Seq("-Xelide-below", "INFO")},     // elide debug logging!
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
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  developers := List(
    Developer(
      id    = "sciss",
      name  = "Hanns Holger Rutz",
      email = "contact@sciss.de",
      url   = url("https://www.sciss.de")
    )
  ),
  scmInfo := {
    val h = "git.iem.at"
    val a = s"sciss/${name.value}"
    Some(ScmInfo(url(s"https://$h/$a"), s"scm:git@$h:$a.git"))
  },
)

