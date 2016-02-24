package com.squareup.timessquare;

import android.util.Log;

/**
 * Log utility class to handle the log tag and DEBUG-only logging.
 */
final class Logr {
    public static void d(final String message) {
        if (BuildConfig.DEBUG) {
            Log.d("TimesSquare", message);
        }
    }

    public static void d(final String message, final Object... args) {
        if (BuildConfig.DEBUG) {
            d(String.format(message, args));
        }
    }
}
