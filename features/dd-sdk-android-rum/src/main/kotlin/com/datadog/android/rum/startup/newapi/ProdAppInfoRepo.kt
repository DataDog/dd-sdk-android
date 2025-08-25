/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.startup.newapi

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class ProdAppInfoRepo(private val context: Context) : AppInfoRepo {
    private val behaviorSubject = MutableStateFlow<AppStartInfoBean?>(null)

    init {
        activityManager(context).addApplicationStartInfoCompletionListener(Executors.newSingleThreadExecutor()) {
            behaviorSubject.tryEmit(AppStartInfoMapper.mapStartInfo(it))
        }
    }

    private fun activityManager(context: Context): ActivityManager {
        return context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    override fun streaming(): Flow<AppStartInfoBean> {
        return behaviorSubject.filterNotNull()
    }

    override fun subscribe(block: (AppStartInfoBean) -> Unit) {
        activityManager(context).addApplicationStartInfoCompletionListener(Executors.newSingleThreadExecutor()) {
            block(AppStartInfoMapper.mapStartInfo(it))
        }
    }
}
