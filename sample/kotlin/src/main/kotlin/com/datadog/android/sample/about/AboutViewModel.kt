/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.about

import android.content.Context
import androidx.lifecycle.ViewModel
import com.datadog.android.rum.resource.getAssetAsRumResource
import com.datadog.android.rum.resource.getRawResAsRumResource
import com.datadog.android.sample.R
import com.datadog.android.sample.sampleTaskDispatcher
import com.datadog.android.sample.sampleTaskScope
import com.datadog.android.trace.withinSpan
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

internal class AboutViewModel : ViewModel() {

    private val taskScope = sampleTaskScope()

    fun getAboutText(context: Context, onDone: (String) -> Unit = {}) {
        taskScope.launch {
            val text = runInterruptible(sampleTaskDispatcher) {
                withinSpan("LoadResource") {
                    context.getRawResAsRumResource(R.raw.about).bufferedReader().use { it.readText() }
                }
            }
            onDone(text)
        }
    }

    fun getLicenseText(context: Context, onDone: (String) -> Unit = {}) {
        taskScope.launch {
            val text = runInterruptible(sampleTaskDispatcher) {
                withinSpan("LoadAsset") {
                    context.getAssetAsRumResource("license.txt").bufferedReader().use { it.readText() }
                }
            }
            onDone(text)
        }
    }

    fun stopAsyncOperations() {
        taskScope.coroutineContext.cancelChildren()
    }
}
