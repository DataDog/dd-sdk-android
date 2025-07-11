/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.utils.forge

import com.datadog.android.trace.api.trace.DatadogTraceId
import com.datadog.android.trace.internal.DatadogTraceIdAdapter
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory

class DatadogTraceIdForgeryFactory : ForgeryFactory<DatadogTraceId> {
    override fun getForgery(forge: Forge): DatadogTraceId {
        return DatadogTraceIdAdapter(forge.getForgery())
    }
}
