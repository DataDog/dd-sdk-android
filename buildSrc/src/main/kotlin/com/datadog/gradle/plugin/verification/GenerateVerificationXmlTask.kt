/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.verification

import com.datadog.gradle.config.AndroidConfig
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.redundent.kotlin.xml.PrintOptions
import org.redundent.kotlin.xml.xml
import java.io.File
import java.security.MessageDigest

open class GenerateVerificationXmlTask : DefaultTask() {

    init {
        group = "datadog"
        description =
            "Generate the verification-metadata.xml for the artifact built from the module"
    }

    private val md = MessageDigest.getInstance("SHA-256")

    // region Task

    @TaskAction
    fun applyTask() {
        val buildDir = project.layout.buildDirectory.asFile.get()
        val projectDir = project.layout.projectDirectory.asFile
        val publicationReleaseDir = File(File(buildDir, "publications"), "release")
        val outputFile = File(projectDir, VerificationXmlPlugin.XML_FILE_NAME)

        val aarFile = File(File(File(buildDir, "outputs"), "aar"), "${project.name}-release.aar")
        val pomFile = File(publicationReleaseDir, "pom-default.xml")
        val moduleFile = File(publicationReleaseDir, "module.json")

        val aarSha256 = aarFile.sha256()
        val pomSha256 = pomFile.sha256()
        val moduleSha256 = moduleFile.sha256()

        val content = xml(TAG_ROOT) {
            xmlns = NS_DEPS_VERIF
            TAG_CONFIGURATION {
                TAG_VERIF_METADATA { -true.toString() }
                TAG_VERIF_SIGNATURE { -false.toString() } // TODO RUM-3104 add signature verification
            }
            TAG_COMPONENTS {
                TAG_COMPONENT {
                    attribute(ATTR_GROUP, project.group)
                    attribute(ATTR_NAME, project.name)
                    attribute(ATTR_VERSION, AndroidConfig.VERSION.name)
                    TAG_ARTIFACT {
                        attribute(ATTR_NAME, "${project.name}-${AndroidConfig.VERSION.name}.aar")
                        TAG_SHA256 {
                            attribute(ATTR_VALUE, aarSha256)
                            attribute(ATTR_ORIGIN, ORIGIN)
                        }
                    }
                    TAG_ARTIFACT {
                        attribute(ATTR_NAME, "${project.name}-${AndroidConfig.VERSION.name}.pom")
                        TAG_SHA256 {
                            attribute(ATTR_VALUE, pomSha256)
                            attribute(ATTR_ORIGIN, ORIGIN)
                        }
                    }
                    TAG_ARTIFACT {
                        attribute(ATTR_NAME, "${project.name}-${AndroidConfig.VERSION.name}.module")
                        TAG_SHA256 {
                            attribute(ATTR_VALUE, moduleSha256)
                            attribute(ATTR_ORIGIN, ORIGIN)
                        }
                    }
                }
            }
        }
        val xmlContent = content.toString(
            PrintOptions(
                indent = "   ",
                pretty = true,
                singleLineTextElements = true
            )
        )
        outputFile.writeText(XML_PREFIX + xmlContent, Charsets.UTF_8)
    }

    // endregion

    // region Internal

    fun File.sha256(): String {
        return md.digest(readBytes()).fold("", { str, byte -> str + "%02x".format(byte) })
    }

    // endregion

    companion object {

        private const val NS_DEPS_VERIF = "https://schema.gradle.org/dependency-verification"
        private const val XML_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"

        private const val TAG_ROOT = "verification-metadata"
        private const val TAG_CONFIGURATION = "configuration"
        private const val TAG_VERIF_METADATA = "verify-metadata"
        private const val TAG_VERIF_SIGNATURE = "verify-signature"
        private const val TAG_COMPONENTS = "components"
        private const val TAG_COMPONENT = "component"
        private const val TAG_SHA256 = "sha256"

        private const val TAG_ARTIFACT = "artifact"
        private const val ATTR_GROUP = "group"
        private const val ATTR_NAME = "name"
        private const val ATTR_VERSION = "version"
        private const val ATTR_VALUE = "value"
        private const val ATTR_ORIGIN = "origin"

        private const val ORIGIN = "Datadog"
    }
}
