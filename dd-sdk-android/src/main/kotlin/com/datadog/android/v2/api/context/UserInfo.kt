/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.api.context

/**
 * Holds information about the current User.
 * @property id a unique identifier for the user, or null
 * @property name the name of the user, or null
 * @property email the email address of the user, or null
 * @property additionalProperties a dictionary of custom properties attached to the current user
 */
data class UserInfo(
    val id: String?,
    val name: String?,
    val email: String?,
    val additionalProperties: Map<String, Any?>
)
