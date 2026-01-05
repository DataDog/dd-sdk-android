/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal.utils

/**
 * Executes the given [block] if this value is null.
 * @param T the type of the value.
 * @param block the action to execute if this is null.
 * @return this value unchanged.
 */
fun <T> T?.runIfNull(block: () -> Unit): T? = apply { if (this == null) block() }
