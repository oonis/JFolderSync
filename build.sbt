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
      "org.zeromq" % "jeromq" % "0.4.2"
    )
  )

publishMavenStyle := true
publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)

import ReleaseTransformations._
releasePublishArtifactsAction := PgpKeys.publishSigned.value
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)
