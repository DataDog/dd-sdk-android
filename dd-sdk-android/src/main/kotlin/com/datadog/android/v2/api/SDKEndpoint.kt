/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api

/**
 * One of Datadog's official endpoints, related to our existing datacenters.
 */
enum class SDKEndpoint {
    US1,
    US3,
    US5,
    US1_FED,
    EU1
    // DISCUSS should staging endpoints be available here
    // DISCUSS What about custom endpoints?
}
