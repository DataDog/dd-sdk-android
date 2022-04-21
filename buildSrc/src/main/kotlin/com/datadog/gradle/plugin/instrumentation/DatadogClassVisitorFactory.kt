/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.instrumentation

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.objectweb.asm.ClassVisitor

abstract class DatadogClassVisitorFactory : AsmClassVisitorFactory<DatadogClassVisitorFactory.Parameters> {
    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return DatadogClassVisitor(instrumentationContext.apiVersion.get(), nextClassVisitor)
    }

    interface Parameters : InstrumentationParameters {

        @get:Input
        @get:Optional
        val invalidate: Property<Long>

    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return classData.className == "androidx.compose.ui.platform.AndroidComposeView"
    }
}