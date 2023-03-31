/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import com.datadog.android.security.Encryption
import com.datadog.android.v2.api.InternalLogger

internal interface FileReaderWriter : FileWriter, FileReader {
    companion object {

        /**
         * Creates either plain [PlainFileReaderWriter] or [PlainFileReaderWriter] wrapped in
         * [EncryptedFileReaderWriter] if encryption is provided.
         */
        fun create(internalLogger: InternalLogger, encryption: Encryption?): FileReaderWriter {
            return if (encryption == null) {
                PlainFileReaderWriter(internalLogger)
            } else {
                EncryptedFileReaderWriter(
                    encryption,
                    PlainFileReaderWriter(internalLogger),
                    internalLogger
                )
            }
        }
    }
}
