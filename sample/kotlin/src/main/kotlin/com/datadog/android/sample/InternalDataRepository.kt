package com.datadog.android.sample

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random
import androidx.core.graphics.createBitmap
import com.datadog.android.okhttp.DatadogInterceptor
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

/**
 * HeavyComputationInjector: Component designed to inject Android startup bad practices
 * at application startup to test performance monitoring and identify bottlenecks.
 *
 * This class randomly selects 0-5 bad practices from the top 5 Android startup performance
 * anti-patterns and injects them to simulate real-world performance issues.
 *
 * The 5 bad practices are based on industry research (2024-2025):
 * 1. Heavy Synchronous Work in Application.onCreate()
 * 2. Synchronous Disk I/O and Database Operations on Main Thread
 * 3. WebView Initialization During Startup
 * 4. Complex Layout Inflation and View Hierarchy in Launch Activity
 * 5. Blocking Network Calls and Unnecessary Data Fetching at Startup
 *
 * Why this component exists:
 * - Testing application performance under various startup bad practices
 * - Identifying performance monitoring capabilities in observability tools
 * - Simulating real-world scenarios where common anti-patterns occur
 * - Benchmarking application startup time with different bottlenecks
 * - Validating RUM session tagging for each bad practice type
 */
class InternalDataRepository {

    companion object{
        internal val instance: InternalDataRepository by lazy { InternalDataRepository() }
    }

    /**
     * Sealed class representing the 7 bad practices that can be injected.
     * Each practice has a label used for RUM session tagging.
     */

    /**
     * Public API methods to trigger specific heavy computation scenarios manually.
     * These are designed to be called from the debug overlay UI.
     */

    fun loadInternalData(context: Context) {
        executeHeavySynchronousWork()
    }

    fun preprocessInternalData(context: Context) {
        executeSynchronousDiskIO(context)
    }

    fun injectWebViewInitialization(context: Context) {
        executeWebViewInitialization(context)
    }

    fun injectComplexLayoutInflation(context: Context) {
        executeComplexLayoutInflation(context)
    }

    fun injectBlockingNetworkCalls(context: Context) {
        executeBlockingNetworkCalls()
    }

    fun injectDistantBackendCDN(context: Context) {
        executeDistantBackendCDN()
    }

    fun injectSlowApiProcessing(context: Context) {
        executeSlowApiProcessing()
    }

    // ============================================================================
    // BAD PRACTICE #1: Heavy Synchronous Work in Application.onCreate()
    // ============================================================================
    /**
     * Simulates heavy synchronous CPU and memory intensive work during startup.
     * This includes:
     * - CPU-intensive operations (prime number generation, matrix multiplication)
     * - Memory-intensive operations (large object allocations)
     * - Complex data processing on main thread
     *
     * Impact: 200-800ms added to startup time, blocks main thread
     */
    private fun executeHeavySynchronousWork() {
        // CPU-intensive: Prime number calculation
        val primes = mutableListOf<Int>()
        for (number in 2..15000) {
            var isPrime = true
            for (divisor in 2..sqrt(number.toDouble()).toInt()) {
                if (number % divisor == 0) {
                    isPrime = false
                    break
                }
            }
            if (isPrime) {
                primes.add(number)
            }
        }

        // Memory-intensive: Large data structure allocation
        val largeList = mutableListOf<Map<String, Any>>()
        for (i in 0 until 25000) {
            val complexObject = mapOf(
                "id" to i,
                "name" to "Object_$i",
                "timestamp" to System.currentTimeMillis(),
                "data" to ByteArray(512) { Random.nextInt(256).toByte() },
                "metadata" to mapOf(
                    "type" to "heavy_sync_work",
                    "index" to i.toString()
                )
            )
            largeList.add(complexObject)
        }

        // Sort and filter operations on large collection
        val processed = largeList.sortedBy { it["id"] as Int }
            .filter { (it["id"] as Int) % 2 == 0 }
            .take(5000)
    }

    // ============================================================================
    // BAD PRACTICE #2: Synchronous Disk I/O and Database Operations on Main Thread
    // ============================================================================
    /**
     * Simulates synchronous disk I/O operations during startup.
     * This includes:
     * - Reading/writing files synchronously
     * - SharedPreferences operations (simulated)
     * - Large data file operations
     *
     * Impact: 100-500ms added to startup time (device dependent), blocks main thread
     */
    private fun executeSynchronousDiskIO(context: Context) {
        val cacheDir = context.cacheDir
        val createdFiles = mutableListOf<File>()

        try {
            // Simulate reading SharedPreferences (file I/O)
            val prefsFile = File(cacheDir, "startup_prefs.dat")
            prefsFile.writeText("user_id=12345\ntheme=dark\nlanguage=en\n")
            val prefsContent = prefsFile.readText()

            // Create multiple files with substantial content (simulate cache/config loading)
            repeat(15) { i ->
                val file = File(cacheDir, "startup_data_$i.dat")
                val content = StringBuilder()

                // Generate ~500KB per file
                repeat(512) { j ->
                    content.append("Line $j: ${Random.nextBytes(1000).joinToString("")}\n")
                }

                file.writeText(content.toString())
                createdFiles.add(file)
            }

            // Read and process files (simulate config parsing)
            createdFiles.forEach { file ->
                val lines = file.readLines()
                lines.filter { it.length > 50 }
                    .take(100)
            }

            // Cleanup
            createdFiles.forEach { it.delete() }
            prefsFile.delete()
        } catch (e: Exception) {
            // Cleanup on error
            createdFiles.forEach {
                try {
                    it.delete()
                } catch (_: Exception) { }
            }
        }
    }

    // ============================================================================
    // BAD PRACTICE #3: WebView Initialization During Startup
    // ============================================================================
    /**
     * Initializes WebView(s) during application startup - a known performance anti-pattern.
     *
     * WebView initialization is extremely heavy and includes:
     * - Loading native Chromium libraries
     * - Initializing JavaScript engine
     * - Setting up rendering context
     * - Creating default WebView settings
     * - Allocating significant memory
     *
     * This is a REAL bad practice that many apps do incorrectly. WebView initialization
     * should ideally be:
     * - Deferred until actually needed
     * - Done on a background thread (for pre-warming)
     * - Lazy loaded only when the user navigates to a web-based feature
     *
     * Impact: 200-800ms added to startup time (device and Android version dependent)
     *         - Low-end devices: 500-800ms
     *         - Mid-range devices: 300-500ms
     *         - High-end devices: 200-350ms
     *
     * This bad practice is particularly realistic because:
     * - Many apps use WebViews for login flows, terms of service, help content
     * - Developers often initialize WebView early thinking it will speed up later loading
     * - The actual overhead is not obvious until profiling
     */
    private fun executeWebViewInitialization(context: Context) {
        try {
            // Create 2-3 WebView instances (simulates apps that have multiple web-based features)
            val numberOfWebViews = Random.nextInt(2, 4)
            val webViews = mutableListOf<WebView>()

            repeat(numberOfWebViews) { index ->
                // Create WebView - this is where the heavy initialization happens
                val webView = WebView(context)

                // Configure WebView settings (common pattern in real apps)
                // This adds additional overhead on top of initialization
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true

                    // These settings force additional initialization
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }

                // Some apps even load about:blank or a placeholder URL during init
                // This makes it even slower
                if (index == 0) {
                    webView.loadData("<html><body>Loading...</body></html>", "text/html", "UTF-8")
                }

                webViews.add(webView)
            }

            // Cleanup - in real scenarios, these might leak if not properly managed
            webViews.forEach { webView ->
                try {
                    webView.destroy()
                } catch (e: Exception) {
                    // Handle errors gracefully
                }
            }
        } catch (e: Exception) {
            // Handle WebView initialization errors
            // (can happen on some Android versions or devices)
        }
    }

    // ============================================================================
    // BAD PRACTICE #4: Complex Layout Inflation and View Hierarchy in Launch Activity
    // ============================================================================
    /**
     * Simulates complex layout inflation during startup.
     * This includes:
     * - Creating deeply nested view hierarchies
     * - Inflating multiple complex layouts
     * - Bitmap operations for images
     *
     * Impact: 100-400ms added to startup time, blocks main thread during layout inflation
     */
    private fun executeComplexLayoutInflation(context: Context) {
        try {
            // Create deeply nested view hierarchies programmatically
            // Simulates inflating complex XML layouts

            repeat(10) { layoutIndex ->
                // Create root LinearLayout (simulates deeply nested XML)
                val rootLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                // Create deeply nested structure (5 levels deep)
                var currentParent: ViewGroup = rootLayout
                repeat(5) { level ->
                    val nestedLayout = LinearLayout(context).apply {
                        orientation = if (level % 2 == 0) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    // Add multiple children at each level
                    repeat(10) { childIndex ->
                        val textView = TextView(context).apply {
                            text = "Layout $layoutIndex, Level $level, Child $childIndex"
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                        nestedLayout.addView(textView)
                    }

                    currentParent.addView(nestedLayout)
                    currentParent = nestedLayout
                }

                // Force measure and layout (simulates actual layout inflation overhead)
                rootLayout.measure(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                rootLayout.layout(0, 0, 1080, 1920)
            }

            // Simulate bitmap loading/decoding (common in startup screens)
            val bitmaps = mutableListOf<Bitmap>()
            repeat(20) { i ->
                val bitmap = createBitmap(200, 200)
                // Fill with color data (simulates decoding)
                for (x in 0 until 200 step 10) {
                    for (y in 0 until 200 step 10) {
                        val color = Color.rgb(
                            Random.nextInt(256),
                            Random.nextInt(256),
                            Random.nextInt(256)
                        )
                        bitmap.setPixel(x, y, color)
                    }
                }
                bitmaps.add(bitmap)
            }

            // Cleanup
            bitmaps.forEach { it.recycle() }
        } catch (e: Exception) {
            // Handle errors gracefully
        }
    }

    // ============================================================================
    // BAD PRACTICE #5: Blocking Network Calls and Unnecessary Data Fetching at Startup
    // ============================================================================
    /**
     * Performs blocking network calls during startup using real HTTP requests.
     * This includes:
     * - Synchronous network requests to public APIs
     * - Config/feature flag fetching
     * - User data loading
     * - JSON parsing of network responses
     *
     * Impact: 200-2000ms+ added to startup time (highly variable, network dependent)
     * Note: Uses real OkHttp calls to public APIs (JSONPlaceholder, httpbin.org, etc.)
     */
    private fun executeBlockingNetworkCalls() {
        // Create OkHttp client with reasonable timeouts
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(DatadogInterceptor.Builder(listOf(
                "typicode.com",
                "httpbin.org",
                "github.com",
                "dog.ceo",
                "coindesk.com"
            )).build()    )
            .build()

        // List of public API endpoints to call
        val publicApiUrls = listOf(
            // JSONPlaceholder - Free fake API for testing
            "https://jsonplaceholder.typicode.com/posts/1",
            "https://jsonplaceholder.typicode.com/users/1",
            "https://jsonplaceholder.typicode.com/comments?postId=1",

            // httpbin.org - HTTP Request & Response Service
            "https://httpbin.org/uuid",
            "https://httpbin.org/json",

            // Additional public APIs
            "https://api.github.com/zen", // GitHub Zen quote
            "https://dog.ceo/api/breeds/image/random", // Random dog image API
            "https://api.coindesk.com/v1/bpi/currentprice.json" // Bitcoin price API
        )

        // Randomly select 3-5 APIs to call
        val numberOfCalls = Random.nextInt(3, 6)
        val selectedUrls = publicApiUrls.shuffled().take(numberOfCalls)

        // Make synchronous blocking calls
        selectedUrls.forEach { url ->
            makeBlockingNetworkCall(client, url)
        }
    }

    /**
     * Helper function to make a blocking (synchronous) network call using OkHttp.
     * This deliberately blocks the main thread to simulate the bad practice.
     *
     * @param client The OkHttpClient instance
     * @param url The URL to call
     */
    private fun makeBlockingNetworkCall(client: OkHttpClient, url: String) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            // Execute synchronously (blocks the calling thread)
            val response = client.newCall(request).execute()

            // Read the response body (this also takes time)
            val responseBody = response.body?.string()

            // Parse the response as JSON (adds additional processing time)
            if (responseBody != null && responseBody.isNotEmpty()) {
                try {
                    if (responseBody.trim().startsWith("{")) {
                        JSONObject(responseBody)
                    } else if (responseBody.trim().startsWith("[")) {
                        JSONArray(responseBody)
                    }
                } catch (e: Exception) {
                    // Ignore JSON parsing errors
                }
            }

            response.close()
        } catch (e: Exception) {
            // Handle network errors gracefully (timeout, no connection, etc.)
            // In a real bad practice scenario, this might not be handled at all
        }
    }

    /**
     * Helper function to make an asynchronous (non-blocking) network call using OkHttp.
     * This uses OkHttp's enqueue method to make the call on a background thread,
     * which is the proper way to make network calls in Android.
     *
     * @param client The OkHttpClient instance
     * @param url The URL to call
     */
    private fun makeAsyncNetworkCall(client: OkHttpClient, url: String) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            // Execute asynchronously (does not block the calling thread)
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Handle network errors gracefully (timeout, no connection, etc.)
                    // In a real scenario, this might trigger retry logic or error handling
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        // Read the response body
                        val responseBody = response.body?.string()

                        // Parse the response as JSON (adds additional processing time)
                        if (responseBody != null && responseBody.isNotEmpty()) {
                            try {
                                if (responseBody.trim().startsWith("{")) {
                                    JSONObject(responseBody)
                                } else if (responseBody.trim().startsWith("[")) {
                                    JSONArray(responseBody)
                                }
                            } catch (e: Exception) {
                                // Ignore JSON parsing errors
                            }
                        }
                    } finally {
                        response.close()
                    }
                }
            })
        } catch (e: Exception) {
            // Handle errors in creating the request
        }
    }

    // ============================================================================
    // BAD PRACTICE #6: Distant Backend/CDN - High Network Latency
    // ============================================================================
    /**
     * Performs network calls to geographically distant servers during startup.
     * This simulates the real-world scenario where apps call backends or CDN endpoints
     * that are located far from the user's geographic location, resulting in high network
     * latency due to physical distance.
     *
     * Why this is a bad practice:
     * - Network latency increases with physical distance between client and server
     * - Round-trip time (RTT) to distant servers can add 200-1000ms+ per request
     * - This is especially problematic during app startup even when done asynchronously
     * - Many apps inadvertently call non-geo-distributed APIs during initialization
     *
     * Real-world examples:
     * - Calling a single-region backend from a different continent
     * - Not using CDN or edge computing for static assets
     * - Fetching config/feature flags from distant servers
     * - Loading user profiles or auth tokens from far away data centers
     *
     * This implementation uses AWS Lambda endpoints in various distant regions:
     * - AP Southeast 2 (Sydney, Australia)
     * - US West 2 (Oregon, USA)
     * - EU West 1 (Ireland)
     * - AP Northeast 1 (Tokyo, Japan)
     *
     * Impact: 500-2000ms+ added to startup time (highly variable based on user location and distance)
     * Note: The actual latency depends on the physical distance between device and server
     */
    private fun executeDistantBackendCDN() {
        val trackedHosts = listOf("6naampjwjusykzufomduafjm2u0mykdo.lambda-url.ap-southeast-2.on.aws","typicode.com","github.com","dog.ceo")
        // Create OkHttp client with generous timeouts (distant calls take longer)
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(DatadogInterceptor.Builder(trackedHosts).build())
            .build()

        // List of geographically distributed Lambda endpoints
        // These are real AWS Lambda endpoints in different regions to simulate distance
        val distantApiUrls = listOf(
            // AWS Lambda in AP Southeast 2 (Sydney, Australia) - as shown in Swift example
            "https://6naampjwjusykzufomduafjm2u0mykdo.lambda-url.ap-southeast-2.on.aws/",

            // Additional distant endpoints for testing various geographic locations
            // JSONPlaceholder endpoints (hosted in various locations)
            "https://jsonplaceholder.typicode.com/todos/1",
            "https://jsonplaceholder.typicode.com/users/1",

            // GitHub API (helps simulate CDN with global distribution)
            "https://api.github.com/zen",

            // Dog CEO API (hosted in US)
            "https://dog.ceo/api/breeds/image/random"
        )

        // Randomly select 2-3 distant endpoints to call
        val numberOfCalls = Random.nextInt(2, 4)
        val selectedUrls = distantApiUrls.shuffled().take(numberOfCalls)

        // Make asynchronous calls to distant servers
        selectedUrls.forEach { url ->
            makeAsyncNetworkCall(client, url)
        }
    }

    // ============================================================================
    // BAD PRACTICE #7: Slow API Processing - Server-Side Delays
    // ============================================================================
    /**
     * Performs network calls to endpoints that have intentional server-side
     * processing delays. This simulates the real-world scenario where backend APIs
     * take a long time to process requests due to:
     * - Complex database queries
     * - Heavy computational work on the server
     * - Slow third-party API integrations
     * - Overloaded or under-provisioned backend services
     *
     * Why this is a bad practice:
     * - Even with good network latency, slow backend processing impacts app performance
     * - Users experience the combined effect of network latency + processing time
     * - During app startup, this compounds with other initialization tasks
     * - Calls to slow APIs can delay app initialization and user interactions
     *
     * Real-world examples:
     * - Loading user profile data from a slow database query
     * - Fetching feature flags that require complex authorization checks
     * - Calling analytics endpoints that perform heavy aggregations
     * - Retrieving configuration that involves multiple service calls
     * - Authentication/authorization checks with slow identity providers
     *
     * This implementation uses httpbin.org/delay endpoints which introduce
     * server-side delays to simulate slow API processing.
     *
     * Impact: 1000-5000ms+ added to startup time
     * Note: This simulates the server-side processing time, independent of network latency
     */
    private fun executeSlowApiProcessing() {
        // Create OkHttp client with very generous timeouts for slow APIs
        val trackedHosts = listOf(
            "httpbin.org",
            "typicode.com",
        )
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(DatadogInterceptor.Builder(trackedHosts).build())
            .build()

        // List of endpoints with intentional server-side delays
        // httpbin.org/delay/{n} waits n seconds before responding
        val slowApiUrls = listOf(
            // 1-3 second delays (simulates moderately slow APIs)
            "https://httpbin.org/delay/1",
            "https://httpbin.org/delay/2",
            "https://httpbin.org/delay/3",

            // Alternative: JSONPlaceholder with artificial delay simulation
            // (we'll make multiple calls to simulate cumulative slow processing)
            "https://jsonplaceholder.typicode.com/posts/1",
            "https://jsonplaceholder.typicode.com/users/1"
        )

        // Randomly select 2-3 slow endpoints to call
        // This ensures we get 2-6+ seconds of total delay
        val numberOfCalls = Random.nextInt(2, 4)
        val selectedUrls = slowApiUrls.shuffled().take(numberOfCalls)

        // Make asynchronous calls to slow-processing endpoints
        selectedUrls.forEach { url ->
            makeAsyncNetworkCall(client, url)
        }
    }

}