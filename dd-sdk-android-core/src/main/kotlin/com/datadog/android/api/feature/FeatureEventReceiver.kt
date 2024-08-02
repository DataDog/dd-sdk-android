/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.feature

import androidx.annotation.AnyThread

/**
 * Interface to implement in order to receive events sent using [FeatureScope.sendEvent] API.
 */
fun interface FeatureEventReceiver {

    /**
     * Method invoked when event is received. It will be invoked on the thread which sent event.
     *
     * @param event Incoming event.
     */
    @AnyThread
    fun onReceive(event: Any)
}
