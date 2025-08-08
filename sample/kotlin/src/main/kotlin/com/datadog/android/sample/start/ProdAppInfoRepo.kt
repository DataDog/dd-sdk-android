package com.datadog.android.sample.start

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.datadog.android.sample.start.AppInfoRepo
import com.datadog.android.sample.start.AppStartInfoBean
import com.datadog.android.sample.start.AppStartInfoMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class ProdAppInfoRepo(context: Context) : AppInfoRepo {
  private val behaviorSubject = MutableStateFlow<AppStartInfoBean?>(null)

  init {
    activityManager(context).addApplicationStartInfoCompletionListener(Executors.newSingleThreadExecutor()) {
      behaviorSubject.tryEmit(AppStartInfoMapper.mapStartInfo(it))
    }
  }

  private fun activityManager(context: Context): ActivityManager {
    return context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
  }

  override fun streaming(): Flow<AppStartInfoBean> {
    return behaviorSubject.filterNotNull()
  }
}