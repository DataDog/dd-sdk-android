package com.datadog.android.log;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.datadog.android.BuildConfig;
import com.datadog.android.Datadog;
import com.datadog.android.utils.DatadogExtKt;
import com.datadog.tools.unit.ReflectUtilsKt;
import com.datadog.tools.unit.annotations.SystemOutStream;
import com.datadog.tools.unit.extensions.SystemStreamExtension;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extensions;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import java.io.ByteArrayOutputStream;
import java.io.File;

import fr.xgouchet.elmyr.Forge;
import fr.xgouchet.elmyr.annotation.Forgery;
import fr.xgouchet.elmyr.junit5.ForgeExtension;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static com.datadog.tools.unit.assertj.ByteArrayOutputStreamAssert.assertThat;


@Extensions(
        {@ExtendWith(MockitoExtension.class),
                @ExtendWith(ForgeExtension.class),
                @ExtendWith(SystemStreamExtension.class)})
@MockitoSettings()
class LoggerBuilderJavaTest {

    private Context mockContext;

    private String packageName;

    @BeforeEach
    void setUp(@Forgery Forge forge) {
        packageName = forge.anAlphabeticalString();
        mockContext = mockContext(packageName);
        Datadog.initialize(mockContext, forge.anHexadecimalString());
        Datadog.setVerbosity(Log.VERBOSE);
    }

    @AfterEach
    void tearDown() {
        try {
            ReflectUtilsKt.invokeMethod(Datadog.INSTANCE, "stop");
        } catch (IllegalStateException e) {
            // ignore
        }
    }

    @Test
    void builderCanEnableLogcatLogs(@Forgery Forge forge,
                                    @SystemOutStream ByteArrayOutputStream outputStream) {

        final boolean logcatLogsEnabled = true;
        final String fakeMessage = forge.anAlphabeticalString();
        final String fakeServiceName = forge.anAlphaNumericalString();

        final Logger logger = new Logger.Builder()
                .setLogcatLogsEnabled(logcatLogsEnabled)
                .setServiceName(fakeServiceName)
                .build();
        logger.v(fakeMessage);
        final String expectedTagName = DatadogExtKt.resolveTagName(this, fakeServiceName);


        assertThat(outputStream)
                .hasLogLine(Log.VERBOSE, expectedTagName, fakeMessage, false);
    }

    @Test
    void builderCanEnableOnlyLogcatLogs(@Forgery Forge forge,
                                        @SystemOutStream ByteArrayOutputStream outputStream) {

        final boolean logcatLogsEnabled = true;
        final String fakeMessage = forge.anAlphabeticalString();
        final String fakeServiceName = forge.anAlphaNumericalString();

        final Logger logger = new Logger.Builder()
                .setDatadogLogsEnabled(false)
                .setLogcatLogsEnabled(logcatLogsEnabled)
                .setServiceName(fakeServiceName)
                .build();
        logger.v(fakeMessage);
        final String expectedTagName = DatadogExtKt.resolveTagName(this, fakeServiceName);

        assertThat(outputStream)
                .hasLogLine(Log.VERBOSE, expectedTagName, fakeMessage, false);
    }

    private Context mockContext(String packageName) {
        final int versionCode = BuildConfig.VERSION_CODE;
        final String versionName = BuildConfig.VERSION_NAME;
        final PackageInfo mockPackageInfo = new PackageInfo();
        final PackageManager mockPackageMgr = mock(PackageManager.class);
        final Context mockContext = mock(Application.class);

        mockPackageInfo.versionName = versionName;
        mockPackageInfo.versionCode = versionCode;
        try {
            when(mockPackageMgr.getPackageInfo(packageName, 0)).thenReturn(mockPackageInfo);
        } catch (PackageManager.NameNotFoundException e) {
            // ignore
        }

        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mockContext.getPackageManager()).thenReturn(mockPackageMgr);
        final ApplicationInfo mockApplicationInfo = mock(ApplicationInfo.class);
        when(mockContext.getApplicationInfo()).thenReturn(mockApplicationInfo);
        if (BuildConfig.DEBUG) {
            mockApplicationInfo.flags =
                    ApplicationInfo.FLAG_DEBUGGABLE | ApplicationInfo.FLAG_ALLOW_BACKUP;
        }
        when(mockContext.getPackageName()).thenReturn(packageName);
        when(mockContext.getFilesDir()).thenReturn(new File("/dev/null"));
        return mockContext;
    }

}
