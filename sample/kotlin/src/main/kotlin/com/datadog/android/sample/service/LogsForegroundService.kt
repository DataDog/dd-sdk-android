/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.datadog.android.log.Logger
import com.datadog.android.sample.BuildConfig
import com.datadog.android.sample.R

class LogsForegroundService : Service() {

    private val logger: Logger by lazy {
        Logger.Builder()
            .setServiceName("android-sample-kotlin")
            .setLoggerName("foreground_service")
            .setLogcatLogsEnabled(true)
            .build()
            .apply {
                addTag("flavor", BuildConfig.FLAVOR)
                addTag("build_type", BuildConfig.BUILD_TYPE)
            }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            SEND_LOG_ACTION -> {
                logger.d("Message from Foreground service")
            }
            STOP_SERVICE_ACTION -> {
                stopForegroundService()
            }
            else -> {
                startForegroundService()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopForegroundService() {
        stopForeground(true)
        stopSelf()
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val bigTextStyle = NotificationCompat.BigTextStyle()
            .bigText(getString(R.string.foreground_service_desc))
            .setBigContentTitle(getString(R.string.foreground_service_title))

        val sendLogIntent = Intent(this, LogsForegroundService::class.java)
        sendLogIntent.action = SEND_LOG_ACTION
        val sendLogPendingIntent = PendingIntent.getService(
            this,
            0,
            sendLogIntent,
            0
        )

        val stopServiceIntent = Intent(this, LogsForegroundService::class.java)
        stopServiceIntent.action = STOP_SERVICE_ACTION
        val stopServicePendingIntent = PendingIntent.getService(
            this,
            0,
            stopServiceIntent,
            0
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setStyle(bigTextStyle)
            .setSmallIcon(R.drawable.ic_logs_black_24dp)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Send Log",
                sendLogPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Service",
                stopServicePendingIntent
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SampleApp Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val CHANNEL_ID = "LogsServiceChannel"
        const val NOTIFICATION_ID = 1
        const val SEND_LOG_ACTION = "SEND_LOG"
        const val STOP_SERVICE_ACTION = "STOP_SERVICE"
    }
}
