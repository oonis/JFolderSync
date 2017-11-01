name := "JFolderSync"
organization := "so.macalu"
licenses := Seq(
  "Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")
)
homepage := Some(url("https://github.com/oonis/JFolderSync"))
scmInfo := Some(
  ScmInfo(
    url("https://github.com/oonis/JFolderSync"),
    "scm:git@github.com:oonis/JFolderSync.git"
  )
)
pomExtra :=
  <developers>
    <developer>
      <id>oonis</id>
      <name>Sam Macaluso</name>
      <roles>
        <role>Developer</role>
      </roles>
    </developer>
  </developers>

lazy val `JFolderSync` = (project in file("."))
  .settings(
    crossPaths := false,
    libraryDependencies ++= Seq(
      "net.java.dev.jna" % "jna" % "4.2.1",
      "com.google.guava" % "guava" % "23.0",
      "org.slf4j" % "slf4j-api" % "1.7.25",

      "org.slf4j" % "slf4j-simple" % "1.7.25" % Test,
      "com.novocode" % "junit-interface" % "0.11" % Test,
      "io.airlift" % "command" % "0.2" % Test,
      "org.codehaus.plexus" % "plexus-utils" % "3.0.22" % Test
    )
  )

import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  setNextVersion,
  commitNextVersion,
  pushChanges
)
