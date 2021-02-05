/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.internal.domain.event.RumEvent
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumEventAssert(actual: RumEvent) :
    AbstractObjectAssert<RumEventAssert, RumEvent>(
        actual,
        RumEventAssert::class.java
    ) {

    fun hasAttributes(attributes: Map<String, Any?>): RumEventAssert {
        assertThat(actual.globalAttributes)
            .containsAllEntriesOf(attributes)
        return this
    }

    fun hasUserExtraAttributes(attributes: Map<String, Any?>): RumEventAssert {
        assertThat(actual.userExtraAttributes)
            .containsAllEntriesOf(attributes)
        return this
    }

    fun hasViewData(assert: ViewEventAssert.() -> Unit): RumEventAssert {
        assertThat(actual.event)
            .isInstanceOf(ViewEvent::class.java)

        ViewEventAssert(actual.event as ViewEvent).assert()

        return this
    }

    fun hasResourceData(assert: ResourceEventAssert.() -> Unit): RumEventAssert {
        assertThat(actual.event)
            .isInstanceOf(ResourceEvent::class.java)

        ResourceEventAssert(actual.event as ResourceEvent).assert()

        return this
    }

    fun hasActionData(assert: ActionEventAssert.() -> Unit): RumEventAssert {
        assertThat(actual.event)
            .isInstanceOf(ActionEvent::class.java)

        ActionEventAssert(actual.event as ActionEvent).assert()

        return this
    }

    fun hasErrorData(assert: ErrorEventAssert.() -> Unit): RumEventAssert {
        assertThat(actual.event)
            .isInstanceOf(ErrorEvent::class.java)

        ErrorEventAssert(actual.event as ErrorEvent).assert()

        return this
    }

    companion object {

        internal const val TIMESTAMP_THRESHOLD_MS = 50L

        internal fun assertThat(actual: RumEvent): RumEventAssert =
            RumEventAssert(actual)
    }
}
