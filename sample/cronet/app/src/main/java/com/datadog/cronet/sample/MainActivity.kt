package com.datadog.cronet.sample

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ImageDownloaderScreen(
                        cronetEngine = (application as SampleApplication).cronetEngine,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun ImageDownloaderScreen(
    cronetEngine: CronetEngine,
    modifier: Modifier = Modifier
) {
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = {
                isLoading = true
                errorMessage = null
                downloadImage(
                    cronetEngine = cronetEngine,
                    url = "https://storage.googleapis.com/cronet/sun.jpg",
                    onSuccess = { bitmap ->
                        imageBitmap = bitmap
                        isLoading = false
                    },
                    onError = { error ->
                        errorMessage = error
                        isLoading = false
                    }
                )
            },
            enabled = !isLoading
        ) {
            Text("Download Image")
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                CircularProgressIndicator()
            }
            errorMessage != null -> {
                Text("Error: $errorMessage")
            }
            imageBitmap != null -> {
                Image(
                    bitmap = imageBitmap!!,
                    contentDescription = "Downloaded image",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun downloadImage(
    cronetEngine: CronetEngine,
    url: String,
    onSuccess: (ImageBitmap) -> Unit,
    onError: (String) -> Unit
) {
    val executor = Executors.newSingleThreadExecutor()
    val byteArrayOutputStream = mutableListOf<ByteArray>()

    val callback = object : UrlRequest.Callback() {
        override fun onRedirectReceived(
            request: UrlRequest,
            info: UrlResponseInfo,
            newLocationUrl: String
        ) {
            request.followRedirect()
        }

        override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
            request.read(ByteBuffer.allocateDirect(32 * 1024))
        }

        override fun onReadCompleted(
            request: UrlRequest,
            info: UrlResponseInfo,
            byteBuffer: ByteBuffer
        ) {
            byteBuffer.flip()
            val bytes = ByteArray(byteBuffer.remaining())
            byteBuffer.get(bytes)
            byteArrayOutputStream.add(bytes)
            byteBuffer.clear()
            request.read(byteBuffer)
        }

        override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
            val totalSize = byteArrayOutputStream.sumOf { it.size }
            val imageBytes = ByteArray(totalSize)
            var offset = 0
            for (chunk in byteArrayOutputStream) {
                System.arraycopy(chunk, 0, imageBytes, offset, chunk.size)
                offset += chunk.size
            }

            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (bitmap != null) {
                onSuccess(bitmap.asImageBitmap())
            } else {
                onError("Failed to decode image")
            }
        }

        override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
            onError(error.message ?: "Unknown error")
        }
    }

    val requestBuilder = cronetEngine.newUrlRequestBuilder(url, callback, executor)
    requestBuilder.build().start()
}
