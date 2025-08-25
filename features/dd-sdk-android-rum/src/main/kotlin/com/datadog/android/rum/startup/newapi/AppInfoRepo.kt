/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.startup.newapi

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AppInfoRepo {
    fun streaming(): Flow<AppStartInfoBean>

    fun subscribe(block: (AppStartInfoBean) -> Unit)

    companion object {
        fun create(context: Context): AppInfoRepo {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                ProdAppInfoRepo(context)
            } else {
                StubAppInfoRepo
            }
        }
    }
}

object StubAppInfoRepo : AppInfoRepo {
    override fun streaming(): Flow<AppStartInfoBean> {
        return emptyFlow()
    }

    override fun subscribe(block: (AppStartInfoBean) -> Unit) {
        // no-op
    }
}
