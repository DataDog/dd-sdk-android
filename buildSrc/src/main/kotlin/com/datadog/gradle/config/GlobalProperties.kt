package com.datadog.gradle.config

object LocalProjectProperties {

    // you can set this property from your local.properties as: forceEnableLogcat = true | false
    const val FORCE_ENABLE_LOGCAT = "forceEnableLogcat"
}

object GlobalBuildConfigProperties {
    const val LOGCAT_ENABLED = "LOGCAT_ENABLED"
}