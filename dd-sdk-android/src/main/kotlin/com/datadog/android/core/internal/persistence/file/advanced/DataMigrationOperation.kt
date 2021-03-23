/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import com.datadog.tools.annotation.NoOpImplementation

/**
 * A [Runnable] used to perform a data migration operation (moving, modifying or deleting files).
 */
@NoOpImplementation
internal interface DataMigrationOperation : Runnable
