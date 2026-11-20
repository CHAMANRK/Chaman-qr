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
    static final String PHONEPE_PKG = "com.phonepe.app";
    private static final int MAX = 50;
    private static final Pattern SKIP = Pattern.compile("(?i)refund|cashback|reward|scratch|coupon|request");
    private static final String CUR = "(?:\u20b9|rs\\.?|inr)\\s*";
    private static final String AMT = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)";
    private static final String END = "(?=\\s*$|\\s+(?:on|via|using|in|at|ref|upi)\\b|[.,|(])";
    private static final Pattern[] PAY_PATS = {
            Pattern.compile("(?i)\\breceived\\s*" + CUR + AMT + "\\s+from\\s+(.+?)" + END),
            Pattern.compile("(?i)(?<![a-z])" + CUR + AMT + "\\s+(?:has\\s+been\\s+)?received\\s+from\\s+(.+?)" + END),
            Pattern.compile("(?i)^(.+?)\\s+(?:has\\s+)?(?:paid|sent)\\s+you\\s+" + CUR + AMT),
            Pattern.compile("(?i)\\b(?:payment|money)\\s+received\\b.*?" + CUR + AMT + "\\s+from\\s+(.+?)" + END),
            Pattern.compile("(?i)\\breceived\\s*" + CUR + AMT),
    };
    private static final int[][] PAY_GROUPS = {{1, 2}, {1, 2}, {2, 1}, {1, 2}, {1, 0}}; // {amount group, name group}

    /** Returns {amount, name} or null. Only "received" style messages; refunds/cashback/requests skipped. */
    static Object[] parsePay(String title, String text) {
        String all = title + " " + text;
        if (SKIP.matcher(all).find()) return null;
        String[] cands = {title, text, all};
        for (String c : cands) {
            if (c == null || c.isEmpty()) continue;
            for (int i = 0; i < PAY_PATS.length; i++) {
                Matcher m = PAY_PATS[i].matcher(c);
                if (!m.find()) continue;
                try {
                    double amt = Double.parseDouble(m.group(PAY_GROUPS[i][0]).replace(",", ""));
                    String name = PAY_GROUPS[i][1] > 0 ? m.group(PAY_GROUPS[i][1]).trim() : "";
                    if (amt > 0) return new Object[]{amt, name};
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }
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
            String pk = sbn.getPackageName();
            if (PAYTM_PKG.equals(pk) || PHONEPE_PKG.equals(pk)) {
                Object[] r = parsePay(title, text);
                if (r != null) addPay((Double) r[0], (String) r[1], PAYTM_PKG.equals(pk) ? "Paytm" : "PhonePe");
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
    private synchronized void addPay(double amt, String name, String app) throws Exception {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        JSONArray old = new JSONArray(sp.getString(K_PAY, "[]"));
        long now = System.currentTimeMillis();
        if (old.length() > 0) {
            JSONObject last = old.getJSONObject(old.length() - 1);
            if (last.optString("name").equals(name) && Math.abs(last.optDouble("amt") - amt) < 0.001
                    && now - last.optLong("t") < 8000) return;
        }
        JSONObject o = new JSONObject();
        o.put("t", now).put("amt", amt).put("name", name).put("app", app);
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
