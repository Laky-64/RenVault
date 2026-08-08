package com.wails.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ScannerActivity extends AppCompatActivity {

    private static final String TAG = "RenVaultScanner";
    private static final int PERMISSION_REQUEST = 4711;
    private static final String PREFIX = "FIDO:/";

    private final AtomicBoolean reported = new AtomicBoolean(false);

    private ExecutorService analysisExecutor;
    private BarcodeScanner scanner;
    private PreviewView previewView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        previewView = new PreviewView(this);
        previewView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(previewView);

        analysisExecutor = Executors.newSingleThreadExecutor();
        scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST);
            return;
        }
        startCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != PERMISSION_REQUEST) {
            return;
        }
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            report(null, "camera permission was declined");
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ResolutionSelector resolution = new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                        .setResolutionStrategy(new ResolutionStrategy(new Size(1280, 720),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build();

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setResolutionSelector(resolution)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(analysisExecutor, this::analyse);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis);
            } catch (Exception e) {
                Log.e(TAG, "cannot start the camera", e);
                report(null, "cannot start the camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @ExperimentalGetImage
    private void analyse(@NonNull ImageProxy proxy) {
        android.media.Image image = proxy.getImage();
        if (image == null || reported.get()) {
            proxy.close();
            return;
        }
        InputImage input = InputImage.fromMediaImage(image, proxy.getImageInfo().getRotationDegrees());
        scanner.process(input)
                .addOnSuccessListener(this::inspect)
                .addOnFailureListener(e -> Log.w(TAG, "barcode scan failed", e))
                .addOnCompleteListener(task -> proxy.close());
    }

    private void inspect(List<Barcode> barcodes) {
        for (Barcode barcode : barcodes) {
            String value = barcode.getRawValue();
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
                report(trimmed, null);
                return;
            }
        }
    }

    private void report(String url, String error) {
        if (!reported.compareAndSet(false, true)) {
            return;
        }
        RenVaultScanner.deliver(url, error);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        report(null, "the scan was cancelled");
        if (scanner != null) {
            scanner.close();
        }
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
        }
    }
}
