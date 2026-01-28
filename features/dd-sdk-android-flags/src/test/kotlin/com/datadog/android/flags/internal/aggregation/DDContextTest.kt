/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DDContextTest {

    @StringForgery
    lateinit var fakeService: String

    @StringForgery
    lateinit var fakeApplicationId: String

    @StringForgery
    lateinit var fakeViewId: String

    @StringForgery
    lateinit var fakeViewName: String

    @Test
    fun `M extract all fields W fromFeatureContext() { full context }`() {
        // Given
        val featureContext = mapOf(
            "application_id" to fakeApplicationId,
            "view_id" to fakeViewId,
            "view_name" to fakeViewName
        )

        // When
        val context = DDContext.fromFeatureContext(featureContext, fakeService)

        // Then
        assertThat(context.service).isEqualTo(fakeService)
        assertThat(context.applicationId).isEqualTo(fakeApplicationId)
        assertThat(context.viewId).isEqualTo(fakeViewId)
        assertThat(context.viewName).isEqualTo(fakeViewName)
    }

    @Test
    fun `M extract null for missing fields W fromFeatureContext() { empty context }`() {
        // Given
        val featureContext = emptyMap<String, Any?>()

        // When
        val context = DDContext.fromFeatureContext(featureContext, fakeService)

        // Then
        assertThat(context.service).isEqualTo(fakeService)
        assertThat(context.applicationId).isNull()
        assertThat(context.viewId).isNull()
        assertThat(context.viewName).isNull()
    }

    @Test
    fun `M extract partial fields W fromFeatureContext() { partial context }`() {
        // Given - only some fields present
        val featureContext = mapOf(
            "application_id" to fakeApplicationId,
            "view_id" to fakeViewId
            // No view_name
        )

        // When
        val context = DDContext.fromFeatureContext(featureContext, fakeService)

        // Then
        assertThat(context.service).isEqualTo(fakeService)
        assertThat(context.applicationId).isEqualTo(fakeApplicationId)
        assertThat(context.viewId).isEqualTo(fakeViewId)
        assertThat(context.viewName).isNull()
    }

    @Test
    fun `M handle null service W fromFeatureContext() { service not available }`() {
        // Given
        val featureContext = mapOf(
            "application_id" to fakeApplicationId
        )

        // When
        val context = DDContext.fromFeatureContext(featureContext, service = null)

        // Then
        assertThat(context.service).isNull()
        assertThat(context.applicationId).isEqualTo(fakeApplicationId)
    }
}
