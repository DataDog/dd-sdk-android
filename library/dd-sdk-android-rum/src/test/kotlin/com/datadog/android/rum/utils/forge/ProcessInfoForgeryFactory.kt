/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import android.app.ActivityManager
import com.datadog.android.v2.api.context.ProcessInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

// TODO RUMM-2949 Share forgeries/test configurations between modules
class ProcessInfoForgeryFactory : ForgeryFactory<ProcessInfo> {
    override fun getForgery(forge: Forge): ProcessInfo {
        return ProcessInfo(
            isMainProcess = forge.aBool(),
            processImportance = forge.anElementFrom(
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_CANT_SAVE_STATE,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE
            )
        )
    }
}
