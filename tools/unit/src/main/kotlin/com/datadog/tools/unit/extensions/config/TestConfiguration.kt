/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.extensions.config

import fr.xgouchet.elmyr.Forge

/**
 * Implements a self contained Test configuration.
 * The [setUp] and [tearDown] methods will be called before/after each test.
 *
 * Any implementation should make sure that any global/static state set in the [setUp] method
 * is properly reset in the [tearDown] method.
 */
interface TestConfiguration {

    /**
     * Called before a test method (and any local @BeforeEach method) is ran.
     * This can be used to set up mocks, fakes or any global state.
     */
    fun setUp(forge: Forge)

    /**
     * Called after a test method (and any local @AfterEach method) is ran.
     * This can be used to clear up mocks, fakes or reset any global state to a default value.
     */
    fun tearDown(forge: Forge)
}
