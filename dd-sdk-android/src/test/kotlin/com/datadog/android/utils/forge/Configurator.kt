/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.jvm.useJvmFactories

internal class Configurator :
    BaseConfigurator() {
    override fun configure(forge: Forge) {
        super.configure(forge)

        // Datadog Core
        forge.addFactory(CustomAttributesForgeryFactory())
        forge.addFactory(ConfigurationForgeryFactory())
        forge.addFactory(ConfigurationCoreForgeryFactory())
        forge.addFactory(SecurityConfigForgeryFactory())
        forge.addFactory(ConfigurationFeatureForgeryFactory())
        forge.addFactory(ConfigurationLogForgeryFactory())
        forge.addFactory(ConfigurationCrashReportForgeryFactory())
        forge.addFactory(ConfigurationTracingForgeryFactory())
        forge.addFactory(ConfigurationRumForgeryFactory())
        forge.addFactory(CredentialsForgeryFactory())
        forge.addFactory(NetworkInfoForgeryFactory())
        forge.addFactory(UserInfoForgeryFactory())
        forge.addFactory(FilePersistenceConfigForgeryFactory())
        forge.addFactory(AndroidInfoProviderForgeryFactory())
        forge.addFactory(ConfigurationSessionReplayForgeryFactory())

        // IO
        forge.addFactory(BatchForgeryFactory())
        forge.addFactory(PayloadDecorationForgeryFactory())
        forge.addFactory(WorkerParametersForgeryFactory())

        // LOG
        forge.addFactory(LogEventForgeryFactory())

        // APM
        forge.addFactory(SpanForgeryFactory())
        forge.addFactory(SpanEventForgeryFactory())

        // RUM
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

        // Telemetry
        forge.addFactory(TelemetryDebugEventForgeryFactory())
        forge.addFactory(TelemetryErrorEventForgeryFactory())

        // NDK Crash
        forge.addFactory(NdkCrashLogForgeryFactory())

        // MISC
        forge.addFactory(BigIntegerFactory())
        forge.addFactory(CharsetForgeryFactory())
        forge.addFactory(GsonJsonArrayForgeryFactory())
        forge.addFactory(GsonJsonObjectForgeryFactory())
        forge.addFactory(GsonJsonPrimitiveForgeryFactory())
        forge.addFactory(OrgJSONObjectForgeryFactory())
        forge.addFactory(OrgJSONArrayForgeryFactory())

        // Datadog SDK v2
        forge.addFactory(TimeInfoForgeryFactory())
        forge.addFactory(ProcessInfoForgeryFactory())
        forge.addFactory(NetworkInfoV2ForgeryFactory())
        forge.addFactory(DeviceInfoForgeryFactory())
        forge.addFactory(UserInfoV2ForgeryFactory())
        forge.addFactory(DatadogContextForgeryFactory())

        forge.useJvmFactories()
    }
}
