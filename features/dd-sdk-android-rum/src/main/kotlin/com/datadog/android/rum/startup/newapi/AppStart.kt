package com.datadog.android.rum.startup.newapi

import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
fun getAppStartInfo(context: Context, callback: (ApplicationStartInfo) -> Unit) {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    manager.addApplicationStartInfoCompletionListener(ContextCompat.getMainExecutor(context), callback)
}
