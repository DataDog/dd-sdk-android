/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.configuration.RumViewEventWriteConfig
import com.datadog.android.rum.event.ViewEventMapper
import com.datadog.android.rum.internal.model.diffViewEvent
import com.datadog.android.rum.internal.utils.newRumEventWriteOperation
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.model.ViewUpdateEvent

internal data class RumViewUpdateData(
    val viewUpdate: ViewUpdateEvent,
    val viewEvent: ViewEvent
)

internal interface RumViewEventWriter {
    // Should be called from RUM processing thread only
    fun writeViewEvent(
        viewEvent: ViewEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        eventType: EventType
    )

    companion object {
        fun create(
            config: RumViewEventWriteConfig,
            viewEventMapper: ViewEventMapper,
            sdkCore: InternalSdkCore
        ): RumViewEventWriter {
            return RumViewEventWriterImpl(
                config = config,
                viewEventMapper = viewEventMapper,
                sdkCore = sdkCore
            )
        }
    }
}

internal class RumViewEventWriterImpl(
    private val config: RumViewEventWriteConfig,
    private val viewEventMapper: ViewEventMapper,
    private val sdkCore: InternalSdkCore
) : RumViewEventWriter {
    private var prevViewEvent: ViewEvent? = null

    override fun writeViewEvent(
        viewEvent: ViewEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        eventType: EventType
    ) {
        var mappedViewEvent: ViewEvent? = null

        sdkCore.newRumEventWriteOperation(
            datadogContext = datadogContext,
            writeScope = writeScope,
            rumDataWriter = writer,
            eventType = eventType,
            eventSource = {
                val mapped = try {
                    viewEventMapper.map(viewEvent)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    sdkCore.internalLogger.log(
                        level = InternalLogger.Level.WARN,
                        target = InternalLogger.Target.USER,
                        messageBuilder = { VIEW_EVENT_MAPPER_FALLBACK_WARNING_MESSAGE },
                        throwable = e
                    )
                    viewEvent
                }
                mappedViewEvent = mapped
                val prev = prevViewEvent

                when (config) {
                    RumViewEventWriteConfig.AlwaysFullView -> mapped
                    RumViewEventWriteConfig.FullViewOnlyAtStart -> {
                        if (prev == null) {
                            mapped
                        } else {
                            RumViewUpdateData(
                                viewUpdate = diffViewEvent(prev, mapped),
                                viewEvent = mapped
                            )
                        }
                    }
                }
            }
        ).onSuccess {
            mappedViewEvent?.let { prevViewEvent = it }
        }.submit()
    }

    companion object {
        internal const val VIEW_EVENT_MAPPER_FALLBACK_WARNING_MESSAGE =
            "ViewEventMapper failed, using original ViewEvent."
    }
}
