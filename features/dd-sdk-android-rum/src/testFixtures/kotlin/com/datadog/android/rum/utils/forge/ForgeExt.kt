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
import java.util.Locale

/**
 * Will generate an alphaNumericalString which is not matching any values provided in the set.
 */
fun Forge.aStringNotMatchingSet(set: Set<String>): String {
    var aString = anAlphaNumericalString()
    while (set.contains(aString)) {
        aString = anAlphaNumericalString()
    }
    return aString
}

fun Forge.aRumEvent(): Any {
    return this.anElementFrom(
        this.getForgery<ViewEvent>(),
        this.getForgery<ActionEvent>(),
        this.getForgery<ResourceEvent>(),
        this.getForgery<ErrorEvent>(),
        this.getForgery<LongTaskEvent>()
    )
}

fun Forge.ddTagsString(): String {
    val service = anAlphabeticalString()
    val version = aStringMatching("[0-9](\\.[0-9]{1,3}){2,3}")
    val variant = anElementFrom("", anAlphabeticalString())
    val env = anAlphabeticalString().lowercase(Locale.US)
    val sdkVersion = aStringMatching("[0-9](\\.[0-9]{1,2}){1,3}")

    return buildList {
        add("service" to service)
        add("version" to version)
        if (variant.isNotEmpty()) {
            add("variant" to variant)
        }
        add("env" to env)
        add("sdk_version" to sdkVersion)
    }.joinToString(",") { "${it.first}:${it.second}" }
}

fun Forge.useCommonRumFactories() {
    addFactory(ViewEventForgeryFactory())
    addFactory(ResourceEventForgeryFactory())
    addFactory(ActionEventForgeryFactory())
    addFactory(ErrorEventForgeryFactory())
    addFactory(LongTaskEventForgeryFactory())
    addFactory(ResourceTimingForgeryFactory())
    addFactory(AccessibilityForgeryFactory())
    addFactory(AccessibilityInfoForgeryFactory())
}
