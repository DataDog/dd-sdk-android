package com.datadog.gradle.config

import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven

fun Project.publishingConfig(localRepo: String) {

    val projectName = name

    extensionConfig<PublishingExtension> {
        repositories {
            maven {
                setUrl(localRepo)
            }
        }

        publications {
            register("aar", MavenPublication::class.java) {
                groupId = MavenConfig.GROUP_ID
                artifactId = projectName
                version = AndroidConfig.VERSION.name
                artifact("$buildDir/outputs/aar/$projectName-release.aar")

                // publishing AAR doesn't fill the pom.xml dependencies.
                pom.withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")
                    configurations.named("implementation").get().allDependencies.forEach {
                        val dependencyNode = dependenciesNode.appendNode("dependency")
                        dependencyNode.appendNode("groupId", it.group)
                        dependencyNode.appendNode("artifactId", it.name)
                        dependencyNode.appendNode("version", it.version)
                    }
                }
            }
        }
    }

    tasks.withType(AbstractPublishToMaven::class.java) {
        this.dependsOn("bundleReleaseAar")
    }

    task("publishLocalAndRemote").apply {
        this.group = "publishing"
        this.dependsOn("publish")
        this.dependsOn("publishToMavenLocal")
    }
}
