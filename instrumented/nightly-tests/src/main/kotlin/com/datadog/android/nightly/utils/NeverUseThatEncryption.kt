/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.utils

import com.datadog.android.security.Encryption
import kotlin.experimental.inv

/**
 * The only purpose of that one is to be able to test NDK crashes, because key in KeyStore is bound
 * to the certain app, so it cannot be shared between 2 apps (test runner vs app under test) without
 * additional steps like sharedUserId, Content Provider etc., so we won't be able to decrypt RUM
 * data to send it to the server from the test.
 */
class NeverUseThatEncryption : Encryption {
    override fun encrypt(data: ByteArray): ByteArray {
        return data.map { it.inv() }.toByteArray()
    }

    override fun decrypt(data: ByteArray): ByteArray {
        return data.map { it.inv() }.toByteArray()
    }
}
