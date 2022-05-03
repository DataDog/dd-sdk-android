/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

/**
 * Contains the upload configuration for an [SDKFeature] instance.
 *
 * @param endpointUrl the url endpoint data should be uploaded to
 */
data class SDKFeatureUploadConfiguration(
    val endpointUrl: String
    // TODO RUMM-2138 Payload format, …
)
