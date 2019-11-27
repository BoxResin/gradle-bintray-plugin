package com.minyushov.bintray.project

import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.minyushov.bintray.BintrayPlugin
import com.minyushov.bintray.BintrayExtension
import com.minyushov.bintray.artifact.ArtifactProvider
import com.minyushov.bintray.artifact.ComponentProvider
import com.minyushov.bintray.tasks.BintrayUploadTask
import org.gradle.api.Project
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin

internal abstract class BintrayProject(
  protected val project: Project,
  protected val extension: BintrayExtension
) {

  protected val componentProvider = ComponentProvider(project, extension)
  protected val artifactProvider = ArtifactProvider(project, extension)

  protected abstract val components: List<SoftwareComponent>
  protected abstract val artifacts: List<PublishArtifact>

  fun configure() {
    applyPlugins()

    project.afterEvaluate {
      project.extensions.configure(PublishingExtension::class.java) { publishing ->
        val publication = publishing.publications.maybeCreate("maven", MavenPublication::class.java)

        val version = checkNotNull(extension.version.orNull) { "Bintray library version is not defined in '${BintrayPlugin.EXTENSION_NAME}' extension" }
        project.setProperty("version", version)
        publication.version = version

        val group = checkNotNull(extension.groupId.orNull) { "Bintray library groupId is not defined in '${BintrayPlugin.EXTENSION_NAME}' extension" }
        project.setProperty("group", group)
        publication.groupId = group

        val artifactId = checkNotNull(extension.artifactId.orNull) { "Bintray library artifactId is not defined in '${BintrayPlugin.EXTENSION_NAME}' extension" }
        project.setProperty("archivesBaseName", artifactId)
        publication.artifactId = artifactId

        components.forEach { publication.from(it) }
        artifacts.forEach { publication.artifact(it) }
      }

      project.tasks.register("bintrayUpload", BintrayUploadTask::class.java) { task ->
        task.user.set(extension.user)
        task.organization.set(extension.organization)
        task.key.set(extension.key)
        task.repository.set(extension.repo)
        task.packageName.set(extension.pkgName)
        task.artifactId.set(extension.artifactId)
        task.license.set(extension.license)
        task.vcsUrl.set(extension.vcsUrl)
        task.version.set(extension.version)
        task.dryRun.set(extension.dryRun)
      }

      project.extensions.configure(PublishingExtension::class.java) { publishing ->
        val uploadDevTask = project.tasks.named("bintrayUpload")
        publishing.publications.forEach { publication ->
          uploadDevTask.dependsOn("publish${publication.name.capitalize()}PublicationToMavenLocal")
        }
      }
    }
  }

  protected open fun applyPlugins() {
    project.pluginManager.apply {
      apply(MavenPublishPlugin::class.java)
    }
  }

}

internal fun Project.createBintrayProject(extension: BintrayExtension): BintrayProject? =
  pluginManager.let { manager ->
    when {
      manager.hasPlugin("com.android.library") && manager.hasPlugin("kotlin-android") -> AndroidKotlinLibraryProject(this, extension)
      manager.hasPlugin("com.android.library") -> AndroidJavaLibraryProject(this, extension)
      manager.hasPlugin("kotlin") && !manager.hasPlugin("com.android.application") -> KotlinLibraryProject(this, extension)
      manager.hasPlugin("java-library") || manager.hasPlugin("java") || manager.hasPlugin("groovy") -> JavaLibraryProject(this, extension)
      else -> null
    }
  }