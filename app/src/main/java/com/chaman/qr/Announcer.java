package com.chaman.qr;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native payment announcer (works with app closed). Silent = nothing, vibrate mode = vibrate only, normal = voice + vibrate. */
final class Announcer {
    static volatile boolean uiVisible = false;
    private static TextToSpeech tts;
    private static boolean ready = false;
    private static final List<String> pending = new ArrayList<>();

    private Announcer() {
    }

    static boolean always(Context c) {
        return "1".equals(c.getSharedPreferences("cq", Context.MODE_PRIVATE).getString("always", "0"));
    }

    static boolean fgOn(Context c) {
        return "1".equals(c.getSharedPreferences("cq", Context.MODE_PRIVATE).getString("fg", "0"));
    }

    static int ringer(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        return am == null ? AudioManager.RINGER_MODE_NORMAL : am.getRingerMode();
    }

    static void vibrate(Context c) {
        try {
            Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            v.vibrate(VibrationEffect.createWaveform(new long[]{0, 90, 110, 90}, -1));
        } catch (Exception ignored) {
        }
    }

    /** Voice only if ringer is NORMAL. Returns true if it will speak. */
    static synchronized boolean speak(Context c, String text, boolean queue) {
        if (ringer(c) != AudioManager.RINGER_MODE_NORMAL) return false;
        Context app = c.getApplicationContext();
        if (tts == null) {
            tts = new TextToSpeech(app, st -> {
                synchronized (Announcer.class) {
                    if (st == TextToSpeech.SUCCESS) {
                        int r = tts.setLanguage(new Locale("en", "IN"));
                        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                            tts.setLanguage(Locale.US);
                        tts.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                        ready = true;
                        for (String p : pending) tts.speak(p, TextToSpeech.QUEUE_ADD, null, "pay");
                        pending.clear();
                    }
                }
            });
        }
        if (ready) tts.speak(text, queue ? TextToSpeech.QUEUE_ADD : TextToSpeech.QUEUE_FLUSH, null, "pay");
        else pending.add(text);
        return true;
    }

    static String amtWords(double a) {
        long r = (long) Math.floor(a);
        long p = Math.round((a - r) * 100);
        return r + " rupees" + (p > 0 ? " " + p + " paise" : "");
    }

    /** Called from NotifService when UI is not visible. Returns true if handled natively. */
    static boolean announcePayment(Context c, double amt, String name) {
        if (!always(c) || uiVisible) return false;
        int rm = ringer(c);
        if (rm == AudioManager.RINGER_MODE_SILENT) return true;
        vibrate(c);
        if (rm == AudioManager.RINGER_MODE_NORMAL)
            speak(c, "Received " + amtWords(amt) + (name != null && !name.isEmpty() ? " from " + name : ""), true);
        return true;
    }

    /** Start/stop foreground service according to prefs. */
    static void applyService(Context c) {
        try {
            android.content.Intent i = new android.content.Intent(c, KeepAliveService.class);
            if (always(c) && fgOn(c)) {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
                else c.startService(i);
            } else {
                c.stopService(i);
            }
        } catch (Exception ignored) {
        }
    }
}
