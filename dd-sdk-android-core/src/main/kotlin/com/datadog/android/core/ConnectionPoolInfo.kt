/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core

/**
 * Provide information abouth the internal OkHttp Connection pool.
 * @property connectionCount the total number of connections in the pool
 * @property idleConnectionCount the number of idle connections in the pool
 */
data class ConnectionPoolInfo(
    val connectionCount: Int,
    val idleConnectionCount: Int
)
