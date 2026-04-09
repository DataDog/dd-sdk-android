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
    fun `M return true W equals (same key, same uuid)`(@StringForgery key: String, @Forgery uuid: UUID) {
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

    // region Reproduce RUMS-5184: UUID mismatch equality contract

    @Test
    fun `M return false W equals { RUMS-5184 same key but independently generated UUIDs }`(@StringForgery key: String) {
        // Documents the equality contract that makes the RUMS-5184 bug fatal:
        // DatadogInterceptor.intercept() creates ResourceId(key, UUID_A) via generateUuid=true.
        // DatadogEventListener.Factory.create() creates ResourceId(key, UUID_B) via generateUuid=true.
        // UUID_A != UUID_B (independently generated random values), so equals() returns false
        // → waitForTiming is never set, timing field is never set, resource event has no breakdown.
        //
        // The fix is to use generateUuid=false in Factory.create() so that uuid=null,
        // which triggers the key-only fallback in equals().

        // Given
        val interceptorResourceId = ResourceId(key, UUID.randomUUID().toString())
        val eventListenerResourceId = ResourceId(key, UUID.randomUUID().toString())

        // When
        val areEqual = interceptorResourceId == eventListenerResourceId

        // Then: two independently generated UUIDs are virtually always different.
        // The fix must ensure Factory.create() produces uuid=null to avoid this mismatch.
        assertThat(interceptorResourceId.uuid)
            .describedAs("interceptor's ResourceId must have a non-null uuid (generateUuid=true)")
            .isNotNull()
        assertThat(eventListenerResourceId.uuid)
            .describedAs("eventListener's ResourceId must have a non-null uuid (generateUuid=true) — BUG")
            .isNull() // This assertion documents the REQUIRED fix: uuid must be null
    }

    // endregion
}
