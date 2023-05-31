/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.Forge

internal fun Forge.aRumEventAsJson(): JsonObject {
    return anElementFrom(
        this.getForgery<ViewEvent>().toJson().asJsonObject,
        this.getForgery<LongTaskEvent>().toJson().asJsonObject,
        this.getForgery<ActionEvent>().toJson().asJsonObject,
        this.getForgery<ResourceEvent>().toJson().asJsonObject,
        this.getForgery<ErrorEvent>().toJson().asJsonObject
    )
}
