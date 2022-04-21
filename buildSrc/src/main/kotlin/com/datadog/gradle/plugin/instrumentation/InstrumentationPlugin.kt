/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.gradle.AppExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute

class InstrumentationPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.withPlugin("com.android.application") {
            val androidExtension = project.extensions.getByType(AppExtension::class.java)
            val androidComponentsExtension =
                project.extensions.getByType(AndroidComponentsExtension::class.java)
            androidComponentsExtension.onVariants { variant ->
                System.out.println("===== Instrumentation Applied =====")
                val metaInfStripped: Attribute<Boolean> =
                    Attribute.of("meta-inf-stripped", Boolean::class.javaObjectType)
                variant.transformClassesWith(
                    DatadogClassVisitorFactory::class.java,
                    InstrumentationScope.ALL
                ) {
                    it.invalidate.set(System.currentTimeMillis())
                    it.invalidate.disallowChanges()
                }
                variant.setAsmFramesComputationMode(
                    FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
                )

                if (AgpVersions.CURRENT < AgpVersions.VERSION_7_1_2
                ) {
                    project.configurations.named("${variant.name}RuntimeClasspath")
                        .configure {
                            attributes.attribute(metaInfStripped, true)
                        }
                    MetaInfStripTransform.register(
                        project.dependencies,
                        true
                    )
                }
            }

        }
    }
}