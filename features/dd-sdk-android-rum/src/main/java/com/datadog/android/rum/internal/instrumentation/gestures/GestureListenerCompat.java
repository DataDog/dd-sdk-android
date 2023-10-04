/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation.gestures;

import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class GestureListenerCompat implements GestureDetector.OnGestureListener {

    @Override
    public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
        // This implementation is no op, and just there to be able to accept nullable e1 param
        // in our Kotlin implementation.
        // Since Android API 33, the param was annotated with @NonNull, but older versions of Android
        // can still call it with null values. Our Kotlin implementation throws NPEs because of this.
        return false;
    }

    @Override
    public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
        // This implementation is no op, and just there to be able to accept nullable e1 param
        // in our Kotlin implementation.
        // Since Android API 33, the param was annotated with @NonNull, but older versions of Android
        // can still call it with null values. Our Kotlin implementation throws NPEs because of this.
        return false;
    }
}
