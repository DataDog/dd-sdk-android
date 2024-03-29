/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.logger;


import com.datadog.android.api.InternalLogger;

import java.util.Arrays;
import java.util.Locale;

// TODO: 04/03/2024 https://datadoghq.atlassian.net/browse/RUM-3405 Add a proper implementation here
public class DatadogCoreTracerLogger implements Logger {

    static final String LEGACY_ARG_PLACEHOLDER = "{}";


    private final InternalLogger internalLogger;

    private final String loggerName;

    public DatadogCoreTracerLogger(String loggerName, InternalLogger internalLogger) {
        this.internalLogger = internalLogger;
        this.loggerName = loggerName;
    }

    @Override
    public boolean isDebugEnabled() {
        return false;
    }

    @Override
    public void debug(String msg) {
        internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                () -> bundleMessageWithTitle(loggerName, msg),
                null,
                false,
                null
        );
    }

    @Override
    public void debug(String format, Object arg) {
        internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                () -> generateLogMessage(loggerName, format, arg),
                null,
                false,
                null
        );
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
        internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                () -> generateLogMessage(loggerName, format, arg1, arg2),
                null,
                false,
                null
        );
    }

    @Override
    public void debug(String format, Object... arguments) {
        internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.MAINTAINER,
                () -> generateLogMessage(loggerName, format, arguments),
                null,
                false,
                null
        );
    }

    @Override
    public void debug(String msg, Throwable t) {
        internalLogger.log(
                InternalLogger.Level.DEBUG,
                Arrays.asList(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                () -> bundleMessageWithTitle(loggerName, msg),
                t,
                false,
                null
        );
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public void info(String msg) {
        internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                () -> bundleMessageWithTitle(loggerName, msg),
                null,
                false,
                null
        );
    }

    @Override
    public void info(String format, Object arg) {
        internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                () -> generateLogMessage(loggerName, format, arg),
                null,
                false,
                null
        );
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
        internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                () -> generateLogMessage(loggerName, format, arg1, arg2),
                null,
                false,
                null
        );
    }

    @Override
    public void info(String format, Object... arguments) {
        internalLogger.log(
                InternalLogger.Level.INFO,
                InternalLogger.Target.USER,
                () -> generateLogMessage(loggerName, format, arguments),
                null,
                false,
                null
        );
    }

    @Override
    public void info(String msg, Throwable t) {
        internalLogger.log(
                InternalLogger.Level.INFO,
                Arrays.asList(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                () -> bundleMessageWithTitle(loggerName, msg),
                t,
                false,
                null
        );
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public void warn(String msg) {
        internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                () -> bundleMessageWithTitle(loggerName, msg),
                null,
                false,
                null
        );
    }

    @Override
    public void warn(String format, Object arg) {
        internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                () -> generateLogMessage(loggerName, format, arg),
                null,
                false,
                null
        );
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
        internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                () -> generateLogMessage(loggerName, format, arg1, arg2),
                null,
                false,
                null
        );
    }

    @Override
    public void warn(String format, Object... arguments) {
        internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                () -> generateLogMessage(loggerName, format, arguments),
                null,
                false,
                null
        );
    }

    @Override
    public void warn(String msg, Throwable t) {
        internalLogger.log(
                InternalLogger.Level.WARN,
                Arrays.asList(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                () -> bundleMessageWithTitle(loggerName, msg),
                t,
                false,
                null
        );
    }

    @Override
    public void error(String msg) {
        internalLogger.log(
                InternalLogger.Level.ERROR,
                Arrays.asList(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                () -> bundleMessageWithTitle(loggerName, msg),
                null,
                false,
                null
        );
    }

    @Override
    public void error(String format, Object arg) {
        internalLogger.log(
                InternalLogger.Level.ERROR,
                Arrays.asList(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                () -> generateLogMessage(loggerName, format, arg),
                null,
                false,
                null
        );
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
        internalLogger.log(
                InternalLogger.Level.ERROR,
                Arrays.asList(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                () -> generateLogMessage(loggerName, format, arg1, arg2),
                null,
                false,
                null
        );
    }

    @Override
    public void error(String format, Object... arguments) {
        internalLogger.log(
                InternalLogger.Level.ERROR,
                Arrays.asList(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                () -> generateLogMessage(loggerName, format, arguments),
                null,
                false,
                null
        );
    }

    @Override
    public void error(String msg, Throwable t) {
        internalLogger.log(
                InternalLogger.Level.ERROR,
                Arrays.asList(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                () -> bundleMessageWithTitle(loggerName, msg),
                t,
                false,
                null
        );
    }

    static String generateLogMessage(String title, final String format, Object... args) {
        final String sanitizedFormat = format.replace(LEGACY_ARG_PLACEHOLDER, "%s");
        return String.format(Locale.US, title + ": " + sanitizedFormat, args);
    }


    static String bundleMessageWithTitle(final String title, String message) {
        return String.format(Locale.US, "%s: %s", title, message);
    }
}
