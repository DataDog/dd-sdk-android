/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.log.internal.logger;

import com.datadog.android.BuildConfig;
import com.datadog.android.Datadog;
import fr.xgouchet.elmyr.annotation.StringForgery;
import fr.xgouchet.elmyr.annotation.StringForgeryType;
import fr.xgouchet.elmyr.junit5.ForgeExtension;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.atomic.AtomicReference;

@ExtendWith(ForgeExtension.class)
public class LogcatLogHandlerJavaTest {

    LogcatLogHandler testedHandler;

    @StringForgery(StringForgeryType.ALPHABETICAL)
    String fakeServiceName;

    @BeforeEach
    void setUp() {
        testedHandler = new LogcatLogHandler(fakeServiceName, true);
    }

    @AfterEach
    void tearDown() {
        Datadog.INSTANCE.setDebug$dd_sdk_android_debug(BuildConfig.DEBUG);
    }

    @Test
    void resolves_stack_trace_element_null_if_in_release_mode() {
        Datadog.INSTANCE.setDebug$dd_sdk_android_debug(false);

        StackTraceElement element = testedHandler.getCallerStackElement$dd_sdk_android_debug();

        assertThat(element)
                .isNull();
    }

    @Test
    void resolves_stack_trace_element_null_if_useClassnameAsTag_is_false() {
        testedHandler = new LogcatLogHandler(fakeServiceName, false);
        Datadog.INSTANCE.setDebug$dd_sdk_android_debug(true);

        StackTraceElement element = testedHandler.getCallerStackElement$dd_sdk_android_debug();

        assertThat(element)
                .isNull();
    }

    @Test
    void resolves_stack_trace_element_from_caller() {
        Datadog.INSTANCE.setDebug$dd_sdk_android_debug(true);

        StackTraceElement element = testedHandler.getCallerStackElement$dd_sdk_android_debug();

        assertThat(element).isNotNull();
        assertThat(element.getClassName())
                .isEqualTo(getClass().getCanonicalName());
    }

    @Test
    void resolves_nested_stack_trace_element_from_caller() {
        Datadog.INSTANCE.setDebug$dd_sdk_android_debug(true);

        AtomicReference<StackTraceElement> elementRef = new AtomicReference<>();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                elementRef.set(testedHandler.getCallerStackElement$dd_sdk_android_debug());
            }
        };
        runnable.run();

        assertThat(elementRef).isNotNull();
        assertThat(elementRef.get().getClassName())
                .isEqualTo(getClass().getCanonicalName() + "$1");
    }
}
