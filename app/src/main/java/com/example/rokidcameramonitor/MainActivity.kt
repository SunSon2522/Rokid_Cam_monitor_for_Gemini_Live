package com.example.rokidcameramonitor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.rokidcameramonitor.theme.RokidCameraMonitorTheme
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.net.NetworkInterface

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val ips = getLocalIpAddresses()

        setContent {
            RokidCameraMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isRunning by ReceiverService.isRunning
                    val frameTrigger by ReceiverService.frameTrigger.collectAsState()
                    val latestBitmap = ReceiverService.sharedBitmap
                    var isFullscreen by remember { mutableStateOf(false) }

                    CameraMonitorScreen(
                        isReceiving = isRunning,
                        latestBitmap = latestBitmap,
                        frameTrigger = frameTrigger,
                        ips = ips,
                        context = this,
                        onStartServer = {
                            val intent = Intent(this, ReceiverService::class.java)
                            startForegroundService(intent)
                        },
                        onStopServer = {
                            val intent = Intent(this, ReceiverService::class.java)
                            stopService(intent)
                        },
                        onEnterFullscreen = {
                            isFullscreen = true
                            toggleSystemBars(false)
                        },
                        onExitFullscreen = {
                            isFullscreen = false
                            toggleSystemBars(true)
                        },
                        isFullscreen = isFullscreen
                    )
                }
            }
        }
    }

    private fun toggleSystemBars(show: Boolean) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (show) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun getLocalIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress?.indexOf(':') == -1) {
                        ips.add(address.hostAddress ?: "")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ips
    }
}

@Composable
fun CameraMonitorScreen(
    isReceiving: Boolean,
    latestBitmap: Bitmap?,
    frameTrigger: Long,
    ips: List<String>,
    context: Context,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    isFullscreen: Boolean
) {
    BackHandler(enabled = isFullscreen) {
        onExitFullscreen()
    }

    if (isFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (latestBitmap != null && frameTrigger > 0) {
                Image(
                    bitmap = latestBitmap.asImageBitmap(),
                    contentDescription = "Live Feed",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text("Waiting for video stream...", color = Color.White)
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (latestBitmap != null && frameTrigger > 0) {
                    Image(
                        bitmap = latestBitmap.asImageBitmap(),
                        contentDescription = "Live Feed",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("No Video Signal", color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Gemini Vision Monitor",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Server is listening on:", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    ips.forEach { ip ->
                        val url = "rtmp://$ip:1936/live"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(url, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Stream URL", url)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Copied $url", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("COPY")
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            if (!isReceiving) {
                Button(onClick = onStartServer, modifier = Modifier.fillMaxWidth(0.8f)) {
                    Text("START SERVER")
                }
            } else {
                Button(
                    onClick = onStopServer,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("STOP SERVER")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(onClick = onEnterFullscreen, modifier = Modifier.fillMaxWidth(0.8f)) {
                    Text("ENTER FULLSCREEN")
                }
            }
        }
    }
}
