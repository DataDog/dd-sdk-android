/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.plugin

import android.content.Context
import com.datadog.android.privacy.TrackingConsent

/**
 * Used to deliver the context from the SDK internals to a [DatadogPlugin] implementation.
 * @param context [Context] object.
 * @param envName Environment name.
 * @param serviceName Service name.
 * @param trackingConsent Tracking consent.
 */
class DatadogPluginConfig(
    val context: Context,
    val envName: String,
    val serviceName: String,
    val trackingConsent: TrackingConsent
)
