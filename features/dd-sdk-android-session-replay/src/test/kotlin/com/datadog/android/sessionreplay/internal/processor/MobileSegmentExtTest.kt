package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import java.util.Locale

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
internal class MobileSegmentExtTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    // region MobileSegment.Source

    @Test
    fun `M resolve the MobileSegment source W tryFromSource`(
        forge: Forge
    ) {
        // Given
        val fakeValidSource = forge.aValueFrom(MobileSegment.Source::class.java)

        // When
        val source = MobileSegment.Source.tryFromSource(fakeValidSource.toJson().asString, mockInternalLogger)

        // Then
        assertThat(source).isEqualTo(fakeValidSource)
    }

    @Test
    fun `M return default value W tryFromSource { unknown source }`(
        forge: Forge
    ) {
        // Given
        val fakeInvalidSource = forge.aString()

        // When
        val source = MobileSegment.Source.tryFromSource(fakeInvalidSource, mockInternalLogger)

        // Then
        assertThat(source).isEqualTo(MobileSegment.Source.ANDROID)
    }

    @Test
    fun `M send an error maintainer log W tryFromSource { unknown source }`(
        forge: Forge
    ) {
        // Given
        val fakeInvalidSource = forge.aString()

        // When
        MobileSegment.Source.tryFromSource(fakeInvalidSource, mockInternalLogger)

        // Then
        argumentCaptor<() -> String>() {
            verify(mockInternalLogger).log(
                level = eq(InternalLogger.Level.ERROR),
                target = eq(InternalLogger.Target.MAINTAINER),
                messageBuilder = capture(),
                throwable = isA<NoSuchElementException>(),
                onlyOnce = eq(false),
                additionalProperties = isNull()
            )

            assertThat(firstValue()).isEqualTo(
                UNKNOWN_MOBILE_SEGMENT_SOURCE_WARNING_MESSAGE_FORMAT.format(
                    Locale.US,
                    fakeInvalidSource
                )
            )
        }
    }

    // endregion
}
