/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.generated.DdSdkAndroidCoreLogger
import com.datadog.android.security.Encryption
import java.io.File

internal class EncryptedFileReaderWriter(
    internal val encryption: Encryption,
    internal val delegate: FileReaderWriter,
    private val internalLogger: InternalLogger
) : FileReaderWriter by delegate {

    @Suppress("ReturnCount")
    @WorkerThread
    override fun writeData(
        file: File,
        data: ByteArray,
        append: Boolean
    ): Boolean {
        if (append) {
            DdSdkAndroidCoreLogger(internalLogger).logAppendModeNotSupported()
            return false
        }

        val encryptedData = encryption.encrypt(data)

        if (data.isNotEmpty() && encryptedData.isEmpty()) {
            DdSdkAndroidCoreLogger(internalLogger).logEncryptedFileBadEncryptionResult()
            return false
        }

        return delegate.writeData(
            file,
            encryptedData,
            append
        )
    }

    @WorkerThread
    override fun readData(
        file: File
    ): ByteArray {
        return encryption.decrypt(delegate.readData(file))
    }

}
