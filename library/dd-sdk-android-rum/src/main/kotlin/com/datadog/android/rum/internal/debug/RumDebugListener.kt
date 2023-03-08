/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.debug

import androidx.annotation.AnyThread

internal interface RumDebugListener {
    /**
     * It receives collection on purpose - it will allow to debug a situation when previous view
     * is not yet collected when another view is started.
     */
    @AnyThread
    fun onReceiveRumActiveViews(viewNames: List<String>)
}
