package com.chaman.qr;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.service.notification.NotificationListenerService;
import android.util.Base64;
import android.view.HapticFeedbackConstants;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_FILE = 1;
    private WebView web;
    private ValueCallback<Uri[]> fileCb;

    private TextToSpeech tts;
    private boolean ttsOk = false;

    @Override
    protected void onDestroy() {
        if (tts != null) tts.shutdown();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tts = new TextToSpeech(this, st -> {
            if (st == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(new Locale("en", "IN"));
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED)
                    tts.setLanguage(Locale.US);
                ttsOk = true;
            }
        });
        web = new WebView(this);
        web.setBackgroundColor(0xFFEEF1FB);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);

        web.addJavascriptInterface(new Bridge(), "Android");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                boolean json = false;
                String[] types = p.getAcceptTypes();
                if (types != null) for (String t : types) if (t != null && t.contains("json")) json = true;
                i.setType(json ? "*/*" : "image/*");
                startActivityForResult(Intent.createChooser(i, json ? "Choose backup file" : "Choose image"), REQ_FILE);
                return true;
            }
        });
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_FILE && fileCb != null) {
            fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            fileCb = null;
        }
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("(function(){return window.__back?window.__back():false})()", v -> {
            if ("true".equals(v)) return;
            if (web.canGoBack()) web.goBack(); else finish();
        });
    }

    private void toast(final String m) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show());
    }

    private boolean notifEnabled() {
        String f = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return f != null && f.contains(getPackageName());
    }

    private void rebindListener() {
        try {
            if (notifEnabled())
                NotificationListenerService.requestRebind(new ComponentName(this, NotifService.class));
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebindListener();
    }

    private class Bridge {
        @JavascriptInterface
        public void speak(final String text) {
            runOnUiThread(() -> {
                if (ttsOk && tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pay");
            });
        }

        @JavascriptInterface
        public String payEvents(long since) {
            try {
                org.json.JSONArray all = new org.json.JSONArray(
                        getSharedPreferences("cq", MODE_PRIVATE).getString("pay_events", "[]"));
                org.json.JSONArray out = new org.json.JSONArray();
                for (int i = 0; i < all.length(); i++)
                    if (all.getJSONObject(i).optLong("t") > since) out.put(all.get(i));
                return out.toString();
            } catch (Exception e) {
                return "[]";
            }
        }

        @JavascriptInterface
        public String payHistory() {
            return getSharedPreferences("cq", MODE_PRIVATE).getString("pay_events", "[]");
        }

        @JavascriptInterface
        public void clearPay() {
            getSharedPreferences("cq", MODE_PRIVATE).edit().remove("pay_events").apply();
        }

        @JavascriptInterface
        public boolean notifAccess() {
            return notifEnabled();
        }

        @JavascriptInterface
        public String notifStatus() {
            SharedPreferences sp = getSharedPreferences("cq", MODE_PRIVATE);
            return "{\"conn\":" + sp.getBoolean("notif_conn", false)
                    + ",\"seen\":" + sp.getInt("notif_seen", 0)
                    + ",\"all\":" + sp.getBoolean("notif_all", false)
                    + ",\"lastT\":" + sp.getLong("notif_last_t", 0)
                    + ",\"lastPkg\":\"" + sp.getString("notif_last_pkg", "").replace("\"", "") + "\"}";
        }

        @JavascriptInterface
        public void setNotifAll(boolean on) {
            getSharedPreferences("cq", MODE_PRIVATE).edit().putBoolean("notif_all", on).apply();
        }

        @JavascriptInterface
        public void rebindNotif() {
            rebindListener();
        }

        @JavascriptInterface
        public void openNotifSettings() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        }

        @JavascriptInterface
        public void openAppInfo() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))));
        }

        @JavascriptInterface
        public void openBattery() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        }

        @JavascriptInterface
        public String notifLog() {
            return getSharedPreferences("cq", MODE_PRIVATE).getString("notif_log", "[]");
        }

        @JavascriptInterface
        public void clearNotifLog() {
            getSharedPreferences("cq", MODE_PRIVATE).edit().remove("notif_log").apply();
        }

        @JavascriptInterface
        public void tick() {
            runOnUiThread(() -> web.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP));
        }

        @JavascriptInterface
        public void setBright(final boolean on) {
            runOnUiThread(() -> {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = on ? 1f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                getWindow().setAttributes(lp);
                if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void saveData(String key, String val) {
            getSharedPreferences("cq", MODE_PRIVATE).edit().putString(key, val).apply();
        }

        @JavascriptInterface
        public String loadData(String key) {
            SharedPreferences sp = getSharedPreferences("cq", MODE_PRIVATE);
            String v = sp.getString(key, null);
            return v == null ? "" : v;
        }

        @JavascriptInterface
        public String backupFile(String text, String name) {
            try {
                Uri coll = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                String rel = Environment.DIRECTORY_DOCUMENTS + "/ChamanQR/";
                Uri target = null;
                try (Cursor c = getContentResolver().query(coll,
                        new String[]{MediaStore.MediaColumns._ID},
                        MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                        new String[]{name, rel}, null)) {
                    if (c != null && c.moveToFirst()) target = ContentUris.withAppendedId(coll, c.getLong(0));
                }
                byte[] data = text.getBytes("UTF-8");
                if (target != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(target, "wt")) {
                        os.write(data);
                        return "ok";
                    } catch (Exception e) {
                        target = null; // old file owned by another install: write a new one
                    }
                }
                String nm = name;
                for (int n = 0; n < 2; n++) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, n == 0 ? nm : nm.replace(".json", "-" + System.currentTimeMillis() + ".json"));
                    v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, rel);
                    Uri u = getContentResolver().insert(coll, v);
                    if (u == null) continue;
                    try (OutputStream os = getContentResolver().openOutputStream(u, "wt")) {
                        os.write(data);
                        return "ok";
                    }
                }
                return "could not create file";
            } catch (Exception e) {
                return String.valueOf(e.getMessage());
            }
        }

        @JavascriptInterface
        public void saveImage(String b64, String name) {
            try {
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                v.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ChamanQR");
                Uri u = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
                try (OutputStream os = getContentResolver().openOutputStream(u)) {
                    os.write(bytes);
                }
                toast("Saved to Pictures/ChamanQR");
            } catch (Exception e) {
                toast("Save failed: " + e.getMessage());
            }
        }

        @JavascriptInterface
        public void shareImage(String b64, String name) {
            try {
                byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                File dir = new File(getCacheDir(), "shared");
                dir.mkdirs();
                File f = new File(dir, name);
                try (FileOutputStream os = new FileOutputStream(f)) {
                    os.write(bytes);
                }
                Uri u = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", f);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("image/png");
                i.putExtra(Intent.EXTRA_STREAM, u);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Share QR"));
            } catch (Exception e) {
                toast("Share failed: " + e.getMessage());
            }
        }
    }
}
