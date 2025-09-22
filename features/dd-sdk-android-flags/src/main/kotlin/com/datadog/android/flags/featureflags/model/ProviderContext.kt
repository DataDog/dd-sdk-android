/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.model

/**
 * Context for the Flags provider.
 * @param trackingKey TrackingKey to use.
 * @param user User to use.
 */
data class ProviderContext(val trackingKey: String, val user: FeatureFlagsUser)
