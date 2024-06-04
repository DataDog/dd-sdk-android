/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

/**
 * Defines the mitigation to use when a queue hits the maximum back pressure capacity.
 */
enum class BackPressureMitigation {

    /**  Drop the oldest items already in the queue to make room for new ones. */
    DROP_OLDEST,

    /**  Ignore newest items that are not yet in the queue. */
    IGNORE_NEWEST
}
