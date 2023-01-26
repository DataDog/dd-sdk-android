package com.datadog.android.sessionreplay.internal

import com.datadog.android.DatadogEndpoint
import com.datadog.android.DatadogSite
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(value = Configurator::class)
internal class SessionReplayConfigurationBuilderTest {

    lateinit var testedBuilder: SessionReplayConfiguration.Builder

    @BeforeEach
    fun `set up`() {
        testedBuilder = SessionReplayConfiguration.Builder()
    }

    @Test
    fun `𝕄 use sensible defaults 𝕎 build()`() {
        // When
        val config = testedBuilder.build()

        // Then
        assertThat(config.endpointUrl).isEqualTo(DatadogEndpoint.SESSION_REPLAY_US1)
        assertThat(config.privacy).isEqualTo(SessionReplayPrivacy.MASK_ALL)
    }

    @Test
    fun `𝕄 build config with custom site 𝕎 useSite() and build()`(
        @Forgery site: DatadogSite
    ) {
        // When
        val config = testedBuilder.useSite(site).build()

        // Then
        assertThat(config.endpointUrl).isEqualTo(site.sessionReplayEndpoint())
    }

    @Test
    fun `𝕄 build config with custom site 𝕎 useCustomEndpoint() and build()`(
        @StringForgery(regex = "https://[a-z]+\\.com") sessionReplayUrl: String
    ) {
        // When
        val config = testedBuilder.useCustomEndpoint(sessionReplayUrl).build()

        // Then
        assertThat(config.endpointUrl).isEqualTo(sessionReplayUrl)
    }

    @Test
    fun `𝕄 use the given privacy rule 𝕎 setSessionReplayPrivacy`(
        @Forgery fakePrivacy: SessionReplayPrivacy
    ) {
        // When
        val config = testedBuilder.setPrivacy(fakePrivacy).build()

        // Then
        assertThat(config.privacy).isEqualTo(fakePrivacy)
    }
}
