package com.example

import com.datadog.tools.annotation.NoOpImplementation
import java.util.Date

@NoOpImplementation
interface EnumInterface {
    enum class WeekDay { MON, TUE, WED, THU, FRI, SAT, SUN}

    fun getWeekDay(): WeekDay
}
