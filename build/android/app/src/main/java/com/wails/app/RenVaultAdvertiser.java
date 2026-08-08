package com.wails.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.UUID;

public final class RenVaultAdvertiser {

    private static final String TAG = "RenVaultAdvertiser";

    private static final ParcelUuid CABLE_UUID =
            ParcelUuid.fromString("0000fde2-0000-1000-8000-00805f9b34fb");

    private static Context context;
    private static BluetoothLeAdvertiser advertiser;
    private static AdvertiseCallback callback;

    private RenVaultAdvertiser() {
    }

    private static native void nativeRegister();

    public static void attach(Context ctx) {
        context = ctx.getApplicationContext();
        nativeRegister();
    }

    public static void requestPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        String advertise = "android.permission.BLUETOOTH_ADVERTISE";
        String connect = "android.permission.BLUETOOTH_CONNECT";
        boolean needed = activity.checkSelfPermission(advertise) != PackageManager.PERMISSION_GRANTED
                || activity.checkSelfPermission(connect) != PackageManager.PERMISSION_GRANTED;
        if (needed) {
            activity.requestPermissions(new String[]{advertise, connect}, 4712);
        }
    }

    public static synchronized String start(byte[] advert) {
        if (callback != null) {
            return "already advertising";
        }
        if (context == null) {
            return "advertiser has no context";
        }
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager == null) {
            return "no bluetooth service";
        }
        BluetoothAdapter adapter = manager.getAdapter();
        if (adapter == null) {
            return "no bluetooth adapter";
        }
        if (!adapter.isEnabled()) {
            return "bluetooth is off";
        }
        BluetoothLeAdvertiser le = adapter.getBluetoothLeAdvertiser();
        if (le == null) {
            return "this device cannot advertise over bluetooth";
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .setTimeout(0)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceData(CABLE_UUID, advert)
                .build();

        final String[] failure = new String[1];
        final Object done = new Object();
        AdvertiseCallback started = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                synchronized (done) {
                    done.notifyAll();
                }
            }

            @Override
            public void onStartFailure(int errorCode) {
                synchronized (done) {
                    failure[0] = "advertising rejected with code " + errorCode;
                    done.notifyAll();
                }
            }
        };

        try {
            synchronized (done) {
                le.startAdvertising(settings, data, started);
                done.wait(5000);
            }
        } catch (SecurityException e) {
            return "missing bluetooth permission: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted while starting to advertise";
        }

        if (failure[0] != null) {
            return failure[0];
        }
        advertiser = le;
        callback = started;
        return null;
    }

    public static synchronized String stop() {
        if (callback == null) {
            return null;
        }
        try {
            advertiser.stopAdvertising(callback);
        } catch (SecurityException e) {
            Log.w(TAG, "cannot stop advertising", e);
            return "missing bluetooth permission: " + e.getMessage();
        } finally {
            advertiser = null;
            callback = null;
        }
        return null;
    }
}
