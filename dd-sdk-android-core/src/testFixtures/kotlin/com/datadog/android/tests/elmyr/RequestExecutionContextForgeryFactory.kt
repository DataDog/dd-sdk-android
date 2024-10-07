/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tests.elmyr

import com.datadog.android.api.net.RequestExecutionContext
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class RequestExecutionContextForgeryFactory : ForgeryFactory<RequestExecutionContext> {

    override fun getForgery(forge: Forge): RequestExecutionContext {
        return RequestExecutionContext(
            attemptNumber = forge.aPositiveInt(),
            previousResponseCode = forge.aNullable { aPositiveInt() }
        )
    }
}
