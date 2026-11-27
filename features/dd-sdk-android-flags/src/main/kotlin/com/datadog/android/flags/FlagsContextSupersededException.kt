/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Indicates that a later context request replaced this request before its result was processed.
 * Applies when experimentation opt-out or subject-authorization ordering is active. With response signing enabled,
 * it also applies in every opt-out mode to a request that fails admission because a newer request started, unless
 * its callback has already completed.
 */
class FlagsContextSupersededException internal constructor(message: String) : IllegalStateException(message)
