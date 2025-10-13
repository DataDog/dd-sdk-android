/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

/**
 * Internal event to pass the Time To Initial Display (TTID) value in nanoseconds.
 *
 * @param value The TTID value in nanoseconds.
 */
data class TTIDEvent(val value: Long)
