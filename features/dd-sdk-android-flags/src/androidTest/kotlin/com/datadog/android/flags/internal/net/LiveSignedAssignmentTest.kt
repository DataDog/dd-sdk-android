/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.DeviceInfo
import com.datadog.android.api.context.DeviceType
import com.datadog.android.api.context.LocaleInfo
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.ProcessInfo
import com.datadog.android.api.context.TimeInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.flags.AssignmentAuthorization
import com.datadog.android.flags.AssignmentProtection
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.privacy.TrackingConsent
import okhttp3.OkHttpClient
import org.junit.Assert.fail
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
internal class LiveSignedAssignmentTest {

    @Test
    fun acceptsLiveStagingSignatureAndRejectsTampering() {
        val arguments = InstrumentationRegistry.getArguments()
        val clientToken = arguments.getString("clientToken")
        val assignmentJwt = arguments.getString("assignmentJwt")
        val endpoint = arguments.getString("assignmentEndpoint")
            ?: "https://preview.ff-cdn.datad0g.com/precompute-assignments"
        val fixtureKey = arguments.getString("fixtureKey") ?: "country-message"
        assumeNotNull(clientToken)
        requireNotNull(clientToken)

        val protection = if (assignmentJwt == null) {
            AssignmentProtection.SIGNED
        } else {
            AssignmentProtection.SIGNED_AND_AUTHORIZED
        }
        val authorizationStore = AssignmentAuthorizationStore(
            assignmentJwt?.let {
                AssignmentAuthorization(
                    bearerToken = it,
                    expiresAt = Date(System.currentTimeMillis() + LIVE_TEST_AUTHORIZATION_LIFETIME_MS)
                )
            }
        )
        val requestFactory = PrecomputedAssignmentsRequestFactory(
            internalLogger = InternalLogger.UNBOUND,
            customFlagEndpoint = endpoint,
            authorizationStore = authorizationStore,
            assignmentProtection = protection
        )
        val request = requireNotNull(
            requestFactory.create(
                context = EvaluationContext(
                    targetingKey = "signed-assignment-poc",
                    attributes = mapOf("user_id" to "signed-assignment-poc", "country" to "US")
                ),
                datadogContext = datadogContext(clientToken)
            )
        ).newBuilder()
            .header("Accept-Encoding", "identity")
            .build()

        OkHttpClient().newCall(request).execute().use { response ->
            val responseBody = requireNotNull(response.body).bytes()
            check(response.code == 200) { "Staging returned HTTP ${response.code}" }
            val verifier = PrecomputedAssignmentsVerifier(protection)
            val envelope = requireNotNull(verifier.verify(request, response, responseBody))
            val fixtureValue = PrecomputeMapper(InternalLogger.UNBOUND)
                .map(responseBody.decodeToString())[fixtureKey]
                ?.variationValue
                ?.replace('\n', ' ')
                ?.replace('\r', ' ')
                ?.take(MAX_DIAGNOSTIC_VALUE_LENGTH)
                ?: "<missing>"
            val ageSeconds = System.currentTimeMillis() / 1_000L - envelope.issuedAt
            Log.i(
                LOG_TAG,
                "mode=${protection.name} verification=accepted generation=1 " +
                    "certificateId=${envelope.certificateId} cacheSource=network " +
                    "cacheAgeSeconds=$ageSeconds fixtureValue=$fixtureValue"
            )

            val tamperedBody = responseBody.copyOf()
            tamperedBody[0] = (tamperedBody[0].toInt() xor 1).toByte()
            try {
                verifier.verify(request, response, tamperedBody)
                fail("The verifier accepted a modified response body")
            } catch (_: AssignmentPayloadVerificationException) {
                Log.i(LOG_TAG, "mode=${protection.name} verification=rejected attack=body-tamper")
            }
        }
    }

    private fun datadogContext(clientToken: String) = DatadogContext(
        site = DatadogSite.US1,
        clientToken = clientToken,
        service = "signed-assignment-wargame",
        env = "staging",
        version = "1.0.0",
        versionCode = 1,
        variant = "androidTest",
        source = "android",
        sdkVersion = "wargame",
        time = TimeInfo(0L, 0L, 0L, 0L),
        processInfo = ProcessInfo(isMainProcess = true),
        networkInfo = NetworkInfo(),
        deviceInfo = DeviceInfo(
            deviceName = "android-emulator",
            deviceBrand = "android",
            deviceModel = "emulator",
            deviceType = DeviceType.MOBILE,
            deviceBuildId = "wargame",
            osName = "Android",
            osMajorVersion = "test",
            osVersion = "test",
            architecture = "test",
            numberOfDisplays = 1,
            localeInfo = LocaleInfo(listOf("en-US"), "en-US", "UTC"),
            logicalCpuCount = 1,
            totalRam = null,
            isLowRam = null
        ),
        userInfo = UserInfo(),
        accountInfo = null,
        trackingConsent = TrackingConsent.GRANTED,
        appBuildId = null,
        remoteConfigurationId = null,
        featuresContext = emptyMap()
    )

    private companion object {
        const val LOG_TAG = "SignedAssignmentsWargame"
        const val LIVE_TEST_AUTHORIZATION_LIFETIME_MS = 60 * 60 * 1_000L
        const val MAX_DIAGNOSTIC_VALUE_LENGTH = 128
    }
}
