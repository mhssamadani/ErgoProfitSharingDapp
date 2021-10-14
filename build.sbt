lazy val sonatypePublic = "Sonatype Public" at "https://oss.sonatype.org/content/groups/public/"
lazy val sonatypeReleases = "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"
lazy val sonatypeSnapshots = "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

resolvers ++= Seq(Resolver.mavenLocal, sonatypeReleases, sonatypeSnapshots, Resolver.mavenCentral)

version := "1.0.0"
val appkit = "org.ergoplatform" %% "ergo-appkit" % "develop-dd40e4e5-SNAPSHOT"

libraryDependencies ++= Seq(
  appkit, (appkit % Test).classifier("tests").classifier("tests-sources"),
  "com.squareup.okhttp3" % "mockwebserver" % "3.12.0",
  "org.scalatest" %% "scalatest" % "3.0.8" % "test",
  "org.scalacheck" %% "scalacheck" % "1.14.1" % "test",
  "com.typesafe" % "config" % "1.4.1"
)

publishMavenStyle in ThisBuild := true

publishArtifact in Test := false

