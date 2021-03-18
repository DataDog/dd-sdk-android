/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.advanced

import com.datadog.android.core.internal.persistence.file.FileHandler
import java.io.File

/**
 * A [DataMigrationOperation] that moves all the files in the `fromDir` directory
 * to the `toDir` directory.
 */
internal class MoveDataMigrationOperation(
    internal val fromDir: File?,
    internal val toDir: File?,
    internal val fileHandler: FileHandler
) : DataMigrationOperation {

    override fun run() {
        TODO("Not yet implemented")
    }

}
