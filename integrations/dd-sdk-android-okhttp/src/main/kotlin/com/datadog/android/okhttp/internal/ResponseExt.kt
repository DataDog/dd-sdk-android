/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.internal

import com.datadog.android.internal.network.HttpSpec
import okhttp3.MediaType
import okhttp3.Response

/** Returns the MIME type without parameters such as charset. */
internal fun MediaType.mimeType(): String = "$type/$subtype"

/** Uses the already-read MIME type so this check never accesses the response body. */
internal fun Response.isStreaming(mimeType: String?): Boolean =
    HttpSpec.ContentType.isStream(mimeType) ||
        !header(HttpSpec.Header.WEBSOCKET_ACCEPT_HEADER, null).isNullOrBlank()
