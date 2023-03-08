/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.utils.asTimingsPayload
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class ExternalResourceTimingsKtTest {

    @Test
    fun `𝕄 create timings 𝕎 extractResourceTiming`(@Forgery reference: ResourceTiming) {
        // Given
        val timingsPayload = reference.asTimingsPayload()

        // When
        val timings = extractResourceTiming(timingsPayload)

        // Then
        assertThat(timings).isNotNull

        timings!!.let {
            assertThat(it.connectStart).isEqualTo(timingsPayload.startTimeOf("connect"))
            assertThat(it.connectDuration).isEqualTo(timingsPayload.durationOf("connect"))
            assertThat(it.downloadStart).isEqualTo(timingsPayload.startTimeOf("download"))
            assertThat(it.downloadDuration).isEqualTo(timingsPayload.durationOf("download"))
            assertThat(it.dnsStart).isEqualTo(timingsPayload.startTimeOf("dns"))
            assertThat(it.dnsDuration).isEqualTo(timingsPayload.durationOf("dns"))
            assertThat(it.sslStart).isEqualTo(timingsPayload.startTimeOf("ssl"))
            assertThat(it.sslDuration).isEqualTo(timingsPayload.durationOf("ssl"))
            assertThat(it.firstByteStart).isEqualTo(timingsPayload.startTimeOf("firstByte"))
            assertThat(it.firstByteDuration).isEqualTo(timingsPayload.durationOf("firstByte"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["ssl", "firstByte", "download", "connect", "dns"])
    fun `𝕄 create timings 𝕎 extractResourceTiming { some timing is missing }`(
        missingTiming: String,
        @Forgery timing: ResourceTiming
    ) {
        // Given
        val timingsPayload = timing.asTimingsPayload()
        timingsPayload.remove(missingTiming)

        // When
        val timings = extractResourceTiming(timingsPayload)

        // Then
        assertThat(timings).isNotNull

        timings!!.let {
            assertThat(it.connectStart).isEqualTo(timingsPayload.startTimeOf("connect"))
            assertThat(it.connectDuration).isEqualTo(timingsPayload.durationOf("connect"))
            assertThat(it.downloadStart).isEqualTo(timingsPayload.startTimeOf("download"))
            assertThat(it.downloadDuration).isEqualTo(timingsPayload.durationOf("download"))
            assertThat(it.dnsStart).isEqualTo(timingsPayload.startTimeOf("dns"))
            assertThat(it.dnsDuration).isEqualTo(timingsPayload.durationOf("dns"))
            assertThat(it.sslStart).isEqualTo(timingsPayload.startTimeOf("ssl"))
            assertThat(it.sslDuration).isEqualTo(timingsPayload.durationOf("ssl"))
            assertThat(it.firstByteStart).isEqualTo(timingsPayload.startTimeOf("firstByte"))
            assertThat(it.firstByteDuration).isEqualTo(timingsPayload.durationOf("firstByte"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["startTime", "duration"])
    fun `𝕄 create timings 𝕎 extractResourceTiming { ssl timing info is incomplete }`(
        timingPartToRemove: String,
        @Forgery reference: ResourceTiming
    ) {
        // Given
        val timingsPayload = reference.asTimingsPayload()

        val badTiming = "ssl"

        @Suppress("UNCHECKED_CAST")
        val timing = timingsPayload[badTiming] as MutableMap<String, Any?>

        timing.remove(timingPartToRemove)

        // When
        val timings = extractResourceTiming(timingsPayload)

        // Then
        assertThat(timings).isNotNull

        timings!!.let {
            assertThat(it.sslStart).isEqualTo(0L)
            assertThat(it.sslDuration).isEqualTo(0L)

            assertThat(it.connectStart).isEqualTo(timingsPayload.startTimeOf("connect"))
            assertThat(it.connectDuration).isEqualTo(timingsPayload.durationOf("connect"))

            assertThat(it.downloadStart).isEqualTo(timingsPayload.startTimeOf("download"))
            assertThat(it.downloadDuration).isEqualTo(timingsPayload.durationOf("download"))

            assertThat(it.dnsStart).isEqualTo(timingsPayload.startTimeOf("dns"))
            assertThat(it.dnsDuration).isEqualTo(timingsPayload.durationOf("dns"))

            assertThat(it.firstByteStart).isEqualTo(timingsPayload.startTimeOf("firstByte"))
            assertThat(it.firstByteDuration).isEqualTo(timingsPayload.durationOf("firstByte"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["startTime", "duration"])
    fun `𝕄 create timings 𝕎 extractResourceTiming { connect timing info is incomplete }`(
        timingPartToRemove: String,
        @Forgery reference: ResourceTiming
    ) {
        // Given
        val timingsPayload = reference.asTimingsPayload()

        val badTiming = "connect"

        @Suppress("UNCHECKED_CAST")
        val timing = timingsPayload[badTiming] as MutableMap<String, Any?>

        timing.remove(timingPartToRemove)

        // When
        val timings = extractResourceTiming(timingsPayload)

        // Then
        assertThat(timings).isNotNull

        timings!!.let {
            assertThat(it.sslStart).isEqualTo(timingsPayload.startTimeOf("ssl"))
            assertThat(it.sslDuration).isEqualTo(timingsPayload.durationOf("ssl"))

            assertThat(it.connectStart).isEqualTo(0L)
            assertThat(it.connectDuration).isEqualTo(0L)

            assertThat(it.downloadStart).isEqualTo(timingsPayload.startTimeOf("download"))
            assertThat(it.downloadDuration).isEqualTo(timingsPayload.durationOf("download"))

            assertThat(it.dnsStart).isEqualTo(timingsPayload.startTimeOf("dns"))
            assertThat(it.dnsDuration).isEqualTo(timingsPayload.durationOf("dns"))

            assertThat(it.firstByteStart).isEqualTo(timingsPayload.startTimeOf("firstByte"))
            assertThat(it.firstByteDuration).isEqualTo(timingsPayload.durationOf("firstByte"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["startTime", "duration"])
    fun `𝕄 create timings 𝕎 extractResourceTiming { download timing info is incomplete }`(
        timingPartToRemove: String,
        @Forgery reference: ResourceTiming
    ) {
        // Given
        val timingsPayload = reference.asTimingsPayload()

        val badTiming = "download"

        @Suppress("UNCHECKED_CAST")
        val timing = timingsPayload[badTiming] as MutableMap<String, Any?>

        timing.remove(timingPartToRemove)

        // When
        val timings = extractResourceTiming(timingsPayload)

        // Then
        assertThat(timings).isNotNull

        timings!!.let {
            assertThat(it.sslStart).isEqualTo(timingsPayload.startTimeOf("ssl"))
            assertThat(it.sslDuration).isEqualTo(timingsPayload.durationOf("ssl"))

            assertThat(it.connectStart).isEqualTo(timingsPayload.startTimeOf("connect"))
            assertThat(it.connectDuration).isEqualTo(timingsPayload.durationOf("connect"))

            assertThat(it.downloadStart).isEqualTo(0L)
            assertThat(it.downloadDuration).isEqualTo(0L)

            assertThat(it.dnsStart).isEqualTo(timingsPayload.startTimeOf("dns"))
            assertThat(it.dnsDuration).isEqualTo(timingsPayload.durationOf("dns"))

            assertThat(it.firstByteStart).isEqualTo(timingsPayload.startTimeOf("firstByte"))
            assertThat(it.firstByteDuration).isEqualTo(timingsPayload.durationOf("firstByte"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["startTime", "duration"])
    fun `𝕄 create timings 𝕎 extractResourceTiming { dns timing info is incomplete }`(
        timingPartToRemove: String,
        @Forgery reference: ResourceTiming
    ) {
        // Given
        val timingsPayload = reference.asTimingsPayload()

        val badTiming = "dns"

        @Suppress("UNCHECKED_CAST")
        val timing = timingsPayload[badTiming] as MutableMap<String, Any?>

        timing.remove(timingPartToRemove)

        // When
        val timings = extractResourceTiming(timingsPayload)

        // Then
        assertThat(timings).isNotNull

        timings!!.let {
            assertThat(it.sslStart).isEqualTo(timingsPayload.startTimeOf("ssl"))
            assertThat(it.sslDuration).isEqualTo(timingsPayload.durationOf("ssl"))

            assertThat(it.connectStart).isEqualTo(timingsPayload.startTimeOf("connect"))
            assertThat(it.connectDuration).isEqualTo(timingsPayload.durationOf("connect"))

            assertThat(it.downloadStart).isEqualTo(timingsPayload.startTimeOf("download"))
            assertThat(it.downloadDuration).isEqualTo(timingsPayload.durationOf("download"))

            assertThat(it.dnsStart).isEqualTo(0L)
            assertThat(it.dnsDuration).isEqualTo(0L)

            assertThat(it.firstByteStart).isEqualTo(timingsPayload.startTimeOf("firstByte"))
            assertThat(it.firstByteDuration).isEqualTo(timingsPayload.durationOf("firstByte"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["startTime", "duration"])
    fun `𝕄 create timings 𝕎 extractResourceTiming { firstByte timing info is incomplete }`(
        timingPartToRemove: String,
        @Forgery reference: ResourceTiming
    ) {
        // Given
        val timingsPayload = reference.asTimingsPayload()

        val badTiming = "firstByte"

        @Suppress("UNCHECKED_CAST")
        val timing = timingsPayload[badTiming] as MutableMap<String, Any?>

        timing.remove(timingPartToRemove)

        // When
        val timings = extractResourceTiming(timingsPayload)

        // Then
        assertThat(timings).isNotNull

        timings!!.let {
            assertThat(it.sslStart).isEqualTo(timingsPayload.startTimeOf("ssl"))
            assertThat(it.sslDuration).isEqualTo(timingsPayload.durationOf("ssl"))

            assertThat(it.connectStart).isEqualTo(timingsPayload.startTimeOf("connect"))
            assertThat(it.connectDuration).isEqualTo(timingsPayload.durationOf("connect"))

            assertThat(it.downloadStart).isEqualTo(timingsPayload.startTimeOf("download"))
            assertThat(it.downloadDuration).isEqualTo(timingsPayload.durationOf("download"))

            assertThat(it.dnsStart).isEqualTo(timingsPayload.startTimeOf("dns"))
            assertThat(it.dnsDuration).isEqualTo(timingsPayload.durationOf("dns"))

            assertThat(it.firstByteStart).isEqualTo(0L)
            assertThat(it.firstByteDuration).isEqualTo(0L)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["startTime", "duration"])
    fun `𝕄 create timings 𝕎 extractResourceTiming { ssl timing info with wrong structure }`(
        timingPartWithWrongType: String,
        @Forgery reference: ResourceTiming
    ) {
        // Given
        val timingsPayload = reference.asTimingsPayload()

        val badTiming = "ssl"

        @Suppress("UNCHECKED_CAST")
        val timing = timingsPayload[badTiming] as MutableMap<String, Any?>
        timing[timingPartWithWrongType] = true

        // When
        val timings = extractResourceTiming(timingsPayload)

        // Then
        assertThat(timings).isNotNull

        timings!!.let {
            assertThat(it.sslStart).isEqualTo(0L)
            assertThat(it.sslDuration).isEqualTo(0L)

            assertThat(it.connectStart).isEqualTo(timingsPayload.startTimeOf("connect"))
            assertThat(it.connectDuration).isEqualTo(timingsPayload.durationOf("connect"))

            assertThat(it.downloadStart).isEqualTo(timingsPayload.startTimeOf("download"))
            assertThat(it.downloadDuration).isEqualTo(timingsPayload.durationOf("download"))

            assertThat(it.dnsStart).isEqualTo(timingsPayload.startTimeOf("dns"))
            assertThat(it.dnsDuration).isEqualTo(timingsPayload.durationOf("dns"))

            assertThat(it.firstByteStart).isEqualTo(timingsPayload.startTimeOf("firstByte"))
            assertThat(it.firstByteDuration).isEqualTo(timingsPayload.durationOf("firstByte"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["startTime", "duration"])
    fun `𝕄 create timings 𝕎 extractResourceTiming { connect timing info with wrong structure }`(
        timingPartWithWrongType: String,
        @Forgery reference: ResourceTiming
    ) {
        // Given
        val timingsPayload = reference.asTimingsPayload()

        val badTiming = "connect"

        @Suppress("UNCHECKED_CAST")
        val timing = timingsPayload[badTiming] as MutableMap<String, Any?>
        timing[timingPartWithWrongType] = true

        // When
        val timings = extractResourceTiming(timingsPayload)

        // Then
        assertThat(timings).isNotNull

        timings!!.let {
            assertThat(it.sslStart).isEqualTo(timingsPayload.startTimeOf("ssl"))
            assertThat(it.sslDuration).isEqualTo(timingsPayload.durationOf("ssl"))

            assertThat(it.connectStart).isEqualTo(0L)
            assertThat(it.connectDuration).isEqualTo(0L)

            assertThat(it.downloadStart).isEqualTo(timingsPayload.startTimeOf("download"))
            assertThat(it.downloadDuration).isEqualTo(timingsPayload.durationOf("download"))

            assertThat(it.dnsStart).isEqualTo(timingsPayload.startTimeOf("dns"))
            assertThat(it.dnsDuration).isEqualTo(timingsPayload.durationOf("dns"))

            assertThat(it.firstByteStart).isEqualTo(timingsPayload.startTimeOf("firstByte"))
            assertThat(it.firstByteDuration).isEqualTo(timingsPayload.durationOf("firstByte"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["startTime", "duration"])
    fun `𝕄 create timings 𝕎 extractResourceTiming { download timing info with wrong structure }`(
        timingPartWithWrongType: String,
        @Forgery reference: ResourceTiming
    ) {
        // Given
        val timingsPayload = reference.asTimingsPayload()

        val badTiming = "download"

        @Suppress("UNCHECKED_CAST")
        val timing = timingsPayload[badTiming] as MutableMap<String, Any?>
        timing[timingPartWithWrongType] = true

        // When
        val timings = extractResourceTiming(timingsPayload)

        // Then
        assertThat(timings).isNotNull

        timings!!.let {
            assertThat(it.sslStart).isEqualTo(timingsPayload.startTimeOf("ssl"))
            assertThat(it.sslDuration).isEqualTo(timingsPayload.durationOf("ssl"))

            assertThat(it.connectStart).isEqualTo(timingsPayload.startTimeOf("connect"))
            assertThat(it.connectDuration).isEqualTo(timingsPayload.durationOf("connect"))

            assertThat(it.downloadStart).isEqualTo(0L)
            assertThat(it.downloadDuration).isEqualTo(0L)

            assertThat(it.dnsStart).isEqualTo(timingsPayload.startTimeOf("dns"))
            assertThat(it.dnsDuration).isEqualTo(timingsPayload.durationOf("dns"))

            assertThat(it.firstByteStart).isEqualTo(timingsPayload.startTimeOf("firstByte"))
            assertThat(it.firstByteDuration).isEqualTo(timingsPayload.durationOf("firstByte"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["startTime", "duration"])
    fun `𝕄 create timings 𝕎 extractResourceTiming { dns timing info with wrong structure }`(
        timingPartWithWrongType: String,
        @Forgery reference: ResourceTiming
    ) {
        // Given
        val timingsPayload = reference.asTimingsPayload()

        val badTiming = "dns"

        @Suppress("UNCHECKED_CAST")
        val timing = timingsPayload[badTiming] as MutableMap<String, Any?>
        timing[timingPartWithWrongType] = true

        // When
        val timings = extractResourceTiming(timingsPayload)

        // Then
        assertThat(timings).isNotNull

        timings!!.let {
            assertThat(it.sslStart).isEqualTo(timingsPayload.startTimeOf("ssl"))
            assertThat(it.sslDuration).isEqualTo(timingsPayload.durationOf("ssl"))

            assertThat(it.connectStart).isEqualTo(timingsPayload.startTimeOf("connect"))
            assertThat(it.connectDuration).isEqualTo(timingsPayload.durationOf("connect"))

            assertThat(it.downloadStart).isEqualTo(timingsPayload.startTimeOf("download"))
            assertThat(it.downloadDuration).isEqualTo(timingsPayload.durationOf("download"))

            assertThat(it.dnsStart).isEqualTo(0L)
            assertThat(it.dnsDuration).isEqualTo(0L)

            assertThat(it.firstByteStart).isEqualTo(timingsPayload.startTimeOf("firstByte"))
            assertThat(it.firstByteDuration).isEqualTo(timingsPayload.durationOf("firstByte"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["startTime", "duration"])
    fun `𝕄 create timings 𝕎 extractResourceTiming { firstByte timing info with wrong structure }`(
        timingPartWithWrongType: String,
        @Forgery reference: ResourceTiming
    ) {
        // Given
        val timingsPayload = reference.asTimingsPayload()

        val badTiming = "firstByte"

        @Suppress("UNCHECKED_CAST")
        val timing = timingsPayload[badTiming] as MutableMap<String, Any?>
        timing[timingPartWithWrongType] = true

        // When
        val timings = extractResourceTiming(timingsPayload)

        // Then
        assertThat(timings).isNotNull

        timings!!.let {
            assertThat(it.sslStart).isEqualTo(timingsPayload.startTimeOf("ssl"))
            assertThat(it.sslDuration).isEqualTo(timingsPayload.durationOf("ssl"))

            assertThat(it.connectStart).isEqualTo(timingsPayload.startTimeOf("connect"))
            assertThat(it.connectDuration).isEqualTo(timingsPayload.durationOf("connect"))

            assertThat(it.downloadStart).isEqualTo(timingsPayload.startTimeOf("download"))
            assertThat(it.downloadDuration).isEqualTo(timingsPayload.durationOf("download"))

            assertThat(it.dnsStart).isEqualTo(timingsPayload.startTimeOf("dns"))
            assertThat(it.dnsDuration).isEqualTo(timingsPayload.durationOf("dns"))

            assertThat(it.firstByteStart).isEqualTo(0L)
            assertThat(it.firstByteDuration).isEqualTo(0L)
        }
    }

    @Test
    fun `𝕄 not create timings 𝕎 extractResourceTiming { payload is null }`() {
        // When
        val timings = extractResourceTiming(null)

        // Then
        assertThat(timings).isNull()
    }

    @Test
    fun `𝕄 not create timings 𝕎 extractResourceTiming { payload is empty }`() {
        // When
        val timings = extractResourceTiming(emptyMap())

        // Then
        assertThat(timings).isNull()
    }

    // region Internal

    private fun Map<*, *>.startTimeOf(timingName: String): Long {
        @Suppress("UNCHECKED_CAST")
        val timing = this[timingName] as? Map<String, Any?>
        return if (timing != null) {
            timing["startTime"] as? Long ?: 0L
        } else {
            0L
        }
    }

    private fun Map<*, *>.durationOf(timingName: String): Long {
        @Suppress("UNCHECKED_CAST")
        val timing = this[timingName] as? Map<String, Any?>
        return if (timing != null) {
            timing["duration"] as? Long ?: 0L
        } else {
            0L
        }
    }

    // endregion
}
