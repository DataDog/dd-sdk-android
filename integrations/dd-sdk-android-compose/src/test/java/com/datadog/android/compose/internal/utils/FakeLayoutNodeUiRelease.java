/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose.internal.utils;

/**
 * Fake object used in tests to simulate an internal Compose class that exposes
 * methods with the {@code $ui_release} module-suffix naming convention (the JVM-mangled
 * name for {@code internal} members in the release variant of the
 * {@code androidx.compose.ui} module).
 * Used to verify the reflection fallback in {@link LayoutNodeUtils}.
 */
public class FakeLayoutNodeUiRelease {

    public final Object value;

    public FakeLayoutNodeUiRelease(Object value) {
        this.value = value;
    }

    public Object getLayoutDelegate$ui_release() {
        return value;
    }

    public Object getOuterCoordinator$ui_release() {
        return value;
    }

    public Object getCoordinates$ui_release() {
        return value;
    }
}
