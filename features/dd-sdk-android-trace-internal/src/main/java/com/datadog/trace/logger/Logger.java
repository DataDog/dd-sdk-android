
/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.trace.logger;
public interface Logger {
    public static String ROOT_LOGGER_NAME = "ROOT";

    public boolean isInfoEnabled();
    public boolean isWarnEnabled();

    public boolean isDebugEnabled();
    public void debug(String msg);
    public void debug(String format, Object arg);
    public void debug(String format, Object arg1, Object arg2);
    public void debug(String format, Object... arguments);
    public void debug(String msg, Throwable t);
    public void info(String msg);
    public void info(String format, Object arg);
    public void info(String format, Object arg1, Object arg2);
    public void info(String format, Object... arguments);
    public void info(String msg, Throwable t);
    public void warn(String msg);
    public void warn(String format, Object arg);
    public void warn(String format, Object arg1, Object arg2);
    public void warn(String format, Object... arguments);
    public void warn(String msg, Throwable t);
    public void error(String msg);
    public void error(String format, Object arg);
    public void error(String format, Object arg1, Object arg2);
    public void error(String format, Object... arguments);
    public void error(String msg, Throwable t);
}
