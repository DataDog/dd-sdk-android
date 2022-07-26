/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.noopfactory

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * a [SymbolProcessorProvider] providing a [NoOpFactorySymbolProcessor].
 */
class NoOpFactoryProvider : SymbolProcessorProvider {
    /** @inheritdoc */
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return NoOpFactorySymbolProcessor(
            environment.codeGenerator,
            environment.logger
        )
    }
}
