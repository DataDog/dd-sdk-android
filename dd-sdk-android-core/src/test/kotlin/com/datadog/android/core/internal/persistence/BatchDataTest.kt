/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.ObjectTest
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(ExtendWith(ForgeExtension::class))
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class BatchDataTest : ObjectTest<BatchData>() {
    override fun createInstance(forge: Forge): BatchData {
        return forge.getForgery()
    }

    override fun createEqualInstance(source: BatchData, forge: Forge): BatchData {
        return BatchData(source.id, source.data, source.metadata)
    }

    override fun createUnequalInstance(source: BatchData, forge: Forge): BatchData? {
        return forge.getForgery()
    }
}
