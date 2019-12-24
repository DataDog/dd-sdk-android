package com.datadog.android.internal.utils

import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.net.NetworkInfo
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InvalidObjectException

// TODO Merge all these test utils with the ones in the unitTest flavour and move them in a
//  separated module
internal fun randomLog(forge: Forge): Log {
    return Log(
        serviceName = forge.anAlphabeticalString(),
        level = forge.anElementFrom(
            0, 1,
            android.util.Log.VERBOSE, android.util.Log.DEBUG,
            android.util.Log.INFO, android.util.Log.WARN,
            android.util.Log.ERROR, android.util.Log.ASSERT
        ),
        message = forge.anAlphabeticalString(),
        timestamp = forge.aLong(),
        throwable = forge.aThrowable(),
        attributes = forge.aMap(forge.anInt(max = 20)) {
            Pair(
                this.anAlphabeticalString(),
                this.anAlphabeticalString()
            )
        },
        tags = forge.aList { forge.anAlphabeticalString() },
        networkInfo = NetworkInfo(
            carrierName = forge.anAlphaNumericalString(),
            carrierId = forge.anInt()
        ),
        loggerName = forge.anAlphabeticalString(),
        threadName = forge.anAlphabeticalString()
    )
}

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

internal inline fun <reified T> Any.fieldValue(fieldName: String): T {
    val field = this.javaClass.getDeclaredField(fieldName)
    field.isAccessible = true
    return field.get(this) as T
}
