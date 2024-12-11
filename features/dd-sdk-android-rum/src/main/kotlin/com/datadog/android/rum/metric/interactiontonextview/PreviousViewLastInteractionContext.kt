/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.metric.interactiontonextview

import com.datadog.android.rum.model.ActionEvent

/**
 * Represents the context of the last interaction in the previous view.
 * @property actionType The type of the last interaction.
 * @property eventCreatedAtNanos The timestamp (in nanoseconds) when the event was created in the previous view.
 * @property currentViewCreationTimestamp The timestamp (in nanoseconds) when the current view was created,
 * or null if not applicable.
 */
data class PreviousViewLastInteractionContext(
    val actionType: ActionEvent.ActionEventActionType,
    val eventCreatedAtNanos: Long,
    val currentViewCreationTimestamp: Long?
)
