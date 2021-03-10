/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import com.datadog.tools.unit.ObjectTest
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class AcceptAllDefaultFragmentTest : ObjectTest<AcceptAllDefaultFragment>() {

    override fun createInstance(forge: Forge): AcceptAllDefaultFragment {
        return AcceptAllDefaultFragment()
    }

    override fun createEqualInstance(
        source: AcceptAllDefaultFragment,
        forge: Forge
    ): AcceptAllDefaultFragment {
        return AcceptAllDefaultFragment()
    }

    override fun createUnequalInstance(
        source: AcceptAllDefaultFragment,
        forge: Forge
    ): AcceptAllDefaultFragment? {
        return null
    }
}
