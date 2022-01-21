/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

import com.datadog.android.security.Encryption

/**
 * Defines security policy used by SDK.
 *
 *@param localDataEncryption Encryption mechanism to use for the all data stored by SDK
 *  locally (RUM data, logs data, trace data, etc.). If not provided, no encryption will be used.
 */
data class SecurityConfig(val localDataEncryption: Encryption?) {
    internal companion object {
        internal val DEFAULT: SecurityConfig = SecurityConfig(
            localDataEncryption = null
        )
    }
}
