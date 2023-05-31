/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.monitor

import com.datadog.android.rum.internal.domain.event.ResourceTiming

/**
 * FOR INTERNAL USAGE ONLY.
 */
@SuppressWarnings("UndocumentedPublicFunction")
interface AdvancedNetworkRumMonitor {

    fun waitForResourceTiming(key: String)

    fun addResourceTiming(key: String, timing: ResourceTiming)

    fun notifyInterceptorInstantiated()
}
