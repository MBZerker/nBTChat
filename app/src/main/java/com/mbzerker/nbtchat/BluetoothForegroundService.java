package com.mbzerker.nbtchat;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public final class BluetoothForegroundService extends Service implements BtChatManager.Listener {
    public static final String ACTION_PROFILE_UPDATED = "com.mbzerker.nbtchat.PROFILE_UPDATED";

    private BtChatManager btChatManager;
    private ProfileStore profileStore;
    private MessageStore messageStore;
    private String currentRemoteAddress = "";
    private UserProfile currentRemoteProfile = UserProfile.empty();

    @Override
    public void onCreate() {
        super.onCreate();
        profileStore = new ProfileStore(this);
        messageStore = new MessageStore(this);
        btChatManager = new BtChatManager(this, this);
        NotificationHelper.ensureChannels(this);
        startForeground(NotificationHelper.ONLINE_NOTIFICATION_ID, NotificationHelper.buildBackgroundNotification(this));
        btChatManager.startListening();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (btChatManager != null) {
            btChatManager.startListening();
            if (intent != null && ACTION_PROFILE_UPDATED.equals(intent.getAction())) {
                btChatManager.sendProfileUpdate();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (btChatManager != null) {
            btChatManager.stop();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onBluetoothState(String state) {
    }

    @Override
    public void onDeviceFound(BtChatManager.DeviceCandidate candidate) {
    }

    @Override
    public void onDiscoveryFinished() {
    }

    @Override
    public void onIncomingConnection(String remoteName, String remoteAddress) {
    }

    @Override
    public void onRemoteProfile(String remoteAddress, UserProfile profile) {
        currentRemoteAddress = remoteAddress == null ? "" : remoteAddress;
        currentRemoteProfile = profile == null ? UserProfile.empty() : profile;
        profileStore.saveContact(currentRemoteAddress, currentRemoteProfile);
    }

    @Override
    public void onConnected(String remoteAddress, UserProfile profile, String fingerprint) {
        currentRemoteAddress = remoteAddress == null ? "" : remoteAddress;
        currentRemoteProfile = profile == null ? UserProfile.empty() : profile;
        profileStore.saveContact(currentRemoteAddress, currentRemoteProfile);
        profileStore.saveFingerprint(currentRemoteAddress, fingerprint);
    }

    @Override
    public void onRemoteIdentity(String remoteAddress, String deviceId, String identityPublicKey) {
        profileStore.saveIdentity(remoteAddress, deviceId, identityPublicKey);
    }

    @Override
    public void onMessageReceived(String remoteAddress, String id, String kind, String body, String mediaBase64, long durationMs, long sentAt, String replyToId, String replyPreview) {
        String address = remoteAddress == null ? currentRemoteAddress : remoteAddress;
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        UserProfile profile = profileStore.loadContact(address);
        String remoteName = profile.isComplete() ? profile.getDisplayName() : "nBTChat";
        boolean inserted = messageStore.addMessage(address, id, kind, body, mediaBase64, durationMs, false, sentAt, MessageStore.STATUS_DELIVERED, true, replyToId, replyPreview);
        if (!inserted) {
            return;
        }
        int unread = messageStore.getUnread(address);
        String preview = MessageStore.KIND_IMAGE.equals(kind) ? "Imagem" : (MessageStore.KIND_VOICE.equals(kind) ? "Mensagem de voz" : body);
        NotificationHelper.showMessageNotification(this, address, remoteName, preview, unread);

        Intent changed = new Intent(MessageStore.ACTION_MESSAGES_CHANGED);
        changed.setPackage(getPackageName());
        changed.putExtra(MessageStore.EXTRA_ADDRESS, address);
        changed.putExtra(MessageStore.EXTRA_MESSAGE_ID, id);
        changed.putExtra(MessageStore.EXTRA_BODY, preview);
        changed.putExtra(MessageStore.EXTRA_KIND, kind);
        changed.putExtra(MessageStore.EXTRA_SENT_AT, sentAt);
        changed.putExtra(MessageStore.EXTRA_UNREAD, unread);
        sendBroadcast(changed);
    }

    @Override
    public void onMessageDeleted(String remoteAddress, String id) {
        String address = remoteAddress == null ? currentRemoteAddress : remoteAddress;
        messageStore.deleteMessage(address, id);
        Intent changed = new Intent(MessageStore.ACTION_MESSAGES_CHANGED);
        changed.setPackage(getPackageName());
        changed.putExtra(MessageStore.EXTRA_ADDRESS, address);
        changed.putExtra(MessageStore.EXTRA_MESSAGE_ID, id);
        changed.putExtra(MessageStore.EXTRA_DELETED, true);
        sendBroadcast(changed);
    }

    @Override
    public void onReceiptReceived(String remoteAddress, String id, String status) {
        String address = remoteAddress == null ? currentRemoteAddress : remoteAddress;
        messageStore.updateStatus(address, id, status);
        Intent changed = new Intent(MessageStore.ACTION_MESSAGES_CHANGED);
        changed.setPackage(getPackageName());
        changed.putExtra(MessageStore.EXTRA_ADDRESS, address);
        changed.putExtra(MessageStore.EXTRA_MESSAGE_ID, id);
        changed.putExtra(MessageStore.EXTRA_STATUS, status);
        sendBroadcast(changed);
    }

    @Override
    public void onError(String message) {
    }
}
