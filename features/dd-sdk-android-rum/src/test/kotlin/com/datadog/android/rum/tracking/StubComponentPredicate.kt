/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import fr.xgouchet.elmyr.Forge

internal data class StubComponentPredicate<T : Any>(
    val componentName: String,
    val name: String
) : ComponentPredicate<T> {

    constructor(forge: Forge, useAlpha: Boolean = true) :
        this(
            if (useAlpha) forge.anAlphabeticalString() else forge.aNumericalString(),
            if (useAlpha) forge.anAlphabeticalString() else forge.aNumericalString()
        )

    override fun accept(component: T): Boolean {
        return component.javaClass.simpleName == componentName
    }

    override fun getViewName(component: T): String? {
        return name
    }
}
