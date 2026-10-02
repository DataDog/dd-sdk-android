/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.internal.model.FlagKeyObfuscation
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.persistence.FlagsStateDeserializer
import com.datadog.android.flags.internal.persistence.FlagsStateSerializer
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.EvaluationContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class FlagKeyObfuscationDeviceTest {
    @Test
    fun resolvesEncodedAssignmentAfterCacheRoundTrip() {
        val fakeKey = "🚲/旗"
        val fakeDigest = "94b611e0d3b26b52f6ad66013d1c72f8a92ea109390049759e75d3c8d3aa4dab"
        val fakeAttributes = JSONObject(
            """
            {"obfuscated":true,
             "obfuscation":{"scheme":"flag-key-sha256-v1","salt":"000102030405060708090a0b0c0d0e0f"},
             "flags":{"$fakeDigest":{
               "variationType":"boolean","variationValue":true,"doLog":true,
               "allocationKey":"allocation","variationKey":"variation",
               "reason":"TARGETING_MATCH","serialId":123,"extraLogging":{}
             }}}
            """.trimIndent()
        )
        val logger = InternalLogger.UNBOUND
        val testedMapper = PrecomputeMapper(logger)
        val response = JSONObject().put("data", JSONObject().put("attributes", fakeAttributes)).toString()
        val assignments = checkNotNull(testedMapper.map(response))
        assertEquals(fakeDigest, assignments.obfuscation?.encode(fakeKey))
        val entry =
            FlagsStateEntry(EvaluationContext("subject", emptyMap()), assignments.flags, 123L, assignments.obfuscation)
        val serialized = FlagsStateSerializer(logger).serialize(entry)
        assertFalse(JSONObject(serialized).has("flags"))
        val restored = checkNotNull(FlagsStateDeserializer(logger).deserialize(serialized))
        val flag = checkNotNull(restored.flags[restored.obfuscation?.encode(fakeKey)])
        assertEquals(
            true,
            FlagValueConverter.convert(flag.variationValue, flag.variationType, Boolean::class).getOrThrow()
        )
        assertEquals("variation", flag.variationKey)
        assertEquals(123L, flag.serialId)
        assertNull(checkNotNull(FlagKeyObfuscation.read(fakeAttributes)).encode("\ud800"))
    }
}
