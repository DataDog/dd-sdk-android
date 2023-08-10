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
        forge.addFactory(ConfigurationForgeryFactory())
        forge.addFactory(NetworkInfoForgeryFactory())
        forge.addFactory(UserInfoForgeryFactory())
        forge.addFactory(FilePersistenceConfigForgeryFactory())
        forge.addFactory(AndroidInfoProviderForgeryFactory())
        forge.addFactory(FeatureStorageConfigurationForgeryFactory())

        // IO
        forge.addFactory(BatchForgeryFactory())
        forge.addFactory(PayloadDecorationForgeryFactory())
        forge.addFactory(WorkerParametersForgeryFactory())

        // NDK Crash
        forge.addFactory(NdkCrashLogForgeryFactory())

        // MISC
        forge.addFactory(BigIntegerFactory())
        forge.addFactory(CharsetForgeryFactory())

        // Datadog SDK v2
        forge.addFactory(TimeInfoForgeryFactory())
        forge.addFactory(ProcessInfoForgeryFactory())
        forge.addFactory(DeviceInfoForgeryFactory())
        forge.addFactory(DatadogContextForgeryFactory())
        forge.addFactory(DataUploadConfigurationForgeryFactory())

        forge.useJvmFactories()
    }
}
