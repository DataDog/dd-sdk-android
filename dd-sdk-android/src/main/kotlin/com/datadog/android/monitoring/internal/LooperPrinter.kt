/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.monitoring.internal

import android.os.SystemClock
import android.util.Printer
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.Logger
import java.util.concurrent.TimeUnit

class LooperPrinter : Printer {

    private val logger = Logger.Builder()
        .setLoggerName("dd_main_thread")
        .setLogcatLogsEnabled(true)
        .build()

    private var startUptimeNanoSecs: Long = 0L
    private var dispatchingMessage: String = ""

    override fun println(x: String?) {
        val firstChar = x?.first() ?: ' '
        val currentUptimeNs = SystemClock.elapsedRealtimeNanos()

        when (firstChar) {
            '>' -> {
                startUptimeNanoSecs = currentUptimeNs
                dispatchingMessage = x.orEmpty()
            }
            '<' -> checkDuration(startUptimeNanoSecs, currentUptimeNs, dispatchingMessage)
        }
    }

    private fun checkDuration(startNs: Long, endNs: Long, looperMessage: String) {

        val match = DISPATCHING_REGEX.matchEntire(looperMessage)
        val what = match?.groups?.get(3)?.value?.toInt() ?: 0
        val target = match?.groups?.get(1)?.value.orEmpty()
        val callback = match?.groups?.get(2)?.value

        val durationNs = endNs - startNs
        val message = "Long operation performed on main thread looper"
        val attributes = mutableMapOf(
            "operation.duration" to durationNs,
            "operation.target" to target,
            "operation.callback" to callback,
            "operation.what" to what
        )

        if (target.contains("android.app.ActivityThread\$H")) {
            attributes["operation.name"] = when (what) {
                110 -> "BIND_APPLICATION"
                111 -> "EXIT_APPLICATION"
                113 -> "RECEIVER"
                114 -> "CREATE_SERVICE"
                115 -> "SERVICE_ARGS"
                116 -> "STOP_SERVICE"
                118 -> "CONFIGURATION_CHANGED"
                119 -> "CLEAN_UP_CONTEXT"
                120 -> "GC_WHEN_IDLE"
                121 -> "BIND_SERVICE"
                122 -> "UNBIND_SERVICE"
                123 -> "DUMP_SERVICE"
                124 -> "LOW_MEMORY"
                127 -> "PROFILER_CONTROL"
                128 -> "CREATE_BACKUP_AGENT"
                129 -> "DESTROY_BACKUP_AGENT"
                130 -> "SUICIDE"
                131 -> "REMOVE_PROVIDER"
                133 -> "DISPATCH_PACKAGE_BROADCAST"
                134 -> "SCHEDULE_CRASH"
                135 -> "DUMP_HEAP"
                136 -> "DUMP_ACTIVITY"
                137 -> "SLEEPING"
                138 -> "SET_CORE_SETTINGS"
                139 -> "UPDATE_PACKAGE_COMPATIBILITY_INFO"
                141 -> "DUMP_PROVIDER"
                142 -> "UNSTABLE_PROVIDER_DIED"
                143 -> "REQUEST_ASSIST_CONTEXT_EXTRAS"
                144 -> "TRANSLUCENT_CONVERSION_COMPLETE"
                145 -> "INSTALL_PROVIDER"
                146 -> "ON_NEW_ACTIVITY_OPTIONS"
                149 -> "ENTER_ANIMATION_COMPLETE"
                150 -> "START_BINDER_TRACKING"
                151 -> "STOP_BINDER_TRACKING_AND_DUMP"
                154 -> "LOCAL_VOICE_INTERACTION_STARTED"
                155 -> "ATTACH_AGENT"
                156 -> "APPLICATION_INFO_CHANGED"
                158 -> "RUN_ISOLATED_ENTRY_POINT"
                159 -> "EXECUTE_TRANSACTION"
                160 -> "RELAUNCH_ACTIVITY"
                161 -> "PURGE_RESOURCES"
                else -> null
            }
        } else if (target.contains("android.view.ViewRootImpl\$ViewRootHandler")) {
            attributes["operation.name"] = when (what) {
                1 -> "MSG_INVALIDATE"
                2 -> "MSG_INVALIDATE_RECT"
                3 -> "MSG_DIE"
                4 -> "MSG_RESIZED"
                5 -> "MSG_RESIZED_REPORT"
                6 -> "MSG_WINDOW_FOCUS_CHANGED"
                7 -> "MSG_DISPATCH_INPUT_EVENT"
                8 -> "MSG_DISPATCH_APP_VISIBILITY"
                9 -> "MSG_DISPATCH_GET_NEW_SURFACE"
                11 -> "MSG_DISPATCH_KEY_FROM_IME"
                12 -> "MSG_DISPATCH_KEY_FROM_AUTOFILL"
                13 -> "MSG_CHECK_FOCUS"
                14 -> "MSG_CLOSE_SYSTEM_DIALOGS"
                15 -> "MSG_DISPATCH_DRAG_EVENT"
                16 -> "MSG_DISPATCH_DRAG_LOCATION_EVENT"
                17 -> "MSG_DISPATCH_SYSTEM_UI_VISIBILITY"
                18 -> "MSG_UPDATE_CONFIGURATION"
                19 -> "MSG_PROCESS_INPUT_EVENTS"
                21 -> "MSG_CLEAR_ACCESSIBILITY_FOCUS_HOST"
                22 -> "MSG_INVALIDATE_WORLD"
                23 -> "MSG_WINDOW_MOVED"
                24 -> "MSG_SYNTHESIZE_INPUT_EVENT"
                25 -> "MSG_DISPATCH_WINDOW_SHOWN"
                26 -> "MSG_REQUEST_KEYBOARD_SHORTCUTS"
                27 -> "MSG_UPDATE_POINTER_ICON"
                28 -> "MSG_POINTER_CAPTURE_CHANGED"
                29 -> "MSG_DRAW_FINISHED"
                30 -> "MSG_INSETS_CHANGED"
                31 -> "MSG_INSETS_CONTROL_CHANGED"
                32 -> "MSG_SYSTEM_GESTURE_EXCLUSION_CHANGED"
                else -> null
            }
        } else if (target.contains("android.view.inputmethod.InputMethodManager\$H")) {
            attributes["operation.name"] = when (what) {
                1 -> "MSG_DUMP"
                2 -> "MSG_BIND"
                3 -> "MSG_UNBIND"
                4 -> "MSG_SET_ACTIVE"
                5 -> "MSG_SEND_INPUT_EVENT"
                6 -> "MSG_TIMEOUT_INPUT_EVENT"
                7 -> "MSG_FLUSH_INPUT_EVENT"
                10 -> "MSG_REPORT_FULLSCREEN_MODE"
                15 -> "MSG_REPORT_PRE_RENDERED"
                20 -> "MSG_APPLY_IME_VISIBILITY"
                30 -> "MSG_UPDATE_ACTIVITY_VIEW_TO_SCREEN_MATRIX"
                else -> null
            }
        }

        if (durationNs > ALERT_DURATION_NS) {
            logger.wtf(message, null, attributes)
        } else if (durationNs > ERROR_DURATION_NS) {
            logger.e(message, null, attributes)
        } else if (durationNs > WARN_DURATION_NS) {
            logger.w(message, null, attributes)
        } else if (durationNs > INFO_DURATION_NS) {
            logger.i(message, null, attributes)
        }
    }

    companion object {

        private val DISPATCHING_REGEX =
            Regex(">>>>> Dispatching to ([\\w.]+ \\([\\w.$]+\\) \\{\\w+\\}) (.*): (\\d+)")
        private val INFO_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(16)
        private val WARN_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(200)
        private val ERROR_DURATION_NS = TimeUnit.SECONDS.toNanos(1)
        private val ALERT_DURATION_NS = TimeUnit.SECONDS.toNanos(5)
    }
}