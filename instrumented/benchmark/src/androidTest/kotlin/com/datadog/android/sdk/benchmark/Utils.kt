package com.datadog.android.sdk.benchmark

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InvalidObjectException
import okhttp3.mockwebserver.MockResponse

internal fun Forge.aThrowable(): Throwable {
    val errorMessage = anAlphabeticalString()
    return anElementFrom(
        IOException(errorMessage),
        IllegalStateException(errorMessage),
        UnknownError(errorMessage),
        ArrayIndexOutOfBoundsException(errorMessage),
        NullPointerException(errorMessage),
        ForgeryException(errorMessage),
        InvalidObjectException(errorMessage),
        UnsupportedOperationException(errorMessage),
        FileNotFoundException(errorMessage)
    )
}

internal fun mockResponse(code: Int): MockResponse {
    return MockResponse()
        .setResponseCode(code)
        .setBody("{}")
}
