/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.opentracing

import com.datadog.android.api.InternalLogger
import com.datadog.opentracing.PendingTrace.IdentityKey
import com.datadog.tools.unit.ObjectTest
import fr.xgouchet.elmyr.Forge
import org.mockito.kotlin.mock
import java.math.BigInteger

internal class IdentityKeyTest : ObjectTest<IdentityKey>() {

    override fun createInstance(forge: Forge): IdentityKey {
        val mockTracer: DDTracer = mock()
        val mockInternalLogger: InternalLogger = mock()
        val fakeTraceId = BigInteger.valueOf(forge.aLong())
        return IdentityKey(PendingTrace(mockTracer, fakeTraceId, mockInternalLogger))
    }

    override fun createEqualInstance(source: IdentityKey, forge: Forge): IdentityKey {
        return IdentityKey(source.key)
    }

    override fun createUnequalInstance(source: IdentityKey, forge: Forge): IdentityKey {
        val mockTracer: DDTracer = mock()
        val mockInternalLogger: InternalLogger = mock()
        val fakeTraceId = BigInteger.valueOf(forge.aLong())
        return IdentityKey(PendingTrace(mockTracer, fakeTraceId, mockInternalLogger))
    }
}
