/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.embedded

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.tools.unit.ObjectTest
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EmbeddedContentResourceEventTest : ObjectTest<EmbeddedContentEvent.Resource>() {

    override fun createInstance(forge: Forge): EmbeddedContentEvent.Resource {
        return EmbeddedContentEvent.Resource(
            identifier = forge.aString(),
            data = forge.aString().toByteArray(),
            mimeType = forge.aString()
        )
    }

    override fun createEqualInstance(
        source: EmbeddedContentEvent.Resource,
        forge: Forge
    ): EmbeddedContentEvent.Resource {
        return source.copy(data = source.data.copyOf())
    }

    override fun createUnequalInstance(
        source: EmbeddedContentEvent.Resource,
        forge: Forge
    ): EmbeddedContentEvent.Resource {
        return when (forge.anInt(min = 0, max = 2)) {
            0 -> source.copy(identifier = source.identifier + DIFFERENT_VALUE_SUFFIX)
            1 -> source.copy(data = source.data + DIFFERENT_BYTE)
            else -> source.copy(mimeType = source.mimeType + DIFFERENT_VALUE_SUFFIX)
        }
    }

    companion object {
        private const val DIFFERENT_VALUE_SUFFIX = "-different"
        private const val DIFFERENT_BYTE: Byte = 0
    }
}
