/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

// TODO RUM-373 public as hack, no other solution for now. Any?.toJsonElement relies on this
//  particular value. Maybe create something like (class NullMap) and check identity instead?
/**
 * Special value for missing attribute.
 */
@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "PackageNameVisibility")
val NULL_MAP_VALUE: Object = Object()
