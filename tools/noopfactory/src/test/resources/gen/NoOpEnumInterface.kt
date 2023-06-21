@file:Suppress("ktlint")

package com.example

import kotlin.Suppress

@Suppress("RedundantUnitReturnType")
internal class NoOpEnumInterface : EnumInterface {
    public override val weekDay: EnumInterface.WeekDay = EnumInterface.WeekDay.MON

    public override fun getWeekDay(): EnumInterface.WeekDay = EnumInterface.WeekDay.MON
}
