/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.utils.extension

import com.datadog.android.BuildConfig
import com.datadog.android.utils.setStaticValue
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext

class BuildConfigExtension :
        BeforeTestExecutionCallback {

    override fun beforeTestExecution(context: ExtensionContext) {
        val method = context.requiredTestMethod
        val enableLogcat = method.getAnnotation(EnableLogcat::class.java)

        val logCatEnabled = enableLogcat?.isEnabled ?: DEFAULT_LOGCAT
        setLogcatEnabled(logCatEnabled)
    }

    private fun setLogcatEnabled(logCatEnabled: Boolean) {
        BuildConfig::class.java.setStaticValue("LOGCAT_ENABLED", logCatEnabled)
    }

    companion object {
        val DEFAULT_LOGCAT = BuildConfig.LOGCAT_ENABLED
    }
}
