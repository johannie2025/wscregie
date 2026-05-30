package com.wisesmartchurch.system.streaming;

import android.content.Context;
import android.hardware.camera2.*;
import android.media.*;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Gère l'enregistrement MP4 (mix général) et le streaming RTMP
 * via FFmpegKit ou MediaRecorder natif selon la cible.
 */
public class StreamingManager {

    private static final String TAG = "WscStreaming";
    private final Context ctx;
    private final Handler ui = new Handler(Looper.getMainLooper());

    public interface StreamCallback {
        void onStarted(String target);
        void onStopped(String target);
        void onError(String target, String msg);
    }

    private MediaRecorder recorder;
    private boolean recording = false;
    private boolean streaming = false;
    private String currentRecPath;
    private StreamCallback callback;

    public StreamingManager(Context ctx) { this.ctx = ctx; }
    public void setCallback(StreamCallback cb) { this.callback = cb; }

    // ── Recording (local MP4) ───────────────────────────────
    public void startRecording(String outputPath, Surface previewSurface) {
        if (recording) { Log.w(TAG, "Already recording"); return; }
        try {
            File dir = new File(outputPath).getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();

            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setOutputFile(outputPath);
            recorder.setVideoEncodingBitRate(4_000_000);
            recorder.setVideoFrameRate(30);
            recorder.setVideoSize(1920, 1080);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(128000);
            recorder.prepare();
            recorder.start();
            recording = true;
            currentRecPath = outputPath;
            Log.i(TAG, "▶ Enregistrement démarré: " + outputPath);
            if (callback != null) ui.post(() -> callback.onStarted("record"));
        } catch (Exception e) {
            Log.e(TAG, "startRecording error", e);
            if (callback != null) ui.post(() -> callback.onError("record", e.getMessage()));
        }
    }

    public void stopRecording() {
        if (!recording || recorder == null) return;
        try {
            recorder.stop(); recorder.release(); recorder = null; recording = false;
            Log.i(TAG, "⏹ Enregistrement arrêté: " + currentRecPath);
            if (callback != null) ui.post(() -> callback.onStopped("record"));
        } catch (Exception e) {
            Log.e(TAG, "stopRecording error", e);
            recorder = null; recording = false;
        }
    }

    // ── RTMP Streaming via FFmpegKit ───────────────────────
    public void startRtmpStream(String rtmpUrl, Surface previewSurface) {
        if (rtmpUrl == null || rtmpUrl.isEmpty()) {
            if (callback != null) ui.post(() -> callback.onError("rtmp", "URL RTMP vide"));
            return;
        }
        streaming = true;
        Log.i(TAG, "📡 RTMP streaming vers: " + rtmpUrl);
        // FFmpegKit command for streaming screen capture + mic audio
        String cmd = "-f android_camera -i 0 " +
                     "-f alsa -i default " +
                     "-c:v libx264 -preset ultrafast -b:v 2500k " +
                     "-c:a aac -b:a 128k " +
                     "-f flv " + rtmpUrl;
        try {
            // Use reflection to avoid compile-time dependency if FFmpegKit not available
            Class<?> cls = Class.forName("com.arthenica.ffmpegkit.FFmpegKit");
            cls.getMethod("executeAsync", String.class, Object.class)
               .invoke(null, cmd, null);
            if (callback != null) ui.post(() -> callback.onStarted("rtmp"));
        } catch (Exception e) {
            Log.w(TAG, "FFmpegKit unavailable, using MediaProjection fallback");
            if (callback != null) ui.post(() -> callback.onError("rtmp", "FFmpegKit requis pour RTMP"));
        }
    }

    public void stopStream() {
        if (!streaming) return;
        streaming = false;
        try {
            Class<?> cls = Class.forName("com.arthenica.ffmpegkit.FFmpegKit");
            cls.getMethod("cancel").invoke(null);
        } catch (Exception ignored) {}
        if (callback != null) ui.post(() -> callback.onStopped("rtmp"));
    }

    public boolean isRecording() { return recording; }
    public boolean isStreaming()  { return streaming; }
    public String  getRecordPath(Context ctx) {
        String base = ctx.getSharedPreferences("wsc_prefs", Context.MODE_PRIVATE)
                .getString("rec_path", ctx.getExternalFilesDir(null) + "/Recordings/");
        String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
        return base + "WSC_" + ts + ".mp4";
    }
}
