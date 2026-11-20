package com.chaman.qr;

import android.app.Notification;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Pattern;

/** Step 1: only logs money-looking notifications (local, on this phone) so we can see the real formats. */
public class NotifService extends NotificationListenerService {
    static final String PREFS = "cq";
    static final String KEY = "notif_log";
    private static final int MAX = 50;
    private static final Pattern MONEY = Pattern.compile(
            "(?i)(\u20b9|\\brs\\.?\\s?\\d|\\binr\\b|credited|deposited|received|paid|debited)");

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
            Notification n = sbn.getNotification();
            if (n == null || n.extras == null) return;
            Bundle e = n.extras;
            String title = str(e.getCharSequence(Notification.EXTRA_TITLE));
            String text = str(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (text.isEmpty()) text = str(e.getCharSequence(Notification.EXTRA_TEXT));
            if (!MONEY.matcher(title + " " + text).find()) return;
            append(sbn.getPackageName(), title, text, sbn.getPostTime());
        } catch (Exception ignored) {
        }
    }

    private static String str(CharSequence c) {
        if (c == null) return "";
        String s = c.toString().trim();
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    private synchronized void append(String pkg, String title, String text, long t) throws Exception {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        JSONArray old = new JSONArray(sp.getString(KEY, "[]"));
        if (old.length() > 0) {
            JSONObject last = old.getJSONObject(old.length() - 1);
            if (last.optString("pkg").equals(pkg) && last.optString("title").equals(title)
                    && last.optString("text").equals(text) && Math.abs(t - last.optLong("t")) < 5000) return;
        }
        JSONObject o = new JSONObject();
        o.put("t", t).put("pkg", pkg).put("title", title).put("text", text);
        old.put(o);
        JSONArray out = new JSONArray();
        for (int i = Math.max(0, old.length() - MAX); i < old.length(); i++) out.put(old.get(i));
        sp.edit().putString(KEY, out.toString()).apply();
    }
}
