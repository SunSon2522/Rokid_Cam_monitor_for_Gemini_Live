package com.example.rokidcameramonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.bytedeco.javacv.AndroidFrameConverter
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File

class ReceiverService : Service() {

    private val TAG = "ReceiverService"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var isReceiving = false

    companion object {
        val isRunning = androidx.compose.runtime.mutableStateOf(false)
        var sharedBitmap: Bitmap? = null
        val frameTrigger = MutableStateFlow(0L)
    }

    private var process: Process? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(1, buildForegroundNotification("Camera Monitor Active..."), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, buildForegroundNotification("Camera Monitor Active..."))
        }
        isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isReceiving) {
            startMediaMtx()
            startFrameGrabber()
        }
        return START_STICKY
    }

    private fun startMediaMtx() {
        isReceiving = true
        scope.launch {
            try {
                val binaryFile = File(applicationInfo.nativeLibraryDir, "libmediamtx.so")
                if (!binaryFile.exists()) {
                    Log.e(TAG, "MediaMTX binary not found at ${binaryFile.absolutePath}!")
                    return@launch
                }

                val configFile = File(filesDir, "mediamtx.yml")
                val configContent = """
                    rtmpAddress: :1936
                    rtspAddress: :8555
                    hlsAddress: :8889
                    webrtcAddress: :8890
                    paths:
                      all_others:
                        record: no
                """.trimIndent()
                configFile.writeText(configContent)

                Log.d(TAG, "Starting MediaMTX...")
                val processBuilder = ProcessBuilder(binaryFile.absolutePath, configFile.absolutePath)
                processBuilder.directory(filesDir)
                processBuilder.redirectErrorStream(true)
                process = processBuilder.start()

                val reader = process!!.inputStream.bufferedReader()
                while (isReceiving) {
                    val line = reader.readLine() ?: break
                    Log.d("MediaMTX", line)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting MediaMTX", e)
            }
        }
    }

    private fun startFrameGrabber() {
        scope.launch {
            // Wait for MediaMTX to fully start
            delay(3000)
            
            while (isReceiving) {
                var grabber: FFmpegFrameGrabber? = null
                try {
                    // Connect to the local RTMP server running via MediaMTX
                    val streamUrl = "rtmp://localhost:1936/live"
                    Log.d(TAG, "Starting FFmpegFrameGrabber on $streamUrl")
                    
                    grabber = FFmpegFrameGrabber(streamUrl)
                    grabber.format = "flv"
                    grabber.setOption("rtmp_live", "live")
                    grabber.setOption("fflags", "nobuffer")
                    grabber.setOption("flags", "low_delay")
                    grabber.setOption("strict", "experimental")
                    
                    grabber.start()
                    
                    val converter = AndroidFrameConverter()
                    
                    while (isReceiving) {
                        val frame = grabber.grabImage() ?: break // Break if stream ends
                        val bitmap = converter.convert(frame)
                        if (bitmap != null) {
                            // Push to flow for the UI to observe and render immediately
                            sharedBitmap = bitmap
                            frameTrigger.value = frameTrigger.value + 1
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error grabbing frames, retrying in 2 seconds...", e)
                    delay(2000)
                } finally {
                    try {
                        grabber?.stop()
                        grabber?.release()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping grabber", e)
                    }
                }
            }
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val bgChannel = NotificationChannel("CAMERAMONITOR", "Camera Monitor Service", NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(bgChannel)
    }

    private fun buildForegroundNotification(text: String): Notification {
        return NotificationCompat.Builder(this, "CAMERAMONITOR")
            .setContentTitle("Rokid Camera Monitor")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isReceiving = false
        isRunning.value = false
        process?.destroy()
        scope.cancel()
    }
}
