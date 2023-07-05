/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils

internal const val HUNDRED = 100.0

/**
 * Converts value (which should be in the range 0 to 100) to the percent format.
 */
internal fun Float.percent(): Double = this / HUNDRED
