/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.view.View

internal class CapturedMapperTypeWrapper<T : View>(
    private val type: Class<T>,
    private val mapper: CapturedViewMapper<T>
) {
    // view::class.java is a non-null Kotlin type, so it can't be the null that would make this throw
    @Suppress("UnsafeThirdPartyFunctionCall")
    fun supportsView(view: View): Boolean = type.isAssignableFrom(view::class.java)

    @Suppress("UNCHECKED_CAST")
    fun getUnsafeMapper(): CapturedViewMapper<View> = mapper as CapturedViewMapper<View>
}
