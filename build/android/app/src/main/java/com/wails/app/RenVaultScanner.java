package com.wails.app;

import android.content.Context;
import android.content.Intent;

public final class RenVaultScanner {

    private static Context context;

    private RenVaultScanner() {
    }

    private static native void nativeRegister();

    private static native void nativeDeliver(String url, String error);

    public static void attach(Context ctx) {
        context = ctx.getApplicationContext();
        nativeRegister();
    }

    public static synchronized String start() {
        if (context == null) {
            return "scanner has no context";
        }
        Intent intent = new Intent(context, ScannerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            return "cannot open the scanner: " + e.getMessage();
        }
        return null;
    }

    static void deliver(String url, String error) {
        nativeDeliver(url, error);
    }
}
