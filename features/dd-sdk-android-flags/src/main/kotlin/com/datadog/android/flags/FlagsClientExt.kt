/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags

/**
 * Event registrations for this SDK-created client. Each access is a lightweight facade over the same client.
 *
 * @throws IllegalArgumentException if this is a custom client without SDK event support.
 */
val FlagsClient.events: FlagsEvents
    get() = FlagsEvents(this)
