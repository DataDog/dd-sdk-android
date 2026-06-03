/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.ActivityManager
import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import com.datadog.android.internal.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.quality.Strictness
import java.net.URI

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class DdRumContentProviderTest {

    lateinit var testedProvider: ContentProvider

    @BeforeEach
    fun `set up`() {
        testedProvider = DdRumContentProvider()
    }

    @AfterEach
    fun `tear down`() {
        DdRumContentProvider.processImportance = 0
    }

    // region onCreate

    @Test
    fun `M detect process importance W onCreate()`(
        @IntForgery fakeImportance: Int
    ) {
        // Given
        Mockito.mockStatic(ActivityManager::class.java).use { mock ->
            mock.`when`<Unit> { ActivityManager.getMyMemoryState(any()) }
                .thenAnswer { invocation ->
                    invocation
                        .getArgument<ActivityManager.RunningAppProcessInfo>(0)
                        .importance = fakeImportance
                    Unit
                }

            // When
            val response = testedProvider.onCreate()

            // Then
            assertThat(DdRumContentProvider.processImportance).isEqualTo(fakeImportance)
            assertThat(response).isTrue
        }
    }

    @Test
    fun `M detect process importance W onCreate() { exception is thrown }`() {
        // Given
        Mockito.mockStatic(ActivityManager::class.java).use { mock ->
            mock.`when`<Unit> { ActivityManager.getMyMemoryState(any()) }
                .thenThrow(RuntimeException())

            // When
            val response = testedProvider.onCreate()

            // Then
            assertThat(DdRumContentProvider.processImportance)
                // normally it is IMPORTANCE_FOREGROUND, but on JVM the real constructor is not called, so it will be 0
                .isEqualTo(0)
            assertThat(response).isTrue
        }
    }

    @Test
    fun `M detect process importance once W onCreate() twice`(
        @IntForgery(min = 0, max = 10) fakeImportance1: Int,
        @IntForgery(min = 10, max = 100) fakeImportance2: Int
    ) {
        // Given
        Mockito.mockStatic(ActivityManager::class.java).use { mock ->
            var callCount = 0
            mock.`when`<Unit> { ActivityManager.getMyMemoryState(any()) }
                .thenAnswer { invocation ->
                    callCount++
                    invocation.getArgument<ActivityManager.RunningAppProcessInfo>(0)
                        .importance = if (callCount == 1) fakeImportance1 else fakeImportance2
                    Unit
                }

            // When
            val response1 = testedProvider.onCreate()
            val response2 = testedProvider.onCreate()

            // Then
            assertThat(DdRumContentProvider.processImportance).isEqualTo(fakeImportance1)
            assertThat(response1).isTrue
            assertThat(response2).isTrue
        }
    }

    // endregion

    // region processImportance lazy fallback

    @Test
    fun `M read process importance from ActivityManager W processImportance {onCreate not yet called}`(
        @IntForgery fakeImportance: Int
    ) {
        // Given
        Mockito.mockStatic(ActivityManager::class.java).use { mock ->
            mock.`when`<Unit> { ActivityManager.getMyMemoryState(any()) }
                .thenAnswer { invocation ->
                    invocation.getArgument<ActivityManager.RunningAppProcessInfo>(0)
                        .importance = fakeImportance
                    Unit
                }

            // When
            val result = DdRumContentProvider.processImportance

            // Then
            assertThat(result).isEqualTo(fakeImportance)
        }
    }

    @Test
    fun `M cache process importance W processImportance {accessed twice before onCreate}`(
        @IntForgery(min = 0, max = 10) fakeImportance1: Int,
        @IntForgery(min = 10, max = 100) fakeImportance2: Int
    ) {
        // Given
        Mockito.mockStatic(ActivityManager::class.java).use { mock ->
            var callCount = 0
            mock.`when`<Unit> { ActivityManager.getMyMemoryState(any()) }
                .thenAnswer { invocation ->
                    callCount++
                    invocation.getArgument<ActivityManager.RunningAppProcessInfo>(0)
                        .importance = if (callCount == 1) fakeImportance1 else fakeImportance2
                    Unit
                }

            // When
            val first = DdRumContentProvider.processImportance
            val second = DdRumContentProvider.processImportance

            // Then
            assertThat(first).isEqualTo(fakeImportance1)
            assertThat(second).isEqualTo(fakeImportance1)
        }
    }

    // endregion

    // region ContentProvider

    @Test
    fun `M return null W query()`(
        @Forgery uri: URI,
        @StringForgery projection: List<String>,
        @StringForgery selection: String,
        @StringForgery selectionArgs: List<String>,
        @StringForgery sortOrder: String
    ) {
        // When
        val cursor = testedProvider.query(
            Uri.parse(uri.toString()),
            projection.toTypedArray(),
            selection,
            selectionArgs.toTypedArray(),
            sortOrder
        )

        // Then
        assertThat(cursor).isNull()
    }

    @Test
    fun `M return null W getType()`(
        @Forgery uri: URI
    ) {
        // When
        val type = testedProvider.getType(Uri.parse(uri.toString()))

        // Then
        assertThat(type).isNull()
    }

    @Test
    fun `M return null W insert()`(
        @Forgery uri: URI
    ) {
        // When
        val type = testedProvider.insert(
            Uri.parse(uri.toString()),
            ContentValues()
        )

        // Then
        assertThat(type).isNull()
    }

    @Test
    fun `M return 0 W delete()`(
        @Forgery uri: URI,
        @StringForgery selection: String,
        @StringForgery selectionArgs: List<String>
    ) {
        // When
        val deleted = testedProvider.delete(
            Uri.parse(uri.toString()),
            selection,
            selectionArgs.toTypedArray()
        )

        // Then
        assertThat(deleted).isZero()
    }

    @Test
    fun `M return 0 W update()`(
        @Forgery uri: URI,
        @StringForgery selection: String,
        @StringForgery selectionArgs: List<String>
    ) {
        // When
        val deleted = testedProvider.update(
            Uri.parse(uri.toString()),
            ContentValues(),
            selection,
            selectionArgs.toTypedArray()
        )

        // Then
        assertThat(deleted).isZero()
    }

    // endregion
}
