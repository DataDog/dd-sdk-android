/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.storage

import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventBatchWriter
import com.datadog.android.log.model.LogEvent

// TODO RUMM-0000 Support generating NoOpImplementation as public?
internal class NoOpDataWriter : DataWriter<LogEvent> {
    override fun write(writer: EventBatchWriter, element: LogEvent) = false
}
