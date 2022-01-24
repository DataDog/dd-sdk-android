/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.app.Activity
import com.datadog.tools.unit.ObjectTest
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class AcceptAllActivitiesTest : ObjectTest<AcceptAllActivities>() {

    override fun createInstance(forge: Forge): AcceptAllActivities {
        return AcceptAllActivities()
    }

    override fun createEqualInstance(
        source: AcceptAllActivities,
        forge: Forge
    ): AcceptAllActivities {
        return AcceptAllActivities()
    }

    override fun createUnequalInstance(
        source: AcceptAllActivities,
        forge: Forge
    ): AcceptAllActivities? {
        return null
    }

    @Test
    fun `M return true W accept()`() {
        // Given
        val fakeActivity = mock<Activity>()
        val testedPredicate = AcceptAllActivities()

        // When
        val result = testedPredicate.accept(fakeActivity)

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return null W getViewName()`() {
        // Given
        val fakeActivity = mock<Activity>()
        val testedPredicate = AcceptAllActivities()

        // When
        val result = testedPredicate.getViewName(fakeActivity)

        // Then
        assertThat(result).isNull()
    }
}
