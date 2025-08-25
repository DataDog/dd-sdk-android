package com.datadog.android.rum.startup.newapi

import android.app.ApplicationStartInfo
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal object AppStartInfoMapper {

  fun mapStartInfo(startInfo: ApplicationStartInfo): AppStartInfoBean {
    val startType = AppStartInfoModelMapper.toAppStartType(startInfo.startType)
    val startReason = AppStartInfoModelMapper.toAppStartReason(startInfo.reason)
    val startUpState = AppStartInfoModelMapper.toAppStartState(startInfo.startupState)
    val launchMode = AppStartInfoModelMapper.toAppLaunchMode(startInfo.launchMode)
    val startTimestamp = StartupTimestampsMapper.toStartupTimestamps(startInfo.startupTimestamps)
    val wasForceStopped = startInfo.wasForceStopped()
    val startIntent = startInfo.intent.toString()
    println("Ignored app_startup intent : $startIntent")
    return AppStartInfoBean(
      startType,
      startUpState,
      startReason,
      launchMode,
      startTimestamp,
      wasForceStopped
    ).also {
      println("Assembled app_startup bean: $it")
    }
  }

}