package com.chaman.qr;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Foreground service: keeps the process alive so payment voice works with app closed. */
public class KeepAliveService extends Service {
    private static final String CH = "keepalive";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!(Announcer.always(this) && Announcer.fgOn(this))) {
            stopSelf();
            return START_NOT_STICKY;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null && nm.getNotificationChannel(CH) == null) {
            NotificationChannel ch = new NotificationChannel(CH, "Payment voice", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Chaman QR")
                .setContentText("Payment awaaz chalu hai")
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else
            startForeground(7, n);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
