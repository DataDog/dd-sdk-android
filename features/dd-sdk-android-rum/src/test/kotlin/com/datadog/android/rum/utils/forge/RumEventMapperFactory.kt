/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.rum.internal.domain.event.RumEventMapper
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import org.mockito.kotlin.mock

internal class RumEventMapperFactory : ForgeryFactory<RumEventMapper> {

    override fun getForgery(forge: Forge): RumEventMapper {
        return RumEventMapper(
            sdkCore = mock(),
            viewEventMapper = mock(),
            actionEventMapper = mock(),
            resourceEventMapper = mock(),
            errorEventMapper = mock(),
            telemetryConfigurationMapper = mock(),
            internalLogger = mock()
        )
    }
}
