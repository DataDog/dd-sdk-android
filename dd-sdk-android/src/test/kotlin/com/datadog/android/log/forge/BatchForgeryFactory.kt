/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.forge

import com.datadog.android.core.internal.domain.Batch
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

internal class BatchForgeryFactory : ForgeryFactory<Batch> {
    override fun getForgery(forge: Forge): Batch {
        return Batch(
            forge.anHexadecimalString(),
            forge.anAlphabeticalString().toByteArray(Charsets.UTF_8)
        )
    }
}
