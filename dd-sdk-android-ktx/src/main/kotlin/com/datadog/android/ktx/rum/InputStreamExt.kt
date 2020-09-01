/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.ktx.rum

import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.resource.RumResourceInputStream
import java.io.InputStream

/**
 * Allow the [RumMonitor] to track this [InputStream] as a RUM Resource.
 *
 * @param url the url to be associated with this resource
 */
fun InputStream.asRumResource(url: String): InputStream {
    return RumResourceInputStream(this, url)
}
