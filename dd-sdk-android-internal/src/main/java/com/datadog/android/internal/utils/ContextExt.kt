/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal.utils

import android.content.Context

/**
 * Returns the system service associated with [name], cast to the reified type [T], or `null` if
 * the service is unavailable or not assignable to [T].
 * @param T the expected type of the system service.
 * @param name the name of the system service, e.g. [Context.ACTIVITY_SERVICE].
 */
inline fun <reified T> Context.getSystemServiceAs(name: String): T? = getSystemService(name) as? T
