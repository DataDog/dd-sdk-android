package com.datadog.android.sample.start

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface AppInfoRepo {
    fun streaming(): Flow<AppStartInfoBean>

    companion object {
        fun create(context: Context): AppInfoRepo {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                ProdAppInfoRepo(context)
            } else {
                StubAppInfoRepo
            }
        }
    }

}

object StubAppInfoRepo : AppInfoRepo {
    override fun streaming(): Flow<AppStartInfoBean> {
        return emptyFlow()
    }
}