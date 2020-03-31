/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.extension

import com.datadog.android.BuildConfig
import com.datadog.tools.unit.setStaticValue
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext

class EnableLogcatExtension :
    BeforeTestExecutionCallback,
    AfterTestExecutionCallback {

    // region BeforeTestExecutionCallback

    override fun beforeTestExecution(context: ExtensionContext) {
        val method = context.requiredTestMethod
        val enableLogcat = method.getAnnotation(EnableLogcat::class.java)

        val logCatEnabled = enableLogcat?.isEnabled ?: DEFAULT_LOGCAT
        setLogcatEnabled(logCatEnabled)
    }

    // endregion

    // region AfterTestExecutionCallback

    override fun afterTestExecution(context: ExtensionContext?) {
        setLogcatEnabled(DEFAULT_LOGCAT)
    }

    // endregion

    private fun setLogcatEnabled(logCatEnabled: Boolean) {
        BuildConfig::class.java.setStaticValue("LOGCAT_ENABLED", logCatEnabled)
    }

    companion object {
        private val DEFAULT_LOGCAT = BuildConfig.LOGCAT_ENABLED
    }
}
