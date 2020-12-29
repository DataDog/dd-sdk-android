/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.datadog.android.core.configuration.Configuration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.net.URL

internal class ConfigurationCoreForgeryFactory :
    ForgeryFactory<Configuration.Core> {
    override fun getForgery(forge: Forge): Configuration.Core {
        return Configuration.Core(
            needsClearTextHttp = forge.aBool(),
            firstPartyHosts = forge.aList { getForgery<URL>().host }
        )
    }
}
