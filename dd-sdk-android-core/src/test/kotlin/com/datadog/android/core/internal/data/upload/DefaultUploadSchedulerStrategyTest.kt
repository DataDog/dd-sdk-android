package com.datadog.android.core.internal.data.upload

import com.datadog.android.core.configuration.UploadSchedulerStrategy
import com.datadog.android.core.internal.configuration.DataUploadConfiguration
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.io.IOException

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DefaultUploadSchedulerStrategyTest {

    lateinit var testedStrategy: UploadSchedulerStrategy

    @Forgery
    lateinit var fakeConfiguration: DataUploadConfiguration

    @StringForgery
    lateinit var fakeFeatureName: String

    var initialDelay = 0L

    @BeforeEach
    fun `set up`() {
        testedStrategy = DefaultUploadSchedulerStrategy(fakeConfiguration)
        initialDelay = testedStrategy.getMsDelayUntilNextUpload(fakeFeatureName, 0, null, null)
    }

    @Test
    fun `M decrease delay W getMsDelayUntilNextUpload() {successful attempt}`(
        @IntForgery(1, 128) repeats: Int,
        @IntForgery(1, 64) attempts: Int
    ) {
        // Given
        var delay = 0L

        // When
        repeat(repeats) { delay = testedStrategy.getMsDelayUntilNextUpload(fakeFeatureName, attempts, 202, null) }

        // Then
        assertThat(delay).isLessThan(initialDelay)
        assertThat(delay).isGreaterThanOrEqualTo(fakeConfiguration.minDelayMs)
    }

    @Test
    fun `M increase delay W getMsDelayUntilNextUpload() {no attempt made}`(
        @IntForgery(1, 128) repeats: Int
    ) {
        // Given
        var delay = 0L

        // When
        repeat(repeats) { delay = testedStrategy.getMsDelayUntilNextUpload(fakeFeatureName, 0, null, null) }

        // Then
        assertThat(delay).isGreaterThan(initialDelay)
        assertThat(delay).isLessThanOrEqualTo(fakeConfiguration.maxDelayMs)
    }

    @Test
    fun `M increase delay W getMsDelayUntilNextUpload() {invalid status code}`(
        @IntForgery(1, 128) repeats: Int,
        @IntForgery(1, 64) attempts: Int,
        @IntForgery(300, 600) statusCode: Int
    ) {
        // Given
        var delay = 0L

        // When
        repeat(repeats) {
            delay = testedStrategy.getMsDelayUntilNextUpload(fakeFeatureName, attempts, statusCode, null)
        }

        // Then
        assertThat(delay).isGreaterThan(initialDelay)
        assertThat(delay).isLessThanOrEqualTo(fakeConfiguration.maxDelayMs)
    }

    @Test
    fun `M increase delay W getMsDelayUntilNextUpload() {non IOException}`(
        @IntForgery(1, 128) repeats: Int,
        @IntForgery(1, 64) attempts: Int,
        @Forgery exception: Exception
    ) {
        // Given
        var delay = 0L

        // When
        repeat(repeats) { delay = testedStrategy.getMsDelayUntilNextUpload(fakeFeatureName, attempts, null, exception) }

        // Then
        assertThat(delay).isGreaterThan(initialDelay)
        assertThat(delay).isLessThanOrEqualTo(fakeConfiguration.maxDelayMs)
    }

    @Test
    fun `M increase delay to high value W getMsDelayUntilNextUpload() {IOException}`(
        @IntForgery(1, 128) repeats: Int,
        @IntForgery(1, 64) attempts: Int,
        @StringForgery message: String
    ) {
        // Given
        var delay = 0L
        val exception = IOException(message)

        // When
        repeat(repeats) { delay = testedStrategy.getMsDelayUntilNextUpload(fakeFeatureName, attempts, null, exception) }

        // Then
        assertThat(delay).isEqualTo(DefaultUploadSchedulerStrategy.NETWORK_ERROR_DELAY_MS)
    }
}
