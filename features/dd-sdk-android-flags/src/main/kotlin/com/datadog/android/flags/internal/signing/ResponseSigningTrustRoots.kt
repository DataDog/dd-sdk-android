/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.signing

import com.datadog.android.DatadogSite

/** The pinned trust anchor. Responses never supply or replace it. */
internal object ResponseSigningTrustRoots {

    /**
     * `CN=Datadog Customer Signing Root CA - staging`, padded base64 DER.
     * SHA-256 of the DER: `0e469f3cecc9cd51fd64373879636540512d56e99eecd70361c78b98c47ea934`.
     * Subject key identifier: `aa5e91e52cdb130d175eed1010a501c0ef45d60d`. Valid from 2026-11-19 to 2036-11-16.
     */
    const val STAGING_ROOT: String =
        "MIIB0TCCAXigAwIBAgIUeuKLTmp+6/G7at2Gdr8kEvQXr+MwCgYIKoZIzj0EAwIwNTEzMDEGA1UEAxMqRGF0YWRvZyBDdXN0b21l" +
            "ciBTaWduaW5nIFJvb3QgQ0EgLSBzdGFnaW5nMB4XDTI2MTExOTIzNTYzOVoXDTM2MTExNjIzNTcwOVowNTEzMDEGA1UEAxMqRGF0" +
            "YWRvZyBDdXN0b21lciBTaWduaW5nIFJvb3QgQ0EgLSBzdGFnaW5nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEnqgqgmas0ot4" +
            "6nCUq418zkd+SCG5EzGmtxKAFSAT4rGTtmylNee/9Asw5nKeV5eLN5Ki7zZ/3wBgIBL/owvov6NmMGQwDgYDVR0PAQH/BAQDAgEG" +
            "MBIGA1UdEwEB/wQIMAYBAf8CAQIwHQYDVR0OBBYEFKpekeUs2xMNF17tEBClAcDvRdYNMB8GA1UdIwQYMBaAFKpekeUs2xMNF17t" +
            "EBClAcDvRdYNMAoGCCqGSM49BAMCA0cAMEQCIC6R3Cx6TgUbrKOr+jG2wvrbmNLt97tVUqEOne3nKrdtAiAXfSsjQefOVfiEFg+5" +
            "G3ksGZzZTkfOPxf0Zl1WOqmrgA=="

    /** Signing is available only when the SDK targets the staging site. Production has no anchor yet. */
    fun isAvailable(site: DatadogSite?): Boolean = site == DatadogSite.STAGING
}
