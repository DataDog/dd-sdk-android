package com.example

import kotlin.Suppress

@Suppress("RedundantUnitReturnType")
internal class NoOpEnumInterface : EnumInterface {
    public override fun getWeekDay(): EnumInterface.WeekDay = EnumInterface.WeekDay.MON
}
