package com.datadog.android.internal.file

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.core.internal.data.file.FileReader
import com.datadog.android.core.internal.data.file.FileWriter
import com.datadog.android.core.internal.threading.AndroidDeferredHandler
import com.datadog.android.internal.utils.fieldValue
import com.datadog.android.internal.utils.randomLog
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.file.LogFileStrategy
import com.datadog.tools.unit.invokeMethod
import fr.xgouchet.elmyr.junit4.ForgeRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class LogFileStrategyBenchmarking {

    @get:Rule
    val benchmark = BenchmarkRule()
    @get:Rule
    val forge = ForgeRule()

    lateinit var logFileWriter: FileWriter<Log>
    lateinit var logFileReader: FileReader

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().context
        Datadog.initialize(context, "NO_TOKEN", "")
        Datadog.fieldValue<HandlerThread>("handlerThread").quit()
        logFileWriter = Datadog.getLogStrategy().getLogWriter() as FileWriter<Log>
        logFileWriter.quit() // we don't want this thread to run
        val dummyHandler = Handler(Looper.getMainLooper()) // this will never be used
        logFileWriter.deferredHandler =
            object : AndroidDeferredHandler(dummyHandler) {
                override fun handle(r: Runnable) {
                    r.run()
                }
            }
        logFileReader = Datadog.getLogStrategy().getLogReader() as FileReader
    }

    @After
    fun tearDown() {
        Datadog.invokeMethod("stop")
        InstrumentationRegistry.getInstrumentation().context.filesDir.deleteRecursively()
    }

    @Test
    fun benchmark_heavy_writing() {
        benchmark.measureRepeated {
            var counter = 0
            do {
                val log = runWithTimingDisabled {
                    randomLog(forge)
                }
                logFileWriter.write(log)
                counter++
            } while (counter < LogFileStrategy.MAX_LOG_PER_BATCH)
        }
    }

    @Test
    fun benchmark_heavy_reading() {
        // prepare by writing a full batch of logs
        val numberOfBatches = 2
        writeLogBatches(numberOfBatches)
        benchmark.measureRepeated {
            var counter = numberOfBatches
            while (counter -- > 0) {
                logFileReader.readNextBatch()
            }
        }
    }

    private fun writeLogBatches(numberOfBatches: Int) {
        var counter = 0
        do {
            writeLogBatch()
            counter++
        } while (counter < numberOfBatches)
    }

    private fun writeLogBatch() {
        var counter = 0
        do {
            val log = randomLog(forge)
            logFileWriter.write(log)
            counter++
        } while (counter < LogFileStrategy.MAX_LOG_PER_BATCH)
    }
}
