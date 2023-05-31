/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger;

import fr.xgouchet.elmyr.annotation.StringForgery;
import fr.xgouchet.elmyr.junit5.ForgeExtension;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("KotlinInternalInJava")
@ExtendWith(ForgeExtension.class)
public class LogcatLogHandlerJavaTest {

    LogcatLogHandler testedHandler;

    @StringForgery
    String fakeServiceName;

    @Test
    void resolves_stack_trace_element_null_if_in_release_mode() {
        testedHandler = new LogcatLogHandler(fakeServiceName, true, false);

        StackTraceElement element = testedHandler.getCallerStackElement$dd_sdk_android_logs_debug();

        assertThat(element)
                .isNull();
    }

    @Test
    void resolves_stack_trace_element_null_if_useClassnameAsTag_is_false() {
        testedHandler = new LogcatLogHandler(fakeServiceName, false, true);

        StackTraceElement element = testedHandler.getCallerStackElement$dd_sdk_android_logs_debug();

        assertThat(element)
                .isNull();
    }

    @Test
    void resolves_stack_trace_element_from_caller() {
        testedHandler = new LogcatLogHandler(fakeServiceName, true, true);

        StackTraceElement element = testedHandler.getCallerStackElement$dd_sdk_android_logs_debug();

        assertThat(element).isNotNull();
        assertThat(element.getClassName())
                .isEqualTo(getClass().getCanonicalName());
    }

    @Test
    void resolves_nested_stack_trace_element_from_caller() {
        testedHandler = new LogcatLogHandler(fakeServiceName, true, true);

        AtomicReference<StackTraceElement> elementRef = new AtomicReference<>();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                elementRef.set(testedHandler.getCallerStackElement$dd_sdk_android_logs_debug());
            }
        };
        runnable.run();

        assertThat(elementRef).isNotNull();
        assertThat(elementRef.get().getClassName())
                .isEqualTo(getClass().getCanonicalName() + "$1");
    }
}
