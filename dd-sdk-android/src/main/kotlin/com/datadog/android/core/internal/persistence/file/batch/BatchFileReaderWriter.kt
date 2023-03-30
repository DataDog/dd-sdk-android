/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file.batch

import com.datadog.android.core.internal.persistence.file.FileWriter
import com.datadog.android.security.Encryption
import com.datadog.android.v2.api.InternalLogger

internal interface BatchFileReaderWriter : FileWriter, BatchFileReader {

    companion object {
        /**
         * Creates either plain [PlainBatchFileReaderWriter] or [PlainBatchFileReaderWriter] wrapped in
         * [EncryptedBatchReaderWriter] if encryption is provided.
         */
        fun create(internalLogger: InternalLogger, encryption: Encryption?): BatchFileReaderWriter {
            return if (encryption == null) {
                PlainBatchFileReaderWriter(internalLogger)
            } else {
                EncryptedBatchReaderWriter(
                    encryption,
                    PlainBatchFileReaderWriter(internalLogger),
                    internalLogger
                )
            }
        }
    }
}
