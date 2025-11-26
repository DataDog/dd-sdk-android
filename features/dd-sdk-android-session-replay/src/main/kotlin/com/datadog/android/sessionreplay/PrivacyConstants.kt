/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

/**
 * Image dimension threshold in DP for determining if an image should be masked.
 * Images >= this size are considered content/PII when using MASK_LARGE_ONLY privacy mode.
 * Material design icon size is up to 48x48, but 100dp is used to match more images.
 */
const val IMAGE_DIMEN_CONSIDERED_PII_IN_DP: Int = 100
