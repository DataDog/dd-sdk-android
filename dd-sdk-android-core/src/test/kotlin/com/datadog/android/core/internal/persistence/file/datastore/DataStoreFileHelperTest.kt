/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.datastore

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.persistence.datastore.DataStoreFileHelper
import com.datadog.android.core.internal.persistence.datastore.DataStoreFileHelper.Companion.INVALID_DATASTORE_KEY_FORMAT_EXCEPTION
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DataStoreFileHelperTest {
    private lateinit var testedFileHelper: DataStoreFileHelper

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun setup() {
        testedFileHelper = DataStoreFileHelper(
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M log correct exception W logInvalidKeyException()`() {
        // When
        testedFileHelper.logInvalidKeyException()

        // Then
        mockInternalLogger.verifyLog(
            level = InternalLogger.Level.WARN,
            target = InternalLogger.Target.MAINTAINER,
            message = INVALID_DATASTORE_KEY_FORMAT_EXCEPTION
        )
    }

    @Test
    fun `M return true W isKeyInvalid() { invalid key }`(
        @StringForgery(regex = "[^a-zA-Z0-9]") fakeKey: String
    ) {
        // When
        val result = testedFileHelper.isKeyInvalid("$fakeKey/$fakeKey")

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W isKeyInvalid() { valid key }`(
        @StringForgery(regex = "[^a-zA-Z0-9]") fakeKey: String
    ) {
        // When
        val result = testedFileHelper.isKeyInvalid(fakeKey)

        // Then
        assertThat(result).isFalse()
    }
}
