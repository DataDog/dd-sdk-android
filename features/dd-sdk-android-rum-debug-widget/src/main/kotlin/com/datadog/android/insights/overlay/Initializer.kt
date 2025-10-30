/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.insights.overlay

import android.app.Application
import android.content.Context
import androidx.startup.Initializer

/**
 * An [Initializer] to automatically install the [AutoStarter] when
 * using androidx.startup.
 */
class Initializer : Initializer<Unit> {
    override fun create(context: Context) {
        AutoStarter.install(context.applicationContext as Application)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
