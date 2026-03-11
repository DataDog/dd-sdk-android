/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.rum

import org.assertj.core.api.Assertions.assertThat
import com.datadog.android.rum.model.ViewEvent

internal interface ViewEventsVerifier {
    fun <T1, T2, T3> verifyEquals(
        viewEvent: ViewEvent.() -> T1,
        backendView: RumSearchResponse.ViewEvent.() -> T2,
        value: T3
    )
}

internal fun verifyViewEvents(viewEvent: ViewEvent, backendEvent: RumSearchResponse.ViewEvent, block: ViewEventsVerifier.() -> Unit) {
    val verifier = ViewEventsVerifierImpl(viewEventObj = viewEvent, backendEventObj = backendEvent)
    block(verifier)
}

private class ViewEventsVerifierImpl(
    val viewEventObj: ViewEvent,
    val backendEventObj: RumSearchResponse.ViewEvent
): ViewEventsVerifier {
    override fun <T1, T2, T3> verifyEquals(
        viewEvent: ViewEvent.() -> T1,
        backendView: RumSearchResponse.ViewEvent.() -> T2,
        value: T3
    ) {
        val viewField = viewEvent(viewEventObj)
        val backendField = backendView(backendEventObj)

        assertThat(viewField).isEqualTo(backendField)
    }
}
