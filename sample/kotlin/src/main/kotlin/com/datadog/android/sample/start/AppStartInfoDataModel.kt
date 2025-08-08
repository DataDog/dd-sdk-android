package com.datadog.android.sample.start

data class AppStartInfoBean(
    val startType: StartType, // what is the starting point, e.g. hot/cold start
    val startupState: StartupState, // what stage of starting of the app occured, e.g. started/first image rendered
    val startReason: StartReason, // why the app was started
    val launchMode: LaunchMode, // e.g. SINGLE_TASK, SINGLE_TOP
    val timestamps: StartupTimestamps, // timestamps of the events
    val wasForceStopped: Boolean // if the app was forced previously
)

enum class LaunchMode {
  LAUNCH_MODE_UNKNOWN,
  LAUNCH_MODE_SINGLE_INSTANCE,
  LAUNCH_MODE_SINGLE_INSTANCE_PER_TASK,
  LAUNCH_MODE_SINGLE_TASK,
  LAUNCH_MODE_SINGLE_TOP,
  LAUNCH_MODE_STANDARD;
}

enum class StartupState {
  STARTUP_STATE_UNKNOWN,
  STARTUP_STATE_ERROR,
  STARTUP_STATE_FIRST_FRAME_DRAWN,
  STARTUP_STATE_STARTED;
}

enum class StartReason {
  START_REASON_UNKNOWN,
  START_REASON_ALARM,
  START_REASON_BACKUP,
  START_REASON_BOOT_COMPLETE,
  START_REASON_BROADCAST,
  START_REASON_CONTENT_PROVIDER,
  START_REASON_JOB,
  START_REASON_LAUNCHER,
  START_REASON_LAUNCHER_RECENTS,
  START_REASON_OTHER,
  START_REASON_PUSH,
  START_REASON_SERVICE,
  START_REASON_START_ACTIVITY;
}

enum class StartType {
  START_TYPE_UNKNOWN,
  START_TYPE_COLD,
  START_TYPE_HOT,
  START_TYPE_UNSET,
  START_TYPE_WARM;
}

data class StartupTimestamps(
  val applicationOnCreate: Long? = null,
  val bindApplication: Long? = null,
  val firstFrame: Long? = null,
  val fork: Long? = null,
  val fullyDrawn: Long? = null,
  val initialRenderThreadFrame: Long? = null,
  val launch: Long? = null,
  val postOnCreate: Long? = null,
  val preOnCreate: Long? = null,
  val reservedRangeSystem: Long? = null,
  val surfaceFlingerCompositionComplete: Long? = null
)

