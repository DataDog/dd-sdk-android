package com.datadog.android.rum.resource

import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.UUID

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class ResourceIdTest {

    @Test
    fun `M return true W equals (same key, same uuid)`(
        @StringForgery key: String,
        @Forgery uuid: UUID
    ) {
        // Given
        val requestId = ResourceId(key, uuid.toString())
        val otherRequestId = ResourceId(key, uuid.toString())

        // When
        val areEqual = requestId == otherRequestId
        val areHashCodeEqual = requestId.hashCode() == otherRequestId.hashCode()

        // Then
        assertThat(areEqual).isTrue()
        assertThat(areHashCodeEqual).isTrue()
    }

    @Test
    fun `M return false W equals (different key, same uuid)`(
        @StringForgery key: String,
        @StringForgery otherKey: String,
        @Forgery uuid: UUID
    ) {
        // Given
        val requestId = ResourceId(key, uuid.toString())
        val otherRequestId = ResourceId(otherKey, uuid.toString())

        // When
        val areEqual = requestId == otherRequestId
        val areHashCodeEqual = requestId.hashCode() == otherRequestId.hashCode()

        // Then
        assertThat(areEqual).isFalse()
        assertThat(areHashCodeEqual).isFalse()
    }

    @Test
    fun `M return false W equals (same key, different uuid)`(
        @StringForgery key: String,
        @Forgery uuid: UUID,
        @Forgery otherUuid: UUID
    ) {
        // Given
        val requestId = ResourceId(key, uuid.toString())
        val otherRequestId = ResourceId(key, otherUuid.toString())

        // When
        val areEqual = requestId == otherRequestId
        val areHashCodeEqual = requestId.hashCode() == otherRequestId.hashCode()

        // Then
        assertThat(areEqual).isFalse()
        assertThat(areHashCodeEqual).isTrue()
    }

    @Test
    fun `M return false W equals (different key, different uuid)`(
        @StringForgery key: String,
        @StringForgery otherKey: String,
        @Forgery uuid: UUID,
        @Forgery otherUuid: UUID
    ) {
        // Given
        val requestId = ResourceId(key, uuid.toString())
        val otherRequestId = ResourceId(otherKey, otherUuid.toString())

        // When
        val areEqual = requestId == otherRequestId
        val areHashCodeEqual = requestId.hashCode() == otherRequestId.hashCode()

        // Then
        assertThat(areEqual).isFalse()
        assertThat(areHashCodeEqual).isFalse()
    }

    @Test
    fun `M return true W equals (same key, different uuid one is null)`(
        @StringForgery key: String,
        @Forgery uuid: UUID
    ) {
        // Given
        val requestId = ResourceId(key, uuid.toString())
        val otherRequestId = ResourceId(key, null)

        // When
        val areEqual = requestId == otherRequestId
        val areHashCodeEqual = requestId.hashCode() == otherRequestId.hashCode()

        // Then
        assertThat(areEqual).isTrue()
        assertThat(areHashCodeEqual).isTrue()
    }

    @Test
    fun `M return false W equals (different key, different uuid one is null)`(
        @StringForgery key: String,
        @Forgery uuid: UUID,
        @StringForgery otherKey: String
    ) {
        // Given
        val requestId = ResourceId(key, uuid.toString())
        val otherRequestId = ResourceId(otherKey, null)

        // When
        val areEqual = requestId == otherRequestId
        val areHashCodeEqual = requestId.hashCode() == otherRequestId.hashCode()

        // Then
        assertThat(areEqual).isFalse()
        assertThat(areHashCodeEqual).isFalse()
    }

    @Test
    fun `M return true W equals (same key, different uuid one is blank)`(
        @StringForgery key: String,
        @Forgery uuid: UUID,
        @StringForgery(StringForgeryType.WHITESPACE) blankUuid: String
    ) {
        // Given
        val requestId = ResourceId(key, uuid.toString())
        val otherRequestId = ResourceId(key, blankUuid)

        // When
        val areEqual = requestId == otherRequestId
        val areHashCodeEqual = requestId.hashCode() == otherRequestId.hashCode()

        // Then
        assertThat(areEqual).isTrue()
        assertThat(areHashCodeEqual).isTrue()
    }

    @Test
    fun `M return false W equals (different key, different uuid one is blank)`(
        @StringForgery key: String,
        @StringForgery otherKey: String,
        @Forgery uuid: UUID,
        @StringForgery(StringForgeryType.WHITESPACE) blankUuid: String
    ) {
        // Given
        val requestId = ResourceId(key, uuid.toString())
        val otherRequestId = ResourceId(otherKey, blankUuid)

        // When
        val areEqual = requestId == otherRequestId
        val areHashCodeEqual = requestId.hashCode() == otherRequestId.hashCode()

        // Then
        assertThat(areEqual).isFalse()
        assertThat(areHashCodeEqual).isFalse()
    }
}
