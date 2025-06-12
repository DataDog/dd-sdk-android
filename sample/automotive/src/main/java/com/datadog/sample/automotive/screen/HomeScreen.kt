/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.sample.automotive.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import com.datadog.sample.automotive.SharedLogger

class HomeScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        SharedLogger.logger.i("onGetTemplate(HomeScreen)")
        monitorGetTemplate()

        val row = Row.Builder().setTitle("Hello world!").build()
        val pane = Pane.Builder().addRow(row).build()
        return PaneTemplate.Builder(pane)
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Next screen")
                            .setMonitoredClickListener {
                                SharedLogger.logger.i("onClick Next Screen")
                                screenManager.push(SecondScreen(carContext))
                            }
                            .build()
                    )
                    .build()
            )
            .build()
    }
}
