sourceDirectory := file("dummy source directory")

val scalaMajorVersion = SettingKey[Int]("scalaMajorVersion")

scalaVersionSettings

lazy val versionNumber = "1.14.1"

lazy val isRelease = false

lazy val travisCommit = Option(System.getenv().get("TRAVIS_COMMIT"))

lazy val scalaVersionSettings = Seq(
  scalaVersion := "2.13.0",
  crossScalaVersions := Seq("2.10.7", "2.11.12", "2.12.9", scalaVersion.value),
  scalaMajorVersion := {
    val v = scalaVersion.value
    CrossVersion.partialVersion(v).map(_._2.toInt).getOrElse {
      throw new RuntimeException(s"could not get Scala major version from $v")
    }
  }
)

lazy val sharedSettings = MimaSettings.settings ++ scalaVersionSettings ++ Seq(

  name := "scalacheck",

  version := {
    val suffix =
      if (isRelease) ""
      else travisCommit.map("-" + _.take(7)).getOrElse("") + "-SNAPSHOT"
    versionNumber + suffix
  },

  isSnapshot := !isRelease,

  organization := "org.scalacheck",

  licenses := Seq("BSD 3-clause" -> url("https://opensource.org/licenses/BSD-3-Clause")),

  homepage := Some(url("http://www.scalacheck.org")),

  credentials ++= (for {
    username <- Option(System.getenv().get("SONATYPE_USERNAME"))
    password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
  } yield Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    username, password
  )).toSeq,

  unmanagedSourceDirectories in Compile += (baseDirectory in LocalRootProject).value / "src" / "main" / "scala",

  unmanagedSourceDirectories in Compile += {
    val s = if (scalaMajorVersion.value >= 13) "+" else "-"
    (baseDirectory in LocalRootProject).value / "src" / "main" / s"scala-2.13$s"
  },

  unmanagedSourceDirectories in Test += (baseDirectory in LocalRootProject).value / "src" / "test" / "scala",

  resolvers += "sonatype" at "https://oss.sonatype.org/content/repositories/releases",

  javacOptions += "-Xmx1024M",

  // 2.10 - 2.13
  scalacOptions ++= {
    def mk(r: Range)(strs: String*): Int => Seq[String] =
      (n: Int) => if (r.contains(n)) strs else Seq.empty

    val groups: Seq[Int => Seq[String]] = Seq(
      mk(10 to 11)("-Xlint"),
      mk(10 to 12)("-Ywarn-inaccessible", "-Ywarn-nullary-override",
        "-Ywarn-nullary-unit", "-Xfuture", "-Xfatal-warnings", "-deprecation"),
      mk(10 to 13)("-encoding", "UTF-8", "-feature", "-unchecked",
        "-Ywarn-dead-code", "-Ywarn-numeric-widen"),
      mk(11 to 12)("-Ywarn-infer-any", "-Ywarn-unused-import"),
      mk(12 to 13)("-Xlint:-unused",
        "-Ywarn-unused:-patvars,-implicits,-locals,-privates,-explicits"))

    val n = scalaMajorVersion.value
    groups.flatMap(f => f(n))
  },

  // HACK: without these lines, the console is basically unusable,
  // since all imports are reported as being unused (and then become
  // fatal errors).
  scalacOptions in (Compile, console) ~= {_.filterNot("-Ywarn-unused-import" == _)},
  scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value,

  // don't use fatal warnings in tests
  scalacOptions in Test ~= (_ filterNot (_ == "-Xfatal-warnings")),

  mimaPreviousArtifacts := {
    val isScalaJSMilestone: Boolean =
      Option(System.getenv("SCALAJS_VERSION")).filter(_.startsWith("1.0.0-M")).isDefined
    // TODO: re-enable MiMa for 2.14 once there is a final version
    if (scalaMajorVersion.value == 14 || isScalaJSMilestone) Set()
    else Set("org.scalacheck" %%% "scalacheck" % "1.14.0")
  },

  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    val (name, path) = if (isSnapshot.value) ("snapshots", "content/repositories/snapshots")
                       else ("releases", "service/local/staging/deploy/maven2")
    Some(name at nexus + path)
  },

  publishMavenStyle := true,

  // Travis should only publish snapshots
  publishArtifact := !(isRelease && travisCommit.isDefined),

  publishArtifact in Test := false,

  pomIncludeRepository := { _ => false },

  pomExtra := {
    <scm>
      <url>https://github.com/typelevel/scalacheck</url>
      <connection>scm:git:git@github.com:typelevel/scalacheck.git</connection>
    </scm>
    <developers>
      <developer>
        <id>rickynils</id>
        <name>Rickard Nilsson</name>
      </developer>
    </developers>
  }
)

lazy val js = project.in(file("js"))
  .settings(sharedSettings: _*)
  .settings(
    scalaJSStage in Global := FastOptStage,
    libraryDependencies += "org.scala-js" %% "scalajs-test-interface" % scalaJSVersion
  )
  .enablePlugins(ScalaJSPlugin)

lazy val jvm = project.in(file("jvm"))
  .settings(sharedSettings: _*)
  .settings(
    fork in Test := true,
    libraryDependencies += "org.scala-sbt" %  "test-interface" % "1.0"
  )
  .settings(dottySettings)

lazy val native = project.in(file("native"))
  .settings(sharedSettings: _*)
  .settings(
    doc in Compile := (doc in Compile in jvm).value,
    scalaVersion := "2.11.12",
    // TODO: re-enable MiMa for native once published
    mimaPreviousArtifacts := Set(),
    libraryDependencies ++= Seq(
      "org.scala-native" %% "test-interface_native0.3" % nativeVersion
    )
  )
  .enablePlugins(ScalaNativePlugin)

lazy val dottySettings = List(
  libraryDependencies := libraryDependencies.value.map(_.withDottyCompat(scalaVersion.value)),
  scalacOptions := List("-language:Scala2,implicitConversions", "-Yexplicit-nulls", "-Yjava-interop-dont-nullify-outermost")
)
