package com.chaman.qr;

import android.app.Notification;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Logs money-looking notifications (local only) and turns "received" ones into payment events. */
public class NotifService extends NotificationListenerService {
    static final String PREFS = "cq";
    static final String KEY = "notif_log";
    static final String K_CONN = "notif_conn";
    static final String K_SEEN = "notif_seen";
    static final String K_LASTPKG = "notif_last_pkg";
    static final String K_LASTT = "notif_last_t";
    static final String K_ALL = "notif_all";
    static final String K_PAY = "pay_events";
    static final String K_SEENL = "pay_seen_lines";
    static final String PAYTM_PKG = "net.one97.paytm";
    static final String PHONEPE_PKG = "com.phonepe.app";
    private static final int MAX = 50;
    private static final Pattern MONEY = Pattern.compile(
            "(?i)(\u20b9|\\brs\\.?\\s?\\d|\\binr\\b|credited|deposited|received|paid|debited)");
    private static final Pattern SKIP = Pattern.compile("(?i)refund|cashback|reward|scratch|coupon|request");
    private static final String CUR = "(?:\u20b9|rs\\.?|inr)\\s*";
    private static final String AMT = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)";
    private static final String END = "(?=\\s*$|\\s+(?:on|via|using|in|at|ref|upi)\\b|[.,|(])";
    private static final Pattern[] PAY_PATS = {
            Pattern.compile("(?i)\\breceived\\s*" + CUR + AMT + "\\s+from\\s+(.+?)" + END),
            Pattern.compile("(?i)(?<![a-z])" + CUR + AMT + "\\s+(?:has\\s+been\\s+)?received\\s+from\\s+(.+?)" + END),
            Pattern.compile("(?i)^(.+?)\\s+(?:has\\s+)?(?:paid|sent)\\s+you\\s+" + CUR + AMT),
            Pattern.compile("(?i)\\b(?:payment|money)\\s+received\\b.*?" + CUR + AMT + "\\s+from\\s+(.+?)" + END),
            Pattern.compile("(?i)^(.+?)\\s+has\\s+sent\\s+" + CUR + AMT + "\\s+to\\s+(?:you|your)\\b"),
            Pattern.compile("(?i)\\breceived\\s*" + CUR + AMT),
    };
    private static final int[][] PAY_GROUPS = {{1, 2}, {1, 2}, {2, 1}, {1, 2}, {2, 1}, {1, 0}}; // {amount group, name group}

    private SharedPreferences sp() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    /* ---------------- parsing ---------------- */

    /** One line/candidate -> {amount, name} or null. Only "received" style; refunds/cashback/requests skipped. */
    static Object[] parseOne(String c) {
        if (c == null || c.isEmpty() || SKIP.matcher(c).find()) return null;
        for (int i = 0; i < PAY_PATS.length; i++) {
            Matcher m = PAY_PATS[i].matcher(c);
            if (!m.find()) continue;
            try {
                double amt = Double.parseDouble(m.group(PAY_GROUPS[i][0]).replace(",", ""));
                String name = PAY_GROUPS[i][1] > 0 ? tidy(m.group(PAY_GROUPS[i][1]).trim()) : "";
                if (amt > 0) return new Object[]{amt, name};
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    static Object[] parsePay(String title, String text) {
        String[] cands = {title, text, title + " " + text};
        for (String c : cands) {
            Object[] r = parseOne(c);
            if (r != null) return r;
        }
        return null;
    }

    /** ALL CAPS names -> Title Case. */
    static String tidy(String n) {
        if (n.equals(n.toUpperCase()) && !n.equals(n.toLowerCase())) {
            StringBuilder sb = new StringBuilder();
            for (String w : n.toLowerCase().split("\\s+")) {
                if (w.isEmpty()) continue;
                if (sb.length() > 0) sb.append(' ');
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
            return sb.toString();
        }
        return n;
    }

    private static String raw(CharSequence c) {
        return c == null ? "" : c.toString().trim();
    }

    private static void addLine(List<String> out, String s) {
        String t = s == null ? "" : s.replaceAll("^[\\s\u00b7\u2022*\\-]+", "").trim();
        if (!t.isEmpty()) out.add(t);
    }

    /** title + every line of the notification (inbox lines, or big text split by newline). */
    private static List<String> collectLines(Bundle e, String title) {
        List<String> lines = new ArrayList<>();
        addLine(lines, title);
        CharSequence[] tl = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (tl != null && tl.length > 0) {
            for (CharSequence l : tl) addLine(lines, l == null ? "" : l.toString());
        } else {
            String b = raw(e.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (b.isEmpty()) b = raw(e.getCharSequence(Notification.EXTRA_TEXT));
            for (String p : b.split("\\n")) addLine(lines, p);
        }
        return lines;
    }

    private static Map<String, Integer> countOf(List<String> lines) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String l : lines) m.put(l, m.containsKey(l) ? m.get(l) + 1 : 1);
        return m;
    }

    /* ---------------- per-notification memory (PhonePe groups lines in one notification) ---------------- */

    private synchronized JSONArray seenLoad() {
        try {
            return new JSONArray(sp().getString(K_SEENL, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private synchronized Map<String, Integer> seenGet(String key) {
        Map<String, Integer> m = new LinkedHashMap<>();
        try {
            JSONArray a = seenLoad();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (!o.optString("k").equals(key)) continue;
                JSONObject l = o.getJSONObject("l");
                Iterator<String> it = l.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    m.put(k, l.getInt(k));
                }
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private synchronized void seenPut(String key, Map<String, Integer> lines) {
        try {
            JSONArray a = seenLoad();
            List<JSONObject> keep = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                if (!o.optString("k").equals(key)) keep.add(o);
            }
            JSONObject n = new JSONObject();
            JSONObject l = new JSONObject();
            for (Map.Entry<String, Integer> en : lines.entrySet()) l.put(en.getKey(), en.getValue());
            n.put("k", key).put("l", l);
            keep.add(n);
            JSONArray out = new JSONArray();
            for (int i = Math.max(0, keep.size() - 30); i < keep.size(); i++) out.put(keep.get(i));
            sp().edit().putString(K_SEENL, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private synchronized void seenDel(String key) {
        try {
            JSONArray a = seenLoad();
            JSONArray out = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                if (!a.getJSONObject(i).optString("k").equals(key)) out.put(a.get(i));
            }
            sp().edit().putString(K_SEENL, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /* ---------------- listener callbacks ---------------- */

    @Override
    public void onListenerConnected() {
        sp().edit().putBoolean(K_CONN, true).apply();
        // Absorb what PhonePe already shows, so old lines are never announced as new payments.
        try {
            for (StatusBarNotification s : getActiveNotifications()) {
                if (!PHONEPE_PKG.equals(s.getPackageName())) continue;
                Bundle ex = s.getNotification().extras;
                if (ex == null) continue;
                seenPut(s.getKey(), countOf(collectLines(ex, raw(ex.getCharSequence(Notification.EXTRA_TITLE)))));
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onListenerDisconnected() {
        sp().edit().putBoolean(K_CONN, false).apply();
        try {
            requestRebind(new ComponentName(this, NotifService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        try {
            if (sbn != null && PHONEPE_PKG.equals(sbn.getPackageName())) seenDel(sbn.getKey());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
            SharedPreferences sp = sp();
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
            List<String> lines = collectLines(e, raw(e.getCharSequence(Notification.EXTRA_TITLE)));
            if (text.isEmpty() && lines.size() > 1) text = str(String.join(" | ", lines.subList(1, lines.size())));
            if (text.isEmpty()) text = str(n.tickerText);
            // grouped notifications (PhonePe): show every line in the log
            String logText = lines.size() > 2 ? str(String.join(" | ", lines.subList(1, lines.size()))) : text;

            String pk = sbn.getPackageName();
            if (PAYTM_PKG.equals(pk)) {
                Object[] r = parsePay(title, text);
                if (r != null) addPay((Double) r[0], (String) r[1], "Paytm");
            } else if (PHONEPE_PKG.equals(pk)) {
                handlePhonePe(sbn.getKey(), lines);
            }

            if (!all && !MONEY.matcher(title + " " + logText).find()) return;
            append(pk, title, logText, sbn.getPostTime());
        } catch (Exception ignored) {
        }
    }

    /** PhonePe keeps adding lines to one notification: only lines that are NEW since last time count. */
    private void handlePhonePe(String key, List<String> lines) throws Exception {
        Map<String, Integer> prev = seenGet(key);
        Map<String, Integer> cur = countOf(lines);
        for (Map.Entry<String, Integer> en : cur.entrySet()) {
            int extra = en.getValue() - (prev.containsKey(en.getKey()) ? prev.get(en.getKey()) : 0);
            if (extra <= 0) continue;
            Object[] r = parseOne(en.getKey());
            if (r == null) continue;
            for (int i = 0; i < extra; i++) addPay((Double) r[0], (String) r[1], "PhonePe");
        }
        seenPut(key, cur);
    }

    private static String str(CharSequence c) {
        if (c == null) return "";
        String s = c.toString().trim();
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    /** Payment event: only time, amount, sender name, app (no account digits). */
    private synchronized void addPay(double amt, String name, String app) throws Exception {
        SharedPreferences sp = sp();
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
        SharedPreferences sp = sp();
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
