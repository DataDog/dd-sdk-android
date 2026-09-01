/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datadog.android.rum.resource.getAssetAsRumResource
import com.datadog.android.rum.resource.getRawResAsRumResource
import com.datadog.android.sample.R
import com.datadog.android.trace.withinSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader

internal class AboutViewModel : ViewModel() {

    private var aboutJob: Job? = null
    private var licenseJob: Job? = null

    fun getAboutText(
        context: Context,
        onDone: (String) -> Unit = {}
    ) {
        aboutJob?.cancel()
        aboutJob = viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                withinSpan("LoadResource") {
                    context.getRawResAsRumResource(R.raw.about)
                        .bufferedReader()
                        .use(BufferedReader::readText)
                }
            }
            onDone(text)
        }
    }

    fun getLicenseText(
        context: Context,
        onDone: (String) -> Unit = {}
    ) {
        licenseJob?.cancel()
        licenseJob = viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                withinSpan("LoadAsset") {
                    context.getAssetAsRumResource("license.txt")
                        .bufferedReader()
                        .use(BufferedReader::readText)
                }
            }
            onDone(text)
        }
    }

    fun stopAsyncOperations() {
        licenseJob?.cancel()
        aboutJob?.cancel()
        licenseJob = null
        aboutJob = null
    }
}
