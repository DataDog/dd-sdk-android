/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import fr.xgouchet.elmyr.Forge

/**
 * Will generate an alphaNumericalString which is not matching any values provided in the set.
 */
internal fun Forge.aStringNotMatchingSet(set: Set<String>): String {
    var aString = anAlphaNumericalString()
    while (set.contains(aString)) {
        aString = anAlphaNumericalString()
    }
    return aString
}

internal fun Forge.aRumEvent(): Any {
    return this.anElementFrom(
        this.getForgery<ViewEvent>(),
        this.getForgery<ActionEvent>(),
        this.getForgery<ResourceEvent>(),
        this.getForgery<ErrorEvent>(),
        this.getForgery<LongTaskEvent>()
    )
}
