/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.rum.internal.domain.event.RumEventMapper
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class RumEventMapperFactory : ForgeryFactory<RumEventMapper> {

    override fun getForgery(forge: Forge): RumEventMapper {

        return RumEventMapper(
            viewEventMapper = mock(),
            actionEventMapper = mock(),
            resourceEventMapper = mock(),
            errorEventMapper = mock()
        )
    }
}
