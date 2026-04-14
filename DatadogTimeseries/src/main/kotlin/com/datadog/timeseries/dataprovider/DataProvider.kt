package com.datadog.timeseries.dataprovider

import com.datadog.timeseries.models.Sample

interface DataProvider {
    fun read(): Sample?
}
