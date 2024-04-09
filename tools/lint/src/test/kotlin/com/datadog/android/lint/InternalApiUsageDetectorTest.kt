/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.lint

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.jetbrains.kotlin.konan.file.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
internal class InternalApiUsageDetectorTest {

    @StringForgery(regex = "(com|org|fr)\\.[a-z]{1,10}")
    lateinit var nonDatadogPackage: String

    @StringForgery(regex = "com\\.datadog(|\\.[a-z]{1,13})")
    lateinit var datadogPackage: String

    @Test
    fun `M report issue W internal class is used from non-Datadog package { java }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalClassKotlinStub,
                java(
                    """
                package $nonDatadogPackage;
                
                import com.datadog.android.InternalSdkClass;
                
                public class ClassUnderTest { 
                
                    public void testMethod() {
                       InternalSdkClass instance = new InternalSdkClass();
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expect(
                """
                    src/${nonDatadogPackage.packageToPath()}/ClassUnderTest.java:8: Error: Symbols annotated with com.datadog.android.lint.InternalApi shouldn't be used outside of Datadog SDK packages. [DatadogInternalApiUsage]
                           InternalSdkClass instance = new InternalSdkClass();
                                                       ~~~~~~~~~~~~~~~~~~~~~~
                    1 errors, 0 warnings
                """
            )
    }

    @Test
    fun `M report issue W internal method is used from non-Datadog package { java }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalMethodKotlinStub,
                java(
                    """
                package $nonDatadogPackage;
                
                import com.datadog.android.SomeSdkClass;
                
                public class ClassUnderTest { 
                
                    public void testMethod() {
                       SomeSdkClass instance = new SomeSdkClass();
                       instance.internalMethod();
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expect(
                """
                    src/${nonDatadogPackage.packageToPath()}/ClassUnderTest.java:9: Error: Symbols annotated with com.datadog.android.lint.InternalApi shouldn't be used outside of Datadog SDK packages. [DatadogInternalApiUsage]
                           instance.internalMethod();
                           ~~~~~~~~~~~~~~~~~~~~~~~~~
                    1 errors, 0 warnings
                """
            )
    }

    @Test
    fun `M report issue W internal property is used from non-Datadog package { java }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalPropertyKotlinStub,
                java(
                    """
                package $nonDatadogPackage;
                
                import com.datadog.android.SomeSdkClass;
                
                public class ClassUnderTest { 
                
                    public void testMethod() {
                       SomeSdkClass instance = new SomeSdkClass();
                       instance.getInternalProperty();
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expect(
                """
                    src/${nonDatadogPackage.packageToPath()}/ClassUnderTest.java:9: Error: Symbols annotated with com.datadog.android.lint.InternalApi shouldn't be used outside of Datadog SDK packages. [DatadogInternalApiUsage]
                           instance.getInternalProperty();
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    1 errors, 0 warnings
                """
            )
    }

    @Test
    fun `M report issue W method of internal interface is used from non-Datadog package { java }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalMethodInInterfaceKotlinStub,
                classImplementingInternalInterfaceKotlinStub,
                java(
                    """
                package $nonDatadogPackage;
                
                import com.datadog.android.SdkCore;
                
                public class ClassUnderTest { 
                
                    public void testMethod() {
                       SdkCore instance = new SdkCore();
                       instance.internalMethod();
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expect(
                """
                    src/${nonDatadogPackage.packageToPath()}/ClassUnderTest.java:9: Error: Symbols annotated with com.datadog.android.lint.InternalApi shouldn't be used outside of Datadog SDK packages. [DatadogInternalApiUsage]
                           instance.internalMethod();
                           ~~~~~~~~~~~~~~~~~~~~~~~~~
                    1 errors, 0 warnings
                """
            )
    }

    @Test
    fun `M report issue W internal extension method is used from non-Datadog package { java }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalExtensionMethodKotlinStub,
                java(
                    """
                package $nonDatadogPackage;
                
                import com.datadog.android.TestKt;
                
                public class ClassUnderTest { 
                
                    public void testMethod() {
                       TestKt.internalMethod("something");
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expect(
                """
                    src/${nonDatadogPackage.packageToPath()}/ClassUnderTest.java:8: Error: Symbols annotated with com.datadog.android.lint.InternalApi shouldn't be used outside of Datadog SDK packages. [DatadogInternalApiUsage]
                           TestKt.internalMethod("something");
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
                    1 errors, 0 warnings
                """
            )
    }

    @Test
    fun `M report issue W internal method in object class is used from non-Datadog package { java }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalMethodInObjectClassKotlinStub,
                java(
                    """
                package $nonDatadogPackage;
                
                import com.datadog.android.Datadog;
                
                public class ClassUnderTest { 
                
                    public void testMethod() {
                       Datadog._internalProxy();
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expect(
                """
                    src/${nonDatadogPackage.packageToPath()}/ClassUnderTest.java:8: Error: Symbols annotated with com.datadog.android.lint.InternalApi shouldn't be used outside of Datadog SDK packages. [DatadogInternalApiUsage]
                           Datadog._internalProxy();
                           ~~~~~~~~~~~~~~~~~~~~~~~~
                    1 errors, 0 warnings
                """
            )
    }

    @Test
    fun `M report issue W internal class is used from non-Datadog package { kotlin }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalClassKotlinStub,
                kotlin(
                    """
                package $nonDatadogPackage
                
                import com.datadog.android.InternalSdkClass
                
                class ClassUnderTest { 
                
                    fun testMethod() {
                       val instance = InternalSdkClass()
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expect(
                """
                   src/${nonDatadogPackage.packageToPath()}/ClassUnderTest.kt:8: Error: Symbols annotated with com.datadog.android.lint.InternalApi shouldn't be used outside of Datadog SDK packages. [DatadogInternalApiUsage]
                          val instance = InternalSdkClass()
                                         ~~~~~~~~~~~~~~~~~~
                   1 errors, 0 warnings
                """
            )
    }

    @Test
    fun `M report issue W internal method is used from non-Datadog package { kotlin }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalMethodKotlinStub,
                kotlin(
                    """
                package $nonDatadogPackage
                
                import com.datadog.android.SomeSdkClass
                
                class ClassUnderTest { 
                
                    fun testMethod() {
                       val instance = SomeSdkClass()
                       instance.internalMethod()
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expect(
                """
                   src/${nonDatadogPackage.packageToPath()}/ClassUnderTest.kt:9: Error: Symbols annotated with com.datadog.android.lint.InternalApi shouldn't be used outside of Datadog SDK packages. [DatadogInternalApiUsage]
                          instance.internalMethod()
                          ~~~~~~~~~~~~~~~~~~~~~~~~~
                   1 errors, 0 warnings
                """
            )
    }

    @Test
    fun `M report issue W internal property is used from non-Datadog package { kotlin }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalPropertyKotlinStub,
                kotlin(
                    """
                package $nonDatadogPackage
                
                import com.datadog.android.SomeSdkClass
                
                class ClassUnderTest { 
                
                    fun testMethod() {
                       val instance = SomeSdkClass()
                       instance.internalProperty
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expect(
                """
                   src/${nonDatadogPackage.packageToPath()}/ClassUnderTest.kt:9: Error: Symbols annotated with com.datadog.android.lint.InternalApi shouldn't be used outside of Datadog SDK packages. [DatadogInternalApiUsage]
                          instance.internalProperty
                                   ~~~~~~~~~~~~~~~~
                   1 errors, 0 warnings
                """
            )
    }

    @Test
    fun `M report issue W internal extension method is used from non-Datadog package { kotlin }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalExtensionMethodKotlinStub,
                kotlin(
                    """
                package $nonDatadogPackage
                
                import com.datadog.android.internalMethod
                
                class ClassUnderTest { 
                
                    fun testMethod() {
                       // lint will tun this twice in 2 different modes
                       // and reporting outputs will vary: with quotes vs without for reporting
                       // location if call is directly like "something".internalMethod()
                       // so creating temporary variable
                       val literal = "something"
                       literal.internalMethod()
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expect(
                """
                   src/${nonDatadogPackage.packageToPath()}/ClassUnderTest.kt:13: Error: Symbols annotated with com.datadog.android.lint.InternalApi shouldn't be used outside of Datadog SDK packages. [DatadogInternalApiUsage]
                          literal.internalMethod()
                          ~~~~~~~~~~~~~~~~~~~~~~~~
                   1 errors, 0 warnings
                """
            )
    }

    @Test
    fun `M report issue W internal method in object class is used from non-Datadog package { kotlin }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalMethodInObjectClassKotlinStub,
                kotlin(
                    """
                package $nonDatadogPackage
                
                import com.datadog.android.Datadog
                
                class ClassUnderTest { 
                
                    fun testMethod() {
                       Datadog._internalProxy()
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectErrorCount(1)
            .expect(
                """
                   src/${nonDatadogPackage.packageToPath()}/ClassUnderTest.kt:8: Error: Symbols annotated with com.datadog.android.lint.InternalApi shouldn't be used outside of Datadog SDK packages. [DatadogInternalApiUsage]
                          Datadog._internalProxy()
                          ~~~~~~~~~~~~~~~~~~~~~~~~
                   1 errors, 0 warnings
                """
            )
    }

    @Test
    fun `M not report issue W internal method is used from non-Datadog package { kotlin + package-less file }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalMethodKotlinStub,
                kotlin(
                    """                
                import com.datadog.android.SomeSdkClass
                
                class ClassUnderTest { 
                
                    fun testMethod() {
                       val instance = SomeSdkClass()
                       instance.internalMethod()
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `M not report issue W internal class is used from Datadog package { java }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalClassKotlinStub,
                java(
                    """
                package $datadogPackage;
                
                import com.datadog.android.InternalSdkClass;
                
                public class ClassUnderTest { 
                
                    public void testMethod() {
                       InternalSdkClass instance = new InternalSdkClass();
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `M not report issue W internal method is used from Datadog package { java }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalMethodKotlinStub,
                java(
                    """
                package $datadogPackage;
                
                import com.datadog.android.SomeSdkClass;
                
                public class ClassUnderTest { 
                
                    public void testMethod() {
                       SomeSdkClass instance = new SomeSdkClass();
                       instance.internalMethod();
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `M not report issue W internal property is used from Datadog package { java }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalPropertyKotlinStub,
                java(
                    """
                package $datadogPackage;
                
                import com.datadog.android.SomeSdkClass;
                
                public class ClassUnderTest { 
                
                    public void testMethod() {
                       SomeSdkClass instance = new SomeSdkClass();
                       instance.getInternalProperty();
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `M not report issue W internal extension method is used from Datadog package { java }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalExtensionMethodKotlinStub,
                java(
                    """
                package $datadogPackage;
                
                import com.datadog.android.TestKt;
                
                public class ClassUnderTest { 
                
                    public void testMethod() {
                       TestKt.internalMethod("something");
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `M not report issue W internal method in object class is used from Datadog package { java }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalMethodInObjectClassKotlinStub,
                java(
                    """
                package $datadogPackage;
                
                import com.datadog.android.Datadog;
                
                public class ClassUnderTest { 
                
                    public void testMethod() {
                       Datadog._internalProxy();
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `M not report issue W non-int method called from int interface impl from non-Datadog package { java }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalMethodInInterfaceKotlinStub,
                classImplementingInternalInterfaceKotlinStub,
                java(
                    """
                package $nonDatadogPackage;
                
                import com.datadog.android.SdkCore;
                
                public class ClassUnderTest { 
                
                    public void testMethod() {
                       SdkCore instance = new SdkCore();
                       instance.okayMethod();
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `M not report issue W internal class is used from Datadog package { kotlin }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalClassKotlinStub,
                kotlin(
                    """
                package $datadogPackage
                
                import com.datadog.android.InternalSdkClass
                
                class ClassUnderTest { 
                
                    fun testMethod() {
                       val instance = InternalSdkClass()
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `M not report issue W internal method is used from Datadog package { kotlin }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalMethodKotlinStub,
                kotlin(
                    """
                package $datadogPackage
                
                import com.datadog.android.SomeSdkClass
                
                class ClassUnderTest { 
                
                    fun testMethod() {
                       val instance = SomeSdkClass()
                       instance.internalMethod()
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `M not report issue W internal property is used from Datadog package { kotlin }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalPropertyKotlinStub,
                kotlin(
                    """
                package $datadogPackage
                
                import com.datadog.android.SomeSdkClass
                
                class ClassUnderTest { 
                
                    fun testMethod() {
                       val instance = SomeSdkClass()
                       instance.internalProperty
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `M not report issue W internal extension method is used from Datadog package { kotlin }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalExtensionMethodKotlinStub,
                kotlin(
                    """
                package $datadogPackage
                
                import com.datadog.android.internalMethod
                
                class ClassUnderTest { 
                
                    fun testMethod() {
                       "something".internalMethod()
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `M not report issue W internal method in object class is used from Datadog package { kotlin }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalMethodInObjectClassKotlinStub,
                kotlin(
                    """
                package $datadogPackage
                
                import com.datadog.android.Datadog
                
                class ClassUnderTest { 
                
                    fun testMethod() {
                       Datadog._internalProxy()
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    @Test
    fun `M not report issue W non-int method called from int interface impl from non-Datadog package { kotlin }`() {
        lint()
            .files(
                internalApiAnnotationStub,
                internalMethodInInterfaceKotlinStub,
                classImplementingInternalInterfaceKotlinStub,
                kotlin(
                    """
                package $nonDatadogPackage
                
                import com.datadog.android.SdkCore
                
                class ClassUnderTest { 
                
                    fun testMethod() {
                       val instance = SdkCore()
                       instance.okayMethod()
                    }
                
                }
                    """
                ).indented()
            )
            .issues(InternalApiUsageDetector.ISSUE)
            .run()
            .expectClean()
    }

    companion object {
        val internalApiAnnotationStub: TestFile = kotlin(
            """
                package com.datadog.android.lint
                
                @Retention(AnnotationRetention.BINARY)
                @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
                annotation class InternalApi
            """
        ).indented()

        val internalClassKotlinStub: TestFile = kotlin(
            """
                package com.datadog.android
                
                import com.datadog.android.lint.InternalApi;
                
                @InternalApi
                class InternalSdkClass
            """
        ).indented()

        val internalMethodInInterfaceKotlinStub: TestFile = kotlin(
            """
                package com.datadog.android

                import com.datadog.android.lint.InternalApi;

                interface InternalSdkCore {
                  @InternalApi
                  fun internalMethod()
                }

            """
        ).indented()

        val internalMethodInObjectClassKotlinStub: TestFile = kotlin(
            """
                package com.datadog.android

                import com.datadog.android.lint.InternalApi;
                import kotlin.jvm.JvmStatic;

                object Datadog {

                  @JvmStatic
                  @InternalApi
                  fun _internalProxy() = Unit
                }

            """
        ).indented()

        val classImplementingInternalInterfaceKotlinStub: TestFile = kotlin(
            """
                package com.datadog.android

                class SdkCore : InternalSdkCore {
                    override fun internalMethod() = Unit
                    fun okayMethod() = Unit
                }
            """
        ).indented()

        val internalMethodKotlinStub: TestFile = kotlin(
            """
                package com.datadog.android
                
                import com.datadog.android.lint.InternalApi;
                
                class SomeSdkClass {   
                  @InternalApi
                  fun internalMethod() = Unit
                }
            """
        ).indented()

        val internalPropertyKotlinStub: TestFile = kotlin(
            """
                package com.datadog.android
                
                import com.datadog.android.lint.InternalApi;
                
                class SomeSdkClass {   
                  @InternalApi
                  val internalProperty = 42
                }
            """
        ).indented()

        val internalExtensionMethodKotlinStub: TestFile = kotlin(
            """
                package com.datadog.android
                
                import com.datadog.android.lint.InternalApi;
                
                @InternalApi
                fun String.internalMethod() = Unit
            """
        ).indented()
    }

    // region private

    private fun String.packageToPath() = replace(".", File.separator)

    // endregion
}
