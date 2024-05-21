@file:Suppress("ktlint")

package com.example

import kotlin.Suppress

internal class NoOpEnumInterface : EnumInterface {
    override val weekDay: EnumInterface.WeekDay = EnumInterface.WeekDay.MON

    override fun getWeekDay(): EnumInterface.WeekDay = EnumInterface.WeekDay.MON
}
