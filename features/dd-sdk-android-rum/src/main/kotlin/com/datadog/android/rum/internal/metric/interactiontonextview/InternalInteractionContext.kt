/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.metric.interactiontonextview

import com.datadog.android.rum.model.ActionEvent

internal data class InternalInteractionContext(
    internal val viewId: String,
    internal val actionType: ActionEvent.ActionEventActionType,
    internal val eventCreatedAtNanos: Long
)
