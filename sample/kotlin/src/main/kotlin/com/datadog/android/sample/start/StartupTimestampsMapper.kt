package com.datadog.android.sample.start

import android.app.ApplicationStartInfo
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal object StartupTimestampsMapper {

  const val KEY_PRE_ON_CREATE: Int = ApplicationStartInfo.START_TIMESTAMP_RESERVED_RANGE_DEVELOPER_START

  const val KEY_POST_ON_CREATE: Int = ApplicationStartInfo.START_TIMESTAMP_RESERVED_RANGE_DEVELOPER_START + 1

  fun toStartupTimestamps(timestampMap: Map<Int, Long>): StartupTimestamps {
    return StartupTimestamps(
      applicationOnCreate = timestampMap[ApplicationStartInfo.START_TIMESTAMP_APPLICATION_ONCREATE],
      bindApplication = timestampMap[ApplicationStartInfo.START_TIMESTAMP_BIND_APPLICATION],
      firstFrame = timestampMap[ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME],
      fork = timestampMap[ApplicationStartInfo.START_TIMESTAMP_FORK],
      fullyDrawn = timestampMap[ApplicationStartInfo.START_TIMESTAMP_FULLY_DRAWN],
      initialRenderThreadFrame = timestampMap[ApplicationStartInfo.START_TIMESTAMP_INITIAL_RENDERTHREAD_FRAME],
      launch = timestampMap[ApplicationStartInfo.START_TIMESTAMP_LAUNCH],
      preOnCreate = timestampMap[KEY_PRE_ON_CREATE],
      postOnCreate = timestampMap[KEY_POST_ON_CREATE],
      reservedRangeSystem = timestampMap[ApplicationStartInfo.START_TIMESTAMP_RESERVED_RANGE_SYSTEM],
      surfaceFlingerCompositionComplete = timestampMap[ApplicationStartInfo.START_TIMESTAMP_SURFACEFLINGER_COMPOSITION_COMPLETE]
    )
  }
}
