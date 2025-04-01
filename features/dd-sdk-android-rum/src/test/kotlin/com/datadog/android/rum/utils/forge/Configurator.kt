/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.forge

import com.datadog.android.internal.tests.elmyr.InternalTelemetryApiUsageForgeryFactory
import com.datadog.android.internal.tests.elmyr.InternalTelemetryConfigurationForgeryFactory
import com.datadog.android.internal.tests.elmyr.InternalTelemetryDebugLogForgeryFactory
import com.datadog.android.internal.tests.elmyr.InternalTelemetryErrorLogForgeryFactory
import com.datadog.android.internal.tests.elmyr.InternalTelemetryEventForgeryFactory
import com.datadog.android.internal.tests.elmyr.InternalTelemetryMetricForgeryFactory
import com.datadog.android.internal.tests.elmyr.TracingHeaderTypesSetForgeryFactory
import com.datadog.android.rum.tests.elmyr.ResourceIdForgeryFactory
import com.datadog.android.rum.tests.elmyr.RumScopeKeyForgeryFactory
import com.datadog.android.tests.elmyr.useCoreFactories
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.jvm.useJvmFactories

internal class Configurator : BaseConfigurator() {

    override fun configure(forge: Forge) {
        super.configure(forge)
        forge.useJvmFactories()

        // Core
        forge.useCoreFactories()

        // RUM
        forge.addFactory(ConfigurationRumForgeryFactory())
        forge.addFactory(RumConfigurationForgeryFactory())
        forge.addFactory(ActionEventForgeryFactory())
        forge.addFactory(ErrorEventForgeryFactory())
        forge.addFactory(LongTaskEventForgeryFactory())
        forge.addFactory(MotionEventForgeryFactory())
        forge.addFactory(RumEventMapperFactory())
        forge.addFactory(RumContextForgeryFactory())
        forge.addFactory(ResourceEventForgeryFactory())
        forge.addFactory(ResourceTimingForgeryFactory())
        forge.addFactory(ViewEventForgeryFactory())
        forge.addFactory(VitalInfoForgeryFactory())
        forge.addFactory(RumEventMetaForgeryFactory())
        forge.addFactory(ViewEventMetaForgeryFactory())
        forge.addFactory(RumScopeKeyForgeryFactory())
        forge.addFactory(ResourceIdForgeryFactory())
        forge.addFactory(InternalResourceContextFactory())
        forge.addFactory(NetworkSettledResourceContextFactory())
        forge.addFactory(InternalInteractionContextFactory())
        forge.addFactory(PreviousViewLastActionContextFactory())
        forge.addFactory(TelemetryViewInitializationMetricsStateForgeryFactory())
        forge.addFactory(FrameDataForgeryFactory())
        forge.addFactory(FrameMetricDataForgeryFactory())
        forge.addFactory(ViewUIPerformanceReportForgeryFactory())
        forge.addFactory(SlowFramesConfigurationForgeryFactory())

        // Telemetry schema models
        forge.addFactory(TelemetryDebugEventForgeryFactory())
        forge.addFactory(TelemetryErrorEventForgeryFactory())
        forge.addFactory(TelemetryConfigurationEventForgeryFactory())
        forge.addFactory(TelemetryUsageEventForgeryFactory())

        // Telemetry internal models
        forge.addFactory(InternalTelemetryEventForgeryFactory())
        forge.addFactory(InternalTelemetryMetricForgeryFactory())
        forge.addFactory(InternalTelemetryDebugLogForgeryFactory())
        forge.addFactory(InternalTelemetryErrorLogForgeryFactory())
        forge.addFactory(InternalTelemetryConfigurationForgeryFactory())
        forge.addFactory(InternalTelemetryApiUsageForgeryFactory())
        forge.addFactory(TracingHeaderTypesSetForgeryFactory())

        // RumRawEvent
        forge.addFactory(ActionSentForgeryFactory())
        forge.addFactory(ResourceSentForgeryFactory())
        forge.addFactory(ResourceDroppedForgeryFactory())
        forge.addFactory(AddCustomTimingEventForgeryFactory())
        forge.addFactory(AddErrorEventForgeryFactory())
        forge.addFactory(AddFeatureFlagEvaluationForgeryFactory())
        forge.addFactory(AddFeatureFlagEvaluationsForgeryFactory())
        forge.addFactory(ErrorSentForgeryFactory())
        forge.addFactory(KeepAliveForgeryFactory())
        forge.addFactory(LongTaskSentForgeryFactory())
    }
}
