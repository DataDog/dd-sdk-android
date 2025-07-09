/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.impl.internal

import com.datadog.android.trace.api.scope.DataScopeListener
import com.datadog.trace.api.scopemanager.ScopeListener

internal class DatadogScopeListenerAdapter(
    internal val delegate: DataScopeListener
) : ScopeListener {
    override fun afterScopeClosed() = delegate.afterScopeClosed()
    override fun afterScopeActivated() = delegate.afterScopeActivated()
}
