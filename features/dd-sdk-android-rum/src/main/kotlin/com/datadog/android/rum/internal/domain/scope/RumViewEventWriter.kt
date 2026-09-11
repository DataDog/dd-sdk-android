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

internal data class MappedViewEvent(
    val viewEvent: ViewEvent
)

internal data class DiffThenFullView(
    val viewUpdate: ViewUpdateEvent,
    val viewEvent: ViewEvent
)

internal interface RumViewEventWriter {
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

                // The ViewEventMapper contract requires the same instance to be returned;
                // if a different reference comes back, ignore it and use the original event
                // (matches the identity check historically enforced in
                // RumEventMapper.resolveEvent() for the raw ViewEvent / crash-recovery path).
                val safeMapped = if (mapped !== viewEvent) {
                    sdkCore.internalLogger.log(
                        level = InternalLogger.Level.ERROR,
                        target = InternalLogger.Target.USER,
                        messageBuilder = { VIEW_EVENT_MAPPER_NOT_SAME_INSTANCE_WARNING_MESSAGE }
                    )
                    viewEvent
                } else {
                    mapped
                }

                mappedViewEvent = safeMapped
                val prev = prevViewEvent

                when (config) {
                    RumViewEventWriteConfig.AlwaysFullView -> MappedViewEvent(safeMapped)
                    RumViewEventWriteConfig.FullViewOnlyAtStart -> {
                        if (prev == null) {
                            MappedViewEvent(safeMapped)
                        } else if (shouldWriteFullView(safeMapped.dd.documentVersion, safeMapped.view.isActive)) {
                            // Send the last partial diff followed immediately by the full view checkpoint
                            DiffThenFullView(
                                viewUpdate = diffViewEvent(prev, safeMapped),
                                viewEvent = safeMapped
                            )
                        } else {
                            RumViewUpdateData(
                                viewUpdate = diffViewEvent(prev, safeMapped),
                                viewEvent = safeMapped
                            )
                        }
                    }
                }
            }
        ).onSuccess {
            mappedViewEvent?.let { event -> prevViewEvent = event }
        }.submit()
    }

    // Returns true when a full ViewEvent must be sent instead of a diff:
    // - Periodic checkpoint (every FULL_VIEW_EVERY_N_UPDATES versions)
    // - View is closing (isActive transitions to false)
    private fun shouldWriteFullView(documentVersion: Long, isActive: Boolean?): Boolean {
        return documentVersion % FULL_VIEW_EVERY_N_UPDATES == 0L ||
            isActive == false
    }

    companion object {
        internal const val VIEW_EVENT_MAPPER_FALLBACK_WARNING_MESSAGE =
            "ViewEventMapper failed, using original ViewEvent."
        internal const val VIEW_EVENT_MAPPER_NOT_SAME_INSTANCE_WARNING_MESSAGE =
            "ViewEventMapper returned a different ViewEvent instance than the one passed in; " +
                "the original ViewEvent will be used instead."
        internal const val FULL_VIEW_EVERY_N_UPDATES = 4L
    }
}
