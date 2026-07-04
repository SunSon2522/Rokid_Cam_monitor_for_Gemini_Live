# Rokid Camera Monitor for Gemini Vision

A cutting-edge Android application that bridges augmented reality glasses (like the Rokid AR) with Gemini Live's Vision AI by running a completely local, ultra-low-latency RTMP media server on your smartphone.

## How It Works

Instead of relying on cloud servers or complicated OBS setups, this app turns your Android smartphone into a high-performance **live streaming server**:

1. **The AR Glasses** broadcast their live camera feed directly to your phone via the local Wi-Fi or Mobile Hotspot using RTMP.
2. **The App** runs a local instance of [MediaMTX](https://github.com/bluenviron/mediamtx) in a background service, instantly receiving the RTMP feed.
3. **FFmpeg & JavaCV** are used to grab and decode the frames with zero buffering and ultra-low latency.
4. **Jetpack Compose** rapidly paints these frames into a fullscreen Activity.
5. **Gemini Live** can now use Android's screen sharing capability to "see" your fullscreen app, meaning Gemini can literally see exactly what you are seeing through your AR glasses in real-time!

## Features

- **Built-in RTMP Server**: Runs MediaMTX natively on Android. No external server required.
- **Zero-Buffer Decoding**: Hand-tuned FFmpeg frame grabbing optimized for absolute minimal latency.
- **Dynamic IP Detection**: Automatically scans your phone's network interfaces and provides exactly the right stream URLs to input into your AR glasses.
- **Auto-Reconnect**: Robust reconnection loop if the camera feed drops or hasn't started yet.
- **Fullscreen Mode**: Hides Android system UI completely to maximize the video feed area for Gemini Vision screen sharing.
- **Avoids Port Clashes**: Configured to run on port `1936` to avoid clashes with other generic streaming apps.

## Usage

1. Connect your AR Glasses and your Android phone to the same Wi-Fi network (or connect your glasses to your phone's mobile hotspot).
2. Open the **Rokid Camera Monitor** app.
3. Tap **START SERVER**.
4. The app will display the IP addresses your phone is listening on. Tap the **COPY** button next to the IP address of your active network.
5. Paste this URL into your AR Glasses' streaming app. Ensure the URL ends with `/live`.
6. Start the stream on the glasses.
7. Tap **ENTER FULLSCREEN** on your phone.
8. Activate **Gemini Live** screen sharing!

## Tech Stack

- **Kotlin & Jetpack Compose**
- **MediaMTX** (Compiled for Android ARM64)
- **JavaCV / FFmpegFrameGrabber**
- **Kotlin Coroutines / StateFlow**

## License

This project is open-source and free to use.
