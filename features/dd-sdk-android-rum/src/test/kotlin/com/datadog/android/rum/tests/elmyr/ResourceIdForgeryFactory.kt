/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tests.elmyr

import com.datadog.android.rum.resource.ResourceId
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.UUID

internal class ResourceIdForgeryFactory : ForgeryFactory<ResourceId> {
    override fun getForgery(forge: Forge): ResourceId {
        return ResourceId(
            forge.aStringMatching("https://[a-z]+.[a-z]{3}/[a-z0-9_/]+/[a-z0-9_/]+"),
            forge.aNullable { getForgery<UUID>().toString() }
        )
    }
}
