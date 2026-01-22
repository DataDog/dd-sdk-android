/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.assertj

import com.datadog.android.profiling.model.ProfileEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class ProfileEventAssert(actual: ProfileEvent) :
    AbstractObjectAssert<ProfileEventAssert, ProfileEvent>(
        actual,
        ProfileEventAssert::class.java
    ) {

    fun hasStart(expected: String): ProfileEventAssert {
        assertThat(actual.start)
            .overridingErrorMessage(
                "Expected event data to have start $expected " +
                    "but was ${actual.start}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasEnd(expected: String): ProfileEventAssert {
        assertThat(actual.end)
            .overridingErrorMessage(
                "Expected event data to have end $expected " +
                    "but was ${actual.end}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasAttachments(expected: List<String>): ProfileEventAssert {
        assertThat(actual.attachments)
            .overridingErrorMessage(
                "Expected event data to have attachments $expected " +
                    "but was ${actual.attachments}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasFamily(expected: String): ProfileEventAssert {
        assertThat(actual.family)
            .overridingErrorMessage(
                "Expected event data to have family $expected " +
                    "but was ${actual.family}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasRuntime(expected: String): ProfileEventAssert {
        assertThat(actual.runtime)
            .overridingErrorMessage(
                "Expected event data to have runtime $expected " +
                    "but was ${actual.runtime}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasVersion(expected: String): ProfileEventAssert {
        assertThat(actual.version)
            .overridingErrorMessage(
                "Expected event data to have version $expected " +
                    "but was ${actual.version}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasTags(expected: List<String>): ProfileEventAssert {
        assertThat(actual.tagsProfiler.split(","))
            .overridingErrorMessage(
                "Expected event data to have tags_profiler $expected " +
                    "but was ${actual.tagsProfiler}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasApplicationId(expected: String): ProfileEventAssert {
        assertThat(actual.application.id)
            .overridingErrorMessage(
                "Expected event data to have application.id $expected " +
                    "but was ${actual.application.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionId(expected: String): ProfileEventAssert {
        assertThat(actual.session.id)
            .overridingErrorMessage(
                "Expected event data to have session.id $expected " +
                    "but was ${actual.session.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasVitalId(expected: String): ProfileEventAssert {
        assertThat(actual.vital.id)
            .overridingErrorMessage(
                "Expected event data to have vital.id $expected " +
                    "but was ${actual.vital.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasViewId(expected: String?): ProfileEventAssert {
        assertThat(actual.view?.id)
            .overridingErrorMessage(
                "Expected event data to have view.id $expected " +
                    "but was ${actual.view?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasViewName(expected: String?): ProfileEventAssert {
        assertThat(actual.view?.name)
            .overridingErrorMessage(
                "Expected event data to have view.name $expected " +
                    "but was ${actual.view?.name}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {
        internal fun assertThat(actual: ProfileEvent) = ProfileEventAssert(actual)
    }
}
