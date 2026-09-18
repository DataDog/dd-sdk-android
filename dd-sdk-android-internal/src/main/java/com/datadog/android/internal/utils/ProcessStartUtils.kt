/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

/**
 * Returns [computed] if it falls within a plausible range relative to [fallback]; otherwise
 * returns [fallback].
 *
 * Two failure modes are guarded:
 * - computed > fallback  → impossible (process started after the reference point)
 * - fallback - computed > thresholdNs → unreasonably far in the past (OEM clock bug)
 */
fun guardedProcessStartNs(computed: Long, fallback: Long, thresholdNs: Long): Long =
    if (computed > fallback || fallback - computed > thresholdNs) fallback else computed
