/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.extensions;

import org.mockito.Mockito;

public class JavaClassWithStaticMock {
    @SuppressWarnings("unused")
    static Object BAD_MOCK = Mockito.mock(Object.class);
}
