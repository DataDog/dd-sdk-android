/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.extensions

import android.os.Build
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.setStaticValue
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * A JUnit5 [Extension] that will override the [Build.VERSION.SDK_INT] value.
 *
 * You can control that by annotating your test method with @[TestTargetApi].
 */
class ApiLevelExtension :
    BeforeTestExecutionCallback,
    AfterTestExecutionCallback {

    private var originalApiLevel: Int = 0

    // region BeforeTestExecutionCallback

    /** @inheritdoc */
    override fun beforeTestExecution(context: ExtensionContext) {
        val method = context.requiredTestMethod
        val targetApi = method.getAnnotation(TestTargetApi::class.java)

        if (targetApi != null) {
            originalApiLevel = Build.VERSION.SDK_INT
            val apiLevel = targetApi.value
            setApiLevel(apiLevel)
        }
    }

    // endregion

    // region AfterTestExecutionCallback

    /** @inheritdoc */
    override fun afterTestExecution(context: ExtensionContext?) {
        setApiLevel(originalApiLevel)
    }

    // endregion

    // region Internal

    private fun setApiLevel(apiLevel: Int) {
        Build.VERSION::class.java.setStaticValue("SDK_INT", apiLevel)
    }

    // endregion
}
