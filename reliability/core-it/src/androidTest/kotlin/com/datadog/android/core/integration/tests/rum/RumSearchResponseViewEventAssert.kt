/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.rum

import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat

internal class RumSearchResponseViewEventAssert(actual: RumSearchResponse.ViewEvent) :
    AbstractAssert<RumSearchResponseViewEventAssert, RumSearchResponse.ViewEvent>(
        actual,
        RumSearchResponseViewEventAssert::class.java
    ) {

    fun hasViewName(name: String): RumSearchResponseViewEventAssert {
        assertThat(actual.attributes.attributes.view?.name)
            .overridingErrorMessage("Expected view name to be <%s> but was <%s>", name, actual.attributes.attributes.view?.name)
            .isEqualTo(name)
        return this
    }

    fun hasActionCount(count: Int): RumSearchResponseViewEventAssert {
        assertThat(actual.attributes.attributes.view?.action?.count)
            .overridingErrorMessage("Expected action count to be <%d> but was <%d>", count, actual.attributes.attributes.view?.action?.count)
            .isEqualTo(count)
        return this
    }

    fun hasErrorCount(count: Int): RumSearchResponseViewEventAssert {
        assertThat(actual.attributes.attributes.view?.error?.count)
            .overridingErrorMessage("Expected error count to be <%d> but was <%d>", count, actual.attributes.attributes.view?.error?.count)
            .isEqualTo(count)
        return this
    }

    fun hasLongTaskCount(count: Int): RumSearchResponseViewEventAssert {
        assertThat(actual.attributes.attributes.view?.longTask?.count)
            .overridingErrorMessage("Expected long task count to be <%d> but was <%d>", count, actual.attributes.attributes.view?.longTask?.count)
            .isEqualTo(count)
        return this
    }

    fun hasResourceCount(count: Int): RumSearchResponseViewEventAssert {
        assertThat(actual.attributes.attributes.view?.resource?.count)
            .overridingErrorMessage("Expected resource count to be <%d> but was <%d>", count, actual.attributes.attributes.view?.resource?.count)
            .isEqualTo(count)
        return this
    }

    fun isActive(): RumSearchResponseViewEventAssert {
        assertThat(actual.attributes.attributes.view?.isActive)
            .overridingErrorMessage("Expected view to be active but it was not")
            .isTrue()
        return this
    }

    fun isNotActive(): RumSearchResponseViewEventAssert {
        assertThat(actual.attributes.attributes.view?.isActive)
            .overridingErrorMessage("Expected view to be inactive but it was not")
            .isFalse()
        return this
    }

    fun hasApplicationId(applicationId: String): RumSearchResponseViewEventAssert {
        assertThat(actual.attributes.attributes.application?.id)
            .overridingErrorMessage("Expected application id to be <%s> but was <%s>", applicationId, actual.attributes.attributes.application?.id)
            .isEqualTo(applicationId)
        return this
    }

    fun hasSessionId(sessionId: String): RumSearchResponseViewEventAssert {
        assertThat(actual.attributes.attributes.session?.id)
            .overridingErrorMessage("Expected session id to be <%s> but was <%s>", sessionId, actual.attributes.attributes.session?.id)
            .isEqualTo(sessionId)
        return this
    }

    fun hasService(service: String): RumSearchResponseViewEventAssert {
        assertThat(actual.attributes.service)
            .overridingErrorMessage("Expected service to be <%s> but was <%s>", service, actual.attributes.service)
            .isEqualTo(service)
        return this
    }

    companion object {
        fun assertThat(actual: RumSearchResponse.ViewEvent) = RumSearchResponseViewEventAssert(actual)
    }
}
