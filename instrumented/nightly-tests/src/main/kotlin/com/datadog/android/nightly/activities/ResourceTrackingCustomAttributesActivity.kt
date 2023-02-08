/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.activities

import com.datadog.android.nightly.SPECIAL_INT_ATTRIBUTE_NAME
import com.datadog.android.nightly.SPECIAL_NULL_ATTRIBUTE_NAME
import com.datadog.android.nightly.SPECIAL_STRING_ATTRIBUTE_NAME
import com.datadog.android.okhttp.rum.RumInterceptor
import com.datadog.android.rum.RumResourceAttributesProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal class ResourceTrackingCustomAttributesActivity : ResourceTrackingActivity() {

    override val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(
                RumInterceptor(
                    rumResourceAttributesProvider = object :
                        RumResourceAttributesProvider {
                        override fun onProvideAttributes(
                            request: Request,
                            response: Response?,
                            throwable: Throwable?
                        ): Map<String, Any?> {
                            return mapOf(
                                SPECIAL_STRING_ATTRIBUTE_NAME to "custom_string_value",
                                SPECIAL_INT_ATTRIBUTE_NAME to 2,
                                SPECIAL_NULL_ATTRIBUTE_NAME to null
                            )
                        }
                    },
                    traceSamplingRate = HUNDRED_PERCENT
                )
            )
            .build()
    }
}
