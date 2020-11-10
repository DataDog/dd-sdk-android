/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.plugin

import android.content.Context
import com.datadog.android.error.internal.CrashLogFileStrategy
import com.datadog.android.log.internal.domain.LogFileStrategy
import com.datadog.android.rum.internal.domain.RumFileStrategy
import com.datadog.android.tracing.internal.domain.TracingFileStrategy

/**
 * Used to deliver the context from the SDK internals to a [DatadogPlugin] implementation.
 */
sealed class DatadogPluginConfig(
    val context: Context,
    val envName: String,
    val serviceName: String,
    val featurePersistenceDirName: String
) {

    internal class CrashReportsPluginConfig(
        context: Context,
        envName: String,
        serviceName: String
    ) :
        DatadogPluginConfig(
            context,
            envName,
            serviceName,
            CrashLogFileStrategy.AUTHORIZED_FOLDER
        )

    internal class LogsPluginConfig(context: Context, envName: String, serviceName: String) :
        DatadogPluginConfig(context, envName, serviceName, LogFileStrategy.AUTHORIZED_FOLDER)

    internal class TracingPluginConfig(context: Context, envName: String, serviceName: String) :
        DatadogPluginConfig(
            context,
            envName,
            serviceName,
            TracingFileStrategy.AUTHORIZED_FOLDER
        )

    internal class RumPluginConfig(context: Context, envName: String, serviceName: String) :
        DatadogPluginConfig(context, envName, serviceName, RumFileStrategy.AUTHORIZED_FOLDER)
}
