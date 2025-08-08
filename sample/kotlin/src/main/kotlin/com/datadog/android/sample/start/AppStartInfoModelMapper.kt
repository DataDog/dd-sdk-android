package com.datadog.android.sample.start

import android.app.ApplicationStartInfo
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.sample.start.LaunchMode
import com.datadog.android.sample.start.StartReason
import com.datadog.android.sample.start.StartType
import com.datadog.android.sample.start.StartupState


@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal object AppStartInfoModelMapper {
  fun toAppLaunchMode(value: Int): LaunchMode {
    return when (value) {
      ApplicationStartInfo.LAUNCH_MODE_SINGLE_INSTANCE -> LaunchMode.LAUNCH_MODE_SINGLE_INSTANCE
      ApplicationStartInfo.LAUNCH_MODE_SINGLE_INSTANCE_PER_TASK -> LaunchMode.LAUNCH_MODE_SINGLE_INSTANCE_PER_TASK
      ApplicationStartInfo.LAUNCH_MODE_SINGLE_TASK -> LaunchMode.LAUNCH_MODE_SINGLE_TASK
      ApplicationStartInfo.LAUNCH_MODE_SINGLE_TOP -> LaunchMode.LAUNCH_MODE_SINGLE_TOP
      ApplicationStartInfo.LAUNCH_MODE_STANDARD -> LaunchMode.LAUNCH_MODE_STANDARD
      else -> LaunchMode.LAUNCH_MODE_UNKNOWN
    }
  }

  fun toAppStartState(value: Int): StartupState {
    return when (value) {
      ApplicationStartInfo.STARTUP_STATE_ERROR -> StartupState.STARTUP_STATE_ERROR
      ApplicationStartInfo.STARTUP_STATE_FIRST_FRAME_DRAWN -> StartupState.STARTUP_STATE_FIRST_FRAME_DRAWN
      ApplicationStartInfo.STARTUP_STATE_STARTED -> StartupState.STARTUP_STATE_STARTED
      else -> StartupState.STARTUP_STATE_UNKNOWN
    }
  }

  fun toAppStartReason(value: Int): StartReason {
    return when (value) {
      ApplicationStartInfo.START_REASON_ALARM -> StartReason.START_REASON_ALARM
      ApplicationStartInfo.START_REASON_BACKUP -> StartReason.START_REASON_BACKUP
      ApplicationStartInfo.START_REASON_BOOT_COMPLETE -> StartReason.START_REASON_BOOT_COMPLETE
      ApplicationStartInfo.START_REASON_BROADCAST -> StartReason.START_REASON_BROADCAST
      ApplicationStartInfo.START_REASON_CONTENT_PROVIDER -> StartReason.START_REASON_CONTENT_PROVIDER
      ApplicationStartInfo.START_REASON_JOB -> StartReason.START_REASON_JOB
      ApplicationStartInfo.START_REASON_LAUNCHER -> StartReason.START_REASON_LAUNCHER
      ApplicationStartInfo.START_REASON_LAUNCHER_RECENTS -> StartReason.START_REASON_LAUNCHER_RECENTS
      ApplicationStartInfo.START_REASON_OTHER -> StartReason.START_REASON_OTHER
      ApplicationStartInfo.START_REASON_PUSH -> StartReason.START_REASON_PUSH
      ApplicationStartInfo.START_REASON_SERVICE -> StartReason.START_REASON_SERVICE
      ApplicationStartInfo.START_REASON_START_ACTIVITY -> StartReason.START_REASON_START_ACTIVITY
      else -> StartReason.START_REASON_UNKNOWN
    }
  }

  fun toAppStartType(value: Int): StartType {
    return when (value) {
      ApplicationStartInfo.START_TYPE_COLD -> StartType.START_TYPE_COLD
      ApplicationStartInfo.START_TYPE_HOT -> StartType.START_TYPE_HOT
      ApplicationStartInfo.START_TYPE_UNSET -> StartType.START_TYPE_UNSET
      ApplicationStartInfo.START_TYPE_WARM -> StartType.START_TYPE_WARM
      else -> StartType.START_TYPE_UNKNOWN
    }
  }

}
