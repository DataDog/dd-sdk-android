/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.extensions;

public class JavaClassWithNestedStaticMock {
    @SuppressWarnings({"InstantiationOfUtilityClass", "unused"})
    static JavaClassWithStaticMock NESTED_WITH_MOCK = new JavaClassWithStaticMock();
}
