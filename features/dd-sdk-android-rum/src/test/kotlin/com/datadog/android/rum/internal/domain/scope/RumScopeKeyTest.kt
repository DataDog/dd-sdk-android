@file:Suppress("DEPRECATION")

package com.datadog.android.rum.internal.domain.scope

import android.app.Activity
import android.content.ComponentName
import androidx.fragment.app.Fragment
import androidx.navigation.ActivityNavigator
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.navigation.fragment.FragmentNavigator
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import android.app.Fragment as LegacyFragment

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumScopeKeyTest {

    @Mock
    lateinit var mockComponentName: ComponentName

    @StringForgery(regex = "[a-z]+(\\.[a-z]+)+")
    lateinit var fakePackageName: String

    @StringForgery(StringForgeryType.ALPHABETICAL)
    lateinit var fakeSimpleClassName: String

    lateinit var fakeClassName: String

    @BeforeEach
    fun `set up`() {
        fakeClassName = "$fakePackageName.$fakeSimpleClassName"
        whenever(mockComponentName.className) doReturn fakeClassName
        whenever(mockComponentName.packageName) doReturn fakePackageName
        whenever(mockComponentName.shortClassName) doReturn ".$fakeSimpleClassName"
    }

    @Test
    fun `M create a key W from() {String}`(
        forge: Forge
    ) {
        // Given
        val input = forge.aString()
        val expectedId = input
        val expectedUrl = input
        val expectedName = input

        // When
        val key = RumScopeKey.from(input)

        // Then
        assertThat(key.id).isEqualTo(expectedId)
        assertThat(key.url).isEqualTo(expectedUrl)
        assertThat(key.name).isEqualTo(expectedName)
    }

    @Test
    fun `M create a key W from() {Number}`(
        forge: Forge
    ) {
        // Given
        val input = forge.anElementFrom(forge.anInt(), forge.aDouble(), forge.aFloat(), forge.aLong())
        val expectedId = input.toString()
        val expectedUrl = input.toString()
        val expectedName = input.toString()

        // When
        val key = RumScopeKey.from(input)

        // Then
        assertThat(key.id).isEqualTo(expectedId)
        assertThat(key.url).isEqualTo(expectedUrl)
        assertThat(key.name).isEqualTo(expectedName)
    }

    @Test
    fun `M create a key W from() {Enum}`(
        @Forgery value: StubEnum
    ) {
        // Given
        val input = value
        val expectedId = "${StubEnum::class.java.name}@" + input.name
        val expectedUrl = "${StubEnum::class.java.name}." + input.name
        val expectedName = input.name

        // When
        val key = RumScopeKey.from(input)

        // Then
        assertThat(key.id).isEqualTo(expectedId)
        assertThat(key.url).isEqualTo(expectedUrl)
        assertThat(key.name).isEqualTo(expectedName)
    }

    @Test
    fun `M create a key W from() {Activity}`() {
        // Given
        val input = mock<Activity>().apply {
            whenever(componentName) doReturn mockComponentName
        }
        val expectedId = fakeClassName + "@" + System.identityHashCode(input)
        val expectedUrl = fakeClassName
        val expectedName = fakeClassName

        // When
        val key = RumScopeKey.from(input)

        // Then
        assertThat(key.id).isEqualTo(expectedId)
        assertThat(key.url).isEqualTo(expectedUrl)
        assertThat(key.name).isEqualTo(expectedName)
    }

    @Test
    fun `M create a key W from() {Legacy Fragment}`() {
        // Given
        val input = StubLegacyFragment()
        val expectedId = input.toString()
        val expectedUrl = StubLegacyFragment::class.java.canonicalName
        val expectedName = StubLegacyFragment::class.java.name

        // When
        val key = RumScopeKey.from(input)

        // Then
        assertThat(key.id).isEqualTo(expectedId)
        assertThat(key.url).isEqualTo(expectedUrl)
        assertThat(key.name).isEqualTo(expectedName)
    }

    @Test
    fun `M create a key W from() {AndroidX Fragment}`() {
        // Given
        val input = StubFragment()
        val expectedId = input.toString()
        val expectedUrl = StubFragment::class.java.canonicalName
        val expectedName = StubFragment::class.java.name

        // When
        val key = RumScopeKey.from(input)

        // Then
        assertThat(key.id).isEqualTo(expectedId)
        assertThat(key.url).isEqualTo(expectedUrl)
        assertThat(key.name).isEqualTo(expectedName)
    }

    @Test
    fun `M create a key W from() {FragmentNavigator Destination}`(
        @StringForgery fakeClassName: String,
        @IntForgery fakeId: Int
    ) {
        // Given
        val input = org.mockito.kotlin.mock<FragmentNavigator.Destination>().apply {
            whenever(className) doReturn fakeClassName
            whenever(id) doReturn fakeId
        }
        val expectedId = "$fakeClassName#$fakeId"
        val expectedUrl = fakeClassName
        val expectedName = fakeClassName

        // When
        val key = RumScopeKey.from(input)

        // Then
        assertThat(key.id).isEqualTo(expectedId)
        assertThat(key.url).isEqualTo(expectedUrl)
        assertThat(key.name).isEqualTo(expectedName)
    }

    @Test
    fun `M create a key W from() {DialogFragmentNavigator Destination}`(
        @StringForgery fakeClassName: String,
        @IntForgery fakeId: Int
    ) {
        // Given
        val input = org.mockito.kotlin.mock<DialogFragmentNavigator.Destination>().apply {
            whenever(className) doReturn fakeClassName
            whenever(id) doReturn fakeId
        }
        val expectedId = "$fakeClassName#$fakeId"
        val expectedUrl = fakeClassName
        val expectedName = fakeClassName

        // When
        val key = RumScopeKey.from(input)

        // Then
        assertThat(key.id).isEqualTo(expectedId)
        assertThat(key.url).isEqualTo(expectedUrl)
        assertThat(key.name).isEqualTo(expectedName)
    }

    @Test
    fun `M create a key W from() {ActivityNavigator Destination}`() {
        // Given
        val input = org.mockito.kotlin.mock<ActivityNavigator.Destination>().apply {
            whenever(component) doReturn mockComponentName
        }
        val expectedId = fakeClassName + "@" + System.identityHashCode(input)
        val expectedUrl = fakeClassName
        val expectedName = fakeClassName

        // When
        val key = RumScopeKey.from(input)

        // Then
        assertThat(key.id).isEqualTo(expectedId)
        assertThat(key.url).isEqualTo(expectedUrl)
        assertThat(key.name).isEqualTo(expectedName)
    }

    @Test
    fun `M create a key W from() {unknown type}`() {
        // Given
        val input = StubObject()
        val expectedId = input.toString()
        val expectedUrl = StubObject::class.java.canonicalName
        val expectedName = StubObject::class.java.name

        // When
        val key = RumScopeKey.from(input)

        // Then
        assertThat(key.id).isEqualTo(expectedId)
        assertThat(key.url).isEqualTo(expectedUrl)
        assertThat(key.name).isEqualTo(expectedName)
    }

    class StubLegacyFragment : LegacyFragment() {
        @Deprecated("Deprecated in Java")
        override fun toString(): String {
            return "StubLegacyFragment{${System.identityHashCode(this)}}"
        }
    }

    class StubFragment : Fragment()

    class StubObject

    enum class StubEnum {
        VALUE_0,
        VALUE_1,
        VALUE_2,
        VALUE_3,
        VALUE_4,
        VALUE_5,
        VALUE_6,
        VALUE_7,
        VALUE_8,
        VALUE_9
    }
}
