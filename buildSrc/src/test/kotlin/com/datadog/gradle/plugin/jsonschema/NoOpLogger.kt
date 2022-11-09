/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.gradle.plugin.jsonschema

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.slf4j.Marker

class NoOpLogger : Logger {
    override fun getName(): String = "NoOp"

    override fun isTraceEnabled(): Boolean = false

    override fun isTraceEnabled(marker: Marker?): Boolean = false

    override fun trace(msg: String?) {}

    override fun trace(format: String?, arg: Any?) {}

    override fun trace(format: String?, arg1: Any?, arg2: Any?) {}

    override fun trace(format: String?, vararg arguments: Any?) {}

    override fun trace(msg: String?, t: Throwable?) {}

    override fun trace(marker: Marker?, msg: String?) {}

    override fun trace(marker: Marker?, format: String?, arg: Any?) {}

    override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {}

    override fun trace(marker: Marker?, format: String?, vararg argArray: Any?) {}

    override fun trace(marker: Marker?, msg: String?, t: Throwable?) {}

    override fun isDebugEnabled(): Boolean = false

    override fun isDebugEnabled(marker: Marker?): Boolean = false

    override fun debug(message: String?, vararg objects: Any?) {}

    override fun debug(msg: String?) {}

    override fun debug(format: String?, arg: Any?) {}

    override fun debug(format: String?, arg1: Any?, arg2: Any?) {}

    override fun debug(msg: String?, t: Throwable?) {}

    override fun debug(marker: Marker?, msg: String?) {}

    override fun debug(marker: Marker?, format: String?, arg: Any?) {}

    override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {}

    override fun debug(marker: Marker?, format: String?, vararg arguments: Any?) {}

    override fun debug(marker: Marker?, msg: String?, t: Throwable?) {}

    override fun isInfoEnabled(): Boolean = false

    override fun isInfoEnabled(marker: Marker?): Boolean = false

    override fun info(message: String?, vararg objects: Any?) {}

    override fun info(msg: String?) {}

    override fun info(format: String?, arg: Any?) {}

    override fun info(format: String?, arg1: Any?, arg2: Any?) {}

    override fun info(msg: String?, t: Throwable?) {}

    override fun info(marker: Marker?, msg: String?) {}

    override fun info(marker: Marker?, format: String?, arg: Any?) {}

    override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {}

    override fun info(marker: Marker?, format: String?, vararg arguments: Any?) {}

    override fun info(marker: Marker?, msg: String?, t: Throwable?) {}

    override fun isWarnEnabled(): Boolean = false

    override fun isWarnEnabled(marker: Marker?): Boolean = false

    override fun warn(msg: String?) {}

    override fun warn(format: String?, arg: Any?) {}

    override fun warn(format: String?, vararg arguments: Any?) {}

    override fun warn(format: String?, arg1: Any?, arg2: Any?) {}

    override fun warn(msg: String?, t: Throwable?) {}

    override fun warn(marker: Marker?, msg: String?) {}

    override fun warn(marker: Marker?, format: String?, arg: Any?) {}

    override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {}

    override fun warn(marker: Marker?, format: String?, vararg arguments: Any?) {}

    override fun warn(marker: Marker?, msg: String?, t: Throwable?) {}

    override fun isErrorEnabled(): Boolean = false

    override fun isErrorEnabled(marker: Marker?): Boolean = false

    override fun error(msg: String?) {}

    override fun error(format: String?, arg: Any?) {}

    override fun error(format: String?, arg1: Any?, arg2: Any?) {}

    override fun error(format: String?, vararg arguments: Any?) {}

    override fun error(msg: String?, t: Throwable?) {}

    override fun error(marker: Marker?, msg: String?) {}

    override fun error(marker: Marker?, format: String?, arg: Any?) {}

    override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {}

    override fun error(marker: Marker?, format: String?, vararg arguments: Any?) {}

    override fun error(marker: Marker?, msg: String?, t: Throwable?) {}

    override fun isLifecycleEnabled(): Boolean = false

    override fun lifecycle(message: String?) {}

    override fun lifecycle(message: String?, vararg objects: Any?) {}

    override fun lifecycle(message: String?, throwable: Throwable?) {}

    override fun isQuietEnabled(): Boolean = false

    override fun quiet(message: String?) {}

    override fun quiet(message: String?, vararg objects: Any?) {}

    override fun quiet(message: String?, throwable: Throwable?) {}

    override fun isEnabled(level: LogLevel?): Boolean = false

    override fun log(level: LogLevel?, message: String?) {}

    override fun log(level: LogLevel?, message: String?, vararg objects: Any?) {}

    override fun log(level: LogLevel?, message: String?, throwable: Throwable?) {}
}
