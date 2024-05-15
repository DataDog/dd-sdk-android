/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever

internal abstract class AbstractWireframeMapperTest<V : View, WM : WireframeMapper<V>> {

    lateinit var testedWireframeMapper: WM

    lateinit var mockMappedView: V

    @Mock
    lateinit var mockViewIdentifierResolver: ViewIdentifierResolver

    @Mock
    lateinit var mockColorStringFormatter: ColorStringFormatter

    @Mock
    lateinit var mockViewBoundsResolver: ViewBoundsResolver

    @Mock
    lateinit var mockDrawableToColorMapper: DrawableToColorMapper

    @Mock
    lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeMappingContext: MappingContext

    @Forgery
    lateinit var fakeViewGlobalBounds: GlobalBounds

    @FloatForgery(0f, 1f)
    var fakeViewAlpha: Float = 1f

    @LongForgery
    var fakeViewIdentifier: Long = 0L

    // region MappingContext

    fun withPrivacy(privacy: SessionReplayPrivacy) {
        fakeMappingContext = fakeMappingContext.copy(privacy = privacy)
    }

    fun withSystemThemeColor(themeColor: String?) {
        fakeMappingContext = fakeMappingContext.copy(
            systemInformation = fakeMappingContext.systemInformation.copy(themeColor = themeColor)
        )
    }

    // endregion

    // region ViewIdentifierResolver

    fun mockChildUniqueIdentifier(childName: String, identifier: Long?) {
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(mockMappedView, childName)) doReturn identifier
    }

    // endregion

    // region View

    inline fun <reified MV : V> prepareMockView(configureMock: (MV) -> Unit = {}) {
        val mock: MV = mock()

        whenever(mock.alpha) doReturn fakeViewAlpha

        configureMock(mock)

        // generic mock stubbing
        val fakeDensity = fakeMappingContext.systemInformation.screenDensity
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mock, fakeDensity)) doReturn fakeViewGlobalBounds
        whenever(mockViewIdentifierResolver.resolveViewId(mock)) doReturn fakeViewIdentifier

        mockMappedView = mock
    }

    // endregion
}
