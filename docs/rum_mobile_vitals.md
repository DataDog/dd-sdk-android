## Overview

Real User Monitoring offers Mobile Vitals, a set of metrics inspired by [Android Vitals][1], that can help compute insights about your mobile application's responsiveness, stability, and resource consumption. 

{{< img src="real_user_monitoring/android/mobile_vitals.png" alt="Mobile Vitals in the RUM Explorer" style="width:70%;">}}

Mobile Vitals appear in your application's **Overview** tab and in the side panel under **Performance** > **Event Timings and Mobile Vitals** when you click on an individual view in the [RUM Explorer][3]. Click on a graph in **Mobile Vitals** to apply a filter by version or examine filtered sessions. 

{{< img src="real_user_monitoring/android/refresh_rate_and_mobile_vitals.png" alt="Event Timings and Mobile Vitals" style="width:70%;">}}

Mobile Vitals include recommended benchmark ranges that correlate directly to your application's user experience. You can see where a metric scores on the range and click **Search Views With Poor Performance** to apply a filter in your search query.

## Metrics

The following metrics provide insight into your mobile application's performance.

| Measurement                    | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|--------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Slow renders                   | To ensure a smooth, [jank-free][5] user experience, your application should render frames in under 60 Hz. <br /><br />  RUM tracks the application’s [display refresh rate][6] using `@view.refresh_rate_average` and `@view.refresh_rate_min` view attributes. <br /><br />  With slow rendering, you can monitor which views are taking longer than 16 ms or 60 Hz to render. <br /> **Note:** Refresh rates are normalized on a range of zero to 60 fps. For example, if your application runs at 100 fps on a device capable of rendering 120 fps, Datadog reports 50 fps in Mobile Vitals. |
| Frozen frames                  | Frames that take longer than 700ms to render appear as stuck and unresponsive in your application. These are classified as [frozen frames][7]. <br /><br />  RUM tracks `long task` events with the duration for any task taking longer then 100ms to complete. <br /><br />  With frozen frames, you can monitor which views appear frozen (taking longer than 700ms to render) to your end users and eliminate jank in your application.                                                                                                                                                                                                 |
| Application not responding     | When the UI thread of an application is blocked for more than 5 seconds, an `Application Not Responding` ([ANR][8]) error triggers. If the application is in the foreground, the system displays a dialog modal to the user, allowing them to force quit the application. <br /><br />   RUM tracks ANR occurrences and captures the entire stack trace that blocks the main thread when it encounters an ANR.                                                                                                                                                                                                                              |
| Crash-free sessions by version | An [application crash][9] is reported due to an unexpected exit in the application typically caused by an unhandled exception or signal. Crash-free user sessions in your application directly correspond to  your end user’s experience and overall satisfaction. <br /><br />   RUM tracks complete crash reports and presents trends over time with [Error Tracking][10]. <br /><br />  With crash-free sessions, you can stay up to speed on industry benchmarks and ensure that your application is ranked highly on the Google Play Store.                                                                                                 |
| CPU ticks per second           | High CPU usage impacts the [battery life][11] on your users’ devices.  <br /><br />  RUM tracks CPU ticks per second for each view and the CPU utilization over the course of a session. The recommended range is <40 for good and <60 for moderate.                                                                                                                                                                                                                                                                                                                                                         |
| Memory utilization             | High memory usage can lead to [OutOfMemoryError][12], which causes the application to crash and creates a poor user experience. <br /><br />  RUM tracks the amount of physical memory used by your application in bytes for each view, over the course of a session. The recommended range is <200MB for good and <400MB for moderate.                                                                                                                                                                                                                                                                                          |

## Further Reading

{{< partial name="whats-next/whats-next.html" >}}

[1]: https://developer.android.com/topic/performance/vitals
[2]: https://raw.githubusercontent.com/DataDog/dd-sdk-android/alai97/android-monitoring-mobile-vitals/docs/images/mobile_vitals.png
[3]: https://app.datadoghq.com/rum/explorer
[4]: https://raw.githubusercontent.com/DataDog/dd-sdk-android/alai97/android-monitoring-mobile-vitals/docs/images/refresh_rate_and_mobile_vitals.png
[5]: https://developer.android.com/topic/performance/vitals/render#common-jank
[6]: https://developer.android.com/guide/topics/media/frame-rate
[7]: https://developer.android.com/topic/performance/vitals/frozen
[8]: https://developer.android.com/topic/performance/vitals/anr
[9]: https://developer.android.com/topic/performance/vitals/crash
[10]: https://docs.datadoghq.com/real_user_monitoring/error_tracking/android
[11]: https://developer.android.com/topic/performance/power
[12]: https://developer.android.com/reference/java/lang/OutOfMemoryError