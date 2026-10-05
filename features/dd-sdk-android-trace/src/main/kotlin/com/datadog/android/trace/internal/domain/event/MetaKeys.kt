/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.domain.event

/**
 * Internal tag which indicates the span should be created locally but never sent to the backend.
 */
internal const val FORCE_DROP_SPAN: String = "_dd.local.force_drop"
internal const val TRACE_ID_META_KEY = "_dd.p.id"
internal const val APPLICATION_VARIANT_KEY = "variant"
internal const val COMPUTE_STATS_META_KEY = "_dd.compute_stats"
