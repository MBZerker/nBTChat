package com.mbzerker.nbtchat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

public final class NotificationHelper {
    private static final String REQUEST_CHANNEL_ID = "bluetooth_requests";
    private static final String BACKGROUND_CHANNEL_ID = "bluetooth_background_silent";
    private static final String MESSAGE_CHANNEL_ID = "chat_messages";
    private static final int REQUEST_NOTIFICATION_ID = 42;
    public static final int ONLINE_NOTIFICATION_ID = 73;

    private NotificationHelper() {
    }

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                REQUEST_CHANNEL_ID,
                "Pedidos Bluetooth",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Avisos quando outro nBTChat tenta se conectar.");
        manager.createNotificationChannel(channel);

        NotificationChannel backgroundChannel = new NotificationChannel(
                BACKGROUND_CHANNEL_ID,
                "nBTChat em segundo plano",
                NotificationManager.IMPORTANCE_MIN
        );
        backgroundChannel.setDescription("Mantem o Bluetooth escutando mensagens sem som.");
        backgroundChannel.setShowBadge(false);
        backgroundChannel.enableVibration(false);
        backgroundChannel.setSound(null, null);
        manager.createNotificationChannel(backgroundChannel);

        NotificationChannel messageChannel = new NotificationChannel(
                MESSAGE_CHANNEL_ID,
                "Mensagens",
                NotificationManager.IMPORTANCE_HIGH
        );
        messageChannel.setDescription("Mensagens recebidas por Bluetooth.");
        manager.createNotificationChannel(messageChannel);
    }

    public static void showConnectionRequest(Context context, String remoteName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        android.app.Notification notification = new android.app.Notification.Builder(context, REQUEST_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("Pedido de conexao nBTChat")
                .setContentText(remoteName + " quer conversar por Bluetooth.")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(REQUEST_NOTIFICATION_ID, notification);
    }

    public static android.app.Notification buildBackgroundNotification(Context context) {
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new android.app.Notification.Builder(context, BACKGROUND_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("nBTChat")
                .setContentText("Bluetooth ativo para mensagens.")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setLocalOnly(true)
                .setShowWhen(false)
                .setDefaults(0)
                .setPriority(android.app.Notification.PRIORITY_MIN)
                .build();
    }

    public static void showMessageNotification(Context context, String remoteAddress, String remoteName, String body, int unread) {
        if (!new AppSettingsStore(context).notificationsEnabled()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openIntent.putExtra(MessageStore.EXTRA_ADDRESS, remoteAddress);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                remoteAddress == null ? 2 : remoteAddress.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        android.app.Notification notification = new android.app.Notification.Builder(context, MESSAGE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(remoteName == null || remoteName.trim().isEmpty() ? "Nova mensagem nBTChat" : remoteName)
                .setContentText(body)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setNumber(Math.max(1, unread))
                .setAutoCancel(true)
                .build();

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(remoteAddress == null ? 44 : remoteAddress.hashCode(), notification);
    }
}
