package com.chaman.qr;

import android.app.Notification;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Logs money-looking notifications (local only) so we can see real formats. Has diagnostics counters. */
public class NotifService extends NotificationListenerService {
    static final String PREFS = "cq";
    static final String KEY = "notif_log";
    static final String K_CONN = "notif_conn";
    static final String K_SEEN = "notif_seen";
    static final String K_LASTPKG = "notif_last_pkg";
    static final String K_LASTT = "notif_last_t";
    static final String K_ALL = "notif_all";
    static final String K_PAY = "pay_events";
    static final String PAYTM_PKG = "net.one97.paytm";
    private static final int MAX = 50;
    private static final Pattern RECV = Pattern.compile(
            "(?i)^\\s*received\\s*(?:\u20b9|rs\\.?|inr)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s+from\\s+(.+?)\\s*$");
    private static final Pattern MONEY = Pattern.compile(
            "(?i)(\u20b9|\\brs\\.?\\s?\\d|\\binr\\b|credited|deposited|received|paid|debited)");

    @Override
    public void onListenerConnected() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(K_CONN, true).apply();
    }

    @Override
    public void onListenerDisconnected() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(K_CONN, false).apply();
        try {
            requestRebind(new ComponentName(this, NotifService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            sp.edit().putBoolean(K_CONN, true)
                    .putInt(K_SEEN, sp.getInt(K_SEEN, 0) + 1)
                    .putString(K_LASTPKG, sbn.getPackageName())
                    .putLong(K_LASTT, System.currentTimeMillis()).apply();

            Notification n = sbn.getNotification();
            if (n == null) return;
            Bundle e = n.extras != null ? n.extras : new Bundle();
            boolean all = sp.getBoolean(K_ALL, false);
            if (all && (n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;

            String title = str(e.getCharSequence(Notification.EXTRA_TITLE));
            if (title.isEmpty()) title = str(e.getCharSequence(Notification.EXTRA_TITLE_BIG));
            String text = str(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (text.isEmpty()) text = str(e.getCharSequence(Notification.EXTRA_TEXT));
            if (text.isEmpty()) text = str(e.getCharSequence(Notification.EXTRA_SUB_TEXT));
            if (text.isEmpty()) {
                CharSequence[] lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
                if (lines != null) {
                    StringBuilder sb = new StringBuilder();
                    for (CharSequence l : lines) if (l != null) sb.append(l).append(" | ");
                    text = str(sb);
                }
            }
            if (text.isEmpty()) text = str(n.tickerText);
            if (PAYTM_PKG.equals(sbn.getPackageName())) {
                Matcher m = RECV.matcher(title);
                if (m.find()) addPay(Double.parseDouble(m.group(1).replace(",", "")), m.group(2));
            }
            if (!all && !MONEY.matcher(title + " " + text).find()) return;
            append(sbn.getPackageName(), title, text, sbn.getPostTime());
        } catch (Exception ignored) {
        }
    }

    private static String str(CharSequence c) {
        if (c == null) return "";
        String s = c.toString().trim();
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    /** Payment event: only time, amount, sender name (no account digits). */
    private synchronized void addPay(double amt, String name) throws Exception {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        JSONArray old = new JSONArray(sp.getString(K_PAY, "[]"));
        long now = System.currentTimeMillis();
        if (old.length() > 0) {
            JSONObject last = old.getJSONObject(old.length() - 1);
            if (last.optString("name").equals(name) && Math.abs(last.optDouble("amt") - amt) < 0.001
                    && now - last.optLong("t") < 8000) return;
        }
        JSONObject o = new JSONObject();
        o.put("t", now).put("amt", amt).put("name", name);
        old.put(o);
        JSONArray out = new JSONArray();
        for (int i = Math.max(0, old.length() - 200); i < old.length(); i++) out.put(old.get(i));
        sp.edit().putString(K_PAY, out.toString()).apply();
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
