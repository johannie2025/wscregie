package com.wisesmartchurch.system.camera;

import android.content.Context;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;
import androidx.camera.core.*;
import androidx.camera.lifecycle.*;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Gère la caméra du téléphone régie:
 * - Prévisualisation locale dans un PreviewView
 * - Surface pour l'enregistrement / streaming
 * - Envoi du flux caméra comme overlay sur la TV
 */
public class CameraManager {

    private static final String TAG = "WscCamera";
    private final Context ctx;
    private ProcessCameraProvider provider;
    private Preview preview;
    private boolean active = false;
    private int cameraSelector = CameraSelector.LENS_FACING_BACK;

    public CameraManager(Context ctx) { this.ctx = ctx; }

    public void start(PreviewView previewView, LifecycleOwner owner) {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(ctx);
        future.addListener(() -> {
            try {
                provider = future.get();
                bindCamera(previewView, owner);
                active = true;
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "CameraX error", e);
                Toast.makeText(ctx, "Erreur caméra: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(ctx));
    }

    private void bindCamera(PreviewView previewView, LifecycleOwner owner) {
        provider.unbindAll();
        CameraSelector selector = new CameraSelector.Builder()
            .requireLensFacing(cameraSelector).build();
        preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        try {
            provider.bindToLifecycle(owner, selector, preview);
        } catch (Exception e) {
            Log.e(TAG, "bindCamera error", e);
        }
    }

    public void flip() {
        cameraSelector = cameraSelector == CameraSelector.LENS_FACING_BACK
            ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK;
        Log.d(TAG, "Camera flipped to: " + (cameraSelector == CameraSelector.LENS_FACING_BACK ? "BACK" : "FRONT"));
    }

    public void stop() {
        if (provider != null) { provider.unbindAll(); active = false; }
    }

    public boolean isActive() { return active; }
}
