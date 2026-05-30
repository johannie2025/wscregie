package com.wisesmartchurch.system.streaming;

import android.content.Context;
import android.media.*;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;

/**
 * Gère l'enregistrement MP4 local et le streaming RTMP.
 * FFmpegKit 5.1.LTS + MediaRecorder natif.
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
    private String  currentRecPath;
    private StreamCallback callback;

    public StreamingManager(Context ctx) { this.ctx = ctx; }
    public void setCallback(StreamCallback cb) { this.callback = cb; }

    // ── Enregistrement local MP4 ───────────────────
    public void startRecording(String outputPath) {
        if (recording) return;
        try {
            File dir = new File(outputPath).getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();

            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setOutputFile(outputPath);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(128000);
            recorder.prepare();
            recorder.start();
            recording = true;
            currentRecPath = outputPath;
            Log.i(TAG, "▶ Enregistrement: " + outputPath);
            if (callback != null) ui.post(() -> callback.onStarted("record"));
        } catch (Exception e) {
            Log.e(TAG, "startRecording", e);
            if (callback != null) ui.post(() -> callback.onError("record", e.getMessage()));
        }
    }

    public void stopRecording() {
        if (!recording || recorder == null) return;
        try {
            recorder.stop(); recorder.release(); recorder = null; recording = false;
            if (callback != null) ui.post(() -> callback.onStopped("record"));
        } catch (Exception e) {
            recorder = null; recording = false;
            Log.w(TAG, "stopRecording: " + e.getMessage());
        }
    }

    // ── Streaming RTMP via FFmpegKit 5.1.LTS ──────
    public void startRtmpStream(String rtmpUrl) {
        if (rtmpUrl == null || rtmpUrl.trim().isEmpty()) {
            if (callback != null) ui.post(() -> callback.onError("rtmp", "URL RTMP vide"));
            return;
        }
        streaming = true;
        Log.i(TAG, "📡 RTMP: " + rtmpUrl);
        try {
            // FFmpegKit 5.1.LTS API
            com.arthenica.ffmpegkit.FFmpegKit.executeAsync(
                "-f lavfi -i color=c=black:s=1280x720:r=30 " +
                "-f android_camera -i 0 " +
                "-c:v libx264 -preset ultrafast -tune zerolatency -b:v 2000k " +
                "-c:a aac -b:a 128k -ar 44100 " +
                "-f flv \"" + rtmpUrl + "\"",
                session -> {
                    com.arthenica.ffmpegkit.ReturnCode rc = session.getReturnCode();
                    streaming = false;
                    if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(rc)) {
                        if (callback != null) ui.post(() -> callback.onStopped("rtmp"));
                    } else {
                        String err = "Code: " + rc;
                        if (callback != null) ui.post(() -> callback.onError("rtmp", err));
                    }
                },
                log -> {},
                stats -> {}
            );
            if (callback != null) ui.post(() -> callback.onStarted("rtmp"));
        } catch (Exception e) {
            streaming = false;
            Log.e(TAG, "startRtmpStream", e);
            if (callback != null) ui.post(() -> callback.onError("rtmp", e.getMessage()));
        }
    }

    public void stopStream() {
        if (!streaming) return;
        streaming = false;
        try { com.arthenica.ffmpegkit.FFmpegKit.cancel(); } catch (Exception ignored) {}
        if (callback != null) ui.post(() -> callback.onStopped("rtmp"));
    }

    public boolean isRecording() { return recording; }
    public boolean isStreaming()  { return streaming; }

    public String getDefaultRecPath() {
        String base = ctx.getSharedPreferences("wsc_prefs", Context.MODE_PRIVATE)
            .getString("rec_path", "");
        if (base.isEmpty()) base = ctx.getExternalFilesDir(null) + "/Recordings/";
        String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
            java.util.Locale.getDefault()).format(new java.util.Date());
        return base + "WSC_" + ts + ".mp4";
    }
}
