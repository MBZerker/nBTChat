package com.mbzerker.nbtchat;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import org.json.JSONObject;

public final class BluetoothForegroundService extends Service implements BtChatManager.Listener {
    public static final String ACTION_PROFILE_UPDATED = "com.mbzerker.nbtchat.PROFILE_UPDATED";

    private BtChatManager btChatManager;
    private ProfileStore profileStore;
    private MessageStore messageStore;
    private GadgetStore gadgetStore;
    private AppSettingsStore settingsStore;
    private String currentRemoteAddress = "";
    private UserProfile currentRemoteProfile = UserProfile.empty();

    @Override
    public void onCreate() {
        super.onCreate();
        profileStore = new ProfileStore(this);
        messageStore = new MessageStore(this);
        gadgetStore = new GadgetStore(this);
        settingsStore = new AppSettingsStore(this);
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
        if (currentRemoteAddress.isEmpty()) {
            return;
        }
        if (profileStore.isBlocked(currentRemoteAddress)) {
            btChatManager.disconnectCurrent();
            return;
        }
        currentRemoteProfile = profile == null ? UserProfile.empty() : profile;
        profileStore.saveContact(currentRemoteAddress, currentRemoteProfile);
    }

    @Override
    public void onConnected(String remoteAddress, UserProfile profile, String fingerprint) {
        currentRemoteAddress = remoteAddress == null ? "" : remoteAddress;
        if (currentRemoteAddress.isEmpty()) {
            return;
        }
        if (profileStore.isBlocked(currentRemoteAddress)) {
            btChatManager.disconnectCurrent();
            return;
        }
        currentRemoteProfile = profile == null ? UserProfile.empty() : profile;
        profileStore.saveContact(currentRemoteAddress, currentRemoteProfile);
        profileStore.saveFingerprint(currentRemoteAddress, fingerprint);
        btChatManager.sendPresence(presenceForPeer(settingsStore.userPresence()));
        resendUndeliveredMessages(currentRemoteAddress);
    }

    @Override
    public void onRemoteIdentity(String remoteAddress, String deviceId, String identityPublicKey) {
        profileStore.verifyOrStoreIdentity(remoteAddress, deviceId, identityPublicKey, "");
    }

    @Override
    public void onIdentityWarning(String remoteAddress, String status, String deviceId, String identityPublicKey, String fingerprint) {
        if (remoteAddress != null && !remoteAddress.trim().isEmpty()) {
            profileStore.setBlocked(remoteAddress, true);
        }
    }

    @Override
    public void onMessageReceived(String remoteAddress, String id, String kind, String body, String mediaBase64, long durationMs, long sentAt, String replyToId, String replyPreview) {
        String address = remoteAddress == null ? currentRemoteAddress : remoteAddress;
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        if (profileStore.isBlocked(address)) {
            return;
        }
        if (handleTable100Event(address, kind, body)) {
            return;
        }
        UserProfile profile = profileStore.loadContact(address);
        String remoteName = profile.isComplete() ? profile.getDisplayName() : "nBTChat";
        boolean inserted = messageStore.addMessage(address, id, kind, body, mediaBase64, durationMs, false, sentAt, MessageStore.STATUS_DELIVERED, true, replyToId, replyPreview);
        if (!inserted) {
            return;
        }
        int unread = messageStore.getUnread(address);
        String preview = (MessageStore.KIND_IMAGE.equals(kind) || MessageStore.KIND_GIF.equals(kind))
                ? "Imagem"
                : (MessageStore.KIND_VOICE.equals(kind)
                ? "Mensagem de voz"
                : (MessageStore.KIND_TABLE_100.equals(kind)
                ? GadgetStore.TABLE_100_TITLE
                : (MessageStore.KIND_CONTACT_INVITE.equals(kind) ? "Contato nBTChat" : body)));
        if (!profileStore.isMuted(address)) {
            NotificationHelper.showMessageNotification(this, address, remoteName, preview, unread);
        }

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
    public void onTypingReceived(String remoteAddress, boolean typing) {
    }

    @Override
    public void onPresenceReceived(String remoteAddress, String status) {
    }

    @Override
    public void onDisconnected(String remoteAddress) {
    }

    @Override
    public void onReceiptReceived(String remoteAddress, String id, String status) {
        String address = remoteAddress == null ? currentRemoteAddress : remoteAddress;
        if (profileStore.isMuted(address) && !MessageStore.STATUS_SENT.equals(status)) {
            return;
        }
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

    private void resendUndeliveredMessages(String address) {
        if (address == null || address.trim().isEmpty() || btChatManager == null || !btChatManager.canSendTo(address)) {
            return;
        }
        for (MessageStore.ChatMessage message : messageStore.undeliveredOutgoingMessages(address)) {
            btChatManager.sendChatMessage(address, message.id, message.kind, message.body, message.mediaBase64,
                    message.durationMs, message.sentAt, message.replyToId, message.replyPreview);
        }
    }

    private boolean handleTable100Event(String address, String kind, String body) {
        if (!MessageStore.KIND_TABLE_100_CHOICE.equals(kind) && !MessageStore.KIND_TABLE_100_CONFIRM.equals(kind)) {
            return false;
        }
        try {
            JSONObject json = new JSONObject(body == null ? "{}" : body);
            String tableId = json.optString("tableId", "");
            int number = json.optInt("number", 0);
            if (tableId.isEmpty() || number < 1 || number > 100) {
                return true;
            }
            if (MessageStore.KIND_TABLE_100_CHOICE.equals(kind)) {
                gadgetStore.saveChoice(tableId, address, number, json.optString("name", ""), false);
            } else {
                gadgetStore.setChoiceConfirmed(tableId, address, number, json.optBoolean("confirmed", false));
            }
            Intent changed = new Intent(GadgetStore.ACTION_GADGETS_CHANGED);
            changed.setPackage(getPackageName());
            changed.putExtra(GadgetStore.EXTRA_TABLE_ID, tableId);
            sendBroadcast(changed);
        } catch (Exception ignored) {
        }
        return true;
    }

    private String presenceForPeer(String presence) {
        return AppSettingsStore.PRESENCE_INVISIBLE.equals(presence)
                ? "offline"
                : presence;
    }
}
