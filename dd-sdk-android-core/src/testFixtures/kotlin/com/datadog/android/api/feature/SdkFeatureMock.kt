/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.feature

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.core.internal.SdkFeature
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.Future

object SdkFeatureMock {
    /**
     * This method is a trick that allows to mock FeatureScope.getContextFuture extension method.
     */
    fun create(future: Future<DatadogContext?>? = null): FeatureScope = mock<SdkFeature> {
        on { getContextFuture(any()) } doReturn future
    }
}
