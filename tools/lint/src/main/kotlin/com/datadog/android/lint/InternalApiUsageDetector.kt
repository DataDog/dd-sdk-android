/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.lint

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UElement
import org.jetbrains.uast.getContainingUFile

// note that this check will skip synthetic methods (elements annotated with JvmSynthetic).
@Suppress("UndocumentedPublicClass") // used only by Lint tool
class InternalApiUsageDetector : Detector(), SourceCodeScanner {

    override fun applicableAnnotations() = listOf("com.datadog.android.lint.InternalApi")

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
        return type == AnnotationUsageType.METHOD_CALL ||
            type == AnnotationUsageType.ASSIGNMENT_RHS ||
            type == AnnotationUsageType.CLASS_REFERENCE ||
            super.isApplicableAnnotationUsage(type)
    }

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo
    ) {
        val packageName = element.getContainingUFile()?.packageName
        if (!packageName.isNullOrEmpty() && !packageName.startsWith("com.datadog")) {
            context.report(
                issue = ISSUE,
                scope = element,
                location = context.getLocation(usageInfo.usage),
                message = "Symbols annotated with `com.datadog.android.lint.InternalApi` shouldn't" +
                    " be used outside of Datadog SDK packages."
            )
        }
    }

    internal companion object {

        val ISSUE = Issue.create(
            id = "DatadogInternalApiUsage",
            briefDescription = "Prohibits usages of Datadog SDK internal API",
            explanation = "Usages of classes and methods annotated" +
                " with `com.datadog.android.lint.InternalApi` are prohibited",
            category = Category.CORRECTNESS,
            severity = Severity.ERROR,
            implementation = Implementation(
                InternalApiUsageDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
