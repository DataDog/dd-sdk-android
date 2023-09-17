/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.exoplayer.utils.forge

import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge

class ExoPlayerForgeryConfigurator : BaseConfigurator() {

    override fun configure(forge: Forge) {
        super.configure(forge)
        forge.addFactory(MediaItemForgeryFactory())
    }

}