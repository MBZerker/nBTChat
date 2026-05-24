package com.mbzerker.nbtchat;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MessageStore {
    public static final String ACTION_MESSAGES_CHANGED = "com.mbzerker.nbtchat.MESSAGES_CHANGED";
    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_MESSAGE_ID = "messageId";
    public static final String EXTRA_BODY = "body";
    public static final String EXTRA_KIND = "kind";
    public static final String EXTRA_SENT_AT = "sentAt";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_DELETED = "deleted";
    public static final String EXTRA_UNREAD = "unread";
    public static final String KIND_TEXT = "text";
    public static final String KIND_IMAGE = "image";
    public static final String KIND_GIF = "gif";
    public static final String KIND_VOICE = "voice";
    public static final String KIND_TABLE_100 = "table100";
    public static final String KIND_TABLE_100_CHOICE = "table100_choice";
    public static final String KIND_TABLE_100_CONFIRM = "table100_confirm";
    public static final String KIND_CONTACT_INVITE = "contact_invite";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_READ = "read";

    private static final String MESSAGE_PREFS = "chat_messages";
    private static final String META_PREFS = "chat_meta";
    private static final String KEY_UNREAD_PREFIX = "unread:";
    private static final int MAX_MESSAGES_PER_CHAT = 500;

    private final EncryptedPrefs messagePrefs;
    private final EncryptedPrefs metaPrefs;

    public MessageStore(Context context) {
        messagePrefs = new EncryptedPrefs(context, MESSAGE_PREFS);
        metaPrefs = new EncryptedPrefs(context, META_PREFS);
    }

    public synchronized void addMessage(String address, String body, boolean mine, long sentAt, boolean incrementUnread) {
        addMessage(address, createId(), KIND_TEXT, body, "", 0L, mine, sentAt, mine ? STATUS_PENDING : STATUS_DELIVERED, incrementUnread);
    }

    public synchronized boolean addMessage(String address, String id, String kind, String body, String mediaBase64,
                                           long durationMs, boolean mine, long sentAt, String status, boolean incrementUnread) {
        return addMessage(address, id, kind, body, mediaBase64, durationMs, mine, sentAt, status, incrementUnread, "", "");
    }

    public synchronized boolean addMessage(String address, String id, String kind, String body, String mediaBase64,
                                           long durationMs, boolean mine, long sentAt, String status, boolean incrementUnread,
                                           String replyToId, String replyPreview) {
        if (address == null || address.trim().isEmpty() || id == null || id.trim().isEmpty()) {
            return false;
        }
        boolean hasContent = (body != null && !body.trim().isEmpty()) || (mediaBase64 != null && !mediaBase64.trim().isEmpty());
        if (!hasContent) {
            return false;
        }
        if (containsMessage(address, id)) {
            return false;
        }
        try {
            JSONArray messages = rawMessages(address);
            JSONObject message = new JSONObject();
            message.put("id", id);
            message.put("kind", clean(kind, KIND_TEXT));
            message.put("body", body == null ? "" : body);
            message.put("mediaBase64", mediaBase64 == null ? "" : mediaBase64);
            message.put("durationMs", durationMs);
            message.put("mine", mine);
            message.put("sentAt", sentAt);
            message.put("status", clean(status, mine ? STATUS_PENDING : STATUS_DELIVERED));
            message.put("replyToId", clean(replyToId, ""));
            message.put("replyPreview", clean(replyPreview, ""));
            messages.put(message);
            while (messages.length() > MAX_MESSAGES_PER_CHAT) {
                JSONArray trimmed = new JSONArray();
                for (int i = 1; i < messages.length(); i++) {
                    trimmed.put(messages.getJSONObject(i));
                }
                messages = trimmed;
            }
            messagePrefs.edit().putString(address, messages.toString()).apply();
            if (incrementUnread) {
                metaPrefs.edit().putInt(KEY_UNREAD_PREFIX + address, getUnread(address) + 1).apply();
            }
            return true;
        } catch (JSONException ignored) {
            return false;
        }
    }

    public synchronized void updateStatus(String address, String id, String status) {
        if (address == null || id == null || status == null) {
            return;
        }
        try {
            JSONArray messages = rawMessages(address);
            boolean changed = false;
            for (int i = 0; i < messages.length(); i++) {
                JSONObject item = messages.getJSONObject(i);
                if (id.equals(item.optString("id", "")) && item.optBoolean("mine", false)) {
                    item.put("status", strongerStatus(item.optString("status", STATUS_PENDING), status));
                    changed = true;
                }
            }
            if (changed) {
                messagePrefs.edit().putString(address, messages.toString()).apply();
            }
        } catch (JSONException ignored) {
        }
    }

    public synchronized void deleteConversation(String address) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        messagePrefs.edit().remove(address).apply();
        metaPrefs.edit().remove(KEY_UNREAD_PREFIX + address).apply();
    }

    public synchronized void deleteMessage(String address, String id) {
        if (address == null || address.trim().isEmpty() || id == null || id.trim().isEmpty()) {
            return;
        }
        try {
            JSONArray messages = rawMessages(address);
            JSONArray kept = new JSONArray();
            boolean changed = false;
            for (int i = 0; i < messages.length(); i++) {
                JSONObject item = messages.getJSONObject(i);
                if (id.equals(item.optString("id", ""))) {
                    changed = true;
                    continue;
                }
                kept.put(item);
            }
            if (changed) {
                messagePrefs.edit().putString(address, kept.toString()).apply();
            }
        } catch (JSONException ignored) {
        }
    }

    public synchronized List<ChatMessage> loadMessages(String address) {
        List<ChatMessage> messages = new ArrayList<>();
        if (address == null || address.trim().isEmpty()) {
            return messages;
        }
        try {
            JSONArray raw = rawMessages(address);
            for (int i = 0; i < raw.length(); i++) {
                JSONObject item = raw.getJSONObject(i);
                messages.add(new ChatMessage(
                        item.optString("id", ""),
                        item.optString("kind", KIND_TEXT),
                        item.optString("body", ""),
                        item.optString("mediaBase64", ""),
                        item.optLong("durationMs", 0L),
                        item.optBoolean("mine", false),
                        item.optLong("sentAt", 0L),
                        item.optString("status", item.optBoolean("mine", false) ? STATUS_SENT : STATUS_DELIVERED),
                        item.optString("replyToId", ""),
                        item.optString("replyPreview", "")
                ));
            }
        } catch (JSONException ignored) {
        }
        return messages;
    }

    public synchronized ChatMessage findMessage(String address, String id) {
        if (address == null || id == null || id.trim().isEmpty()) {
            return null;
        }
        for (ChatMessage message : loadMessages(address)) {
            if (id.equals(message.id)) {
                return message;
            }
        }
        return null;
    }

    public synchronized ConversationInfo getConversationInfo(String address) {
        List<ChatMessage> messages = loadMessages(address);
        ChatMessage last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
        return new ConversationInfo(
                address,
                last == null ? "" : preview(last),
                last == null ? 0L : last.sentAt,
                getUnread(address)
        );
    }

    public synchronized Map<String, ConversationInfo> loadConversationInfo() {
        Map<String, ConversationInfo> info = new LinkedHashMap<>();
        for (String address : messagePrefs.getAll().keySet()) {
            info.put(address, getConversationInfo(address));
        }
        return info;
    }

    public int getUnread(String address) {
        return metaPrefs.getInt(KEY_UNREAD_PREFIX + address, 0);
    }

    public void markRead(String address) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        metaPrefs.edit().putInt(KEY_UNREAD_PREFIX + address, 0).apply();
    }

    public synchronized List<String> unreadMessageIds(String address) {
        List<String> ids = new ArrayList<>();
        for (ChatMessage message : loadMessages(address)) {
            if (!message.mine && message.id != null && !message.id.isEmpty()) {
                ids.add(message.id);
            }
        }
        return ids;
    }

    public synchronized List<ChatMessage> undeliveredOutgoingMessages(String address) {
        List<ChatMessage> pending = new ArrayList<>();
        for (ChatMessage message : loadMessages(address)) {
            if (message.mine && !STATUS_READ.equals(message.status)) {
                pending.add(message);
            }
        }
        return pending;
    }

    public String createId() {
        return Long.toHexString(System.currentTimeMillis()) + "-" + Long.toHexString(Double.doubleToLongBits(Math.random()));
    }

    private boolean containsMessage(String address, String id) {
        try {
            JSONArray raw = rawMessages(address);
            for (int i = 0; i < raw.length(); i++) {
                if (id.equals(raw.getJSONObject(i).optString("id", ""))) {
                    return true;
                }
            }
        } catch (JSONException ignored) {
        }
        return false;
    }

    private JSONArray rawMessages(String address) throws JSONException {
        String raw = messagePrefs.getString(address, "[]");
        return new JSONArray(raw == null || raw.trim().isEmpty() ? "[]" : raw);
    }

    private String preview(ChatMessage message) {
        if (KIND_IMAGE.equals(message.kind) || KIND_GIF.equals(message.kind)) {
            return message.mine ? "Voce enviou uma imagem" : "Imagem";
        }
        if (KIND_VOICE.equals(message.kind)) {
            return message.mine ? "Voce enviou uma mensagem de voz" : "Mensagem de voz";
        }
        if (KIND_TABLE_100.equals(message.kind)) {
            return message.mine ? "Voce enviou uma cartela de eventos" : GadgetStore.TABLE_100_TITLE;
        }
        if (KIND_CONTACT_INVITE.equals(message.kind)) {
            return message.mine ? "Voce enviou um contato" : "Contato nBTChat";
        }
        return message.body;
    }

    private String clean(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String strongerStatus(String oldStatus, String newStatus) {
        return statusRank(newStatus) > statusRank(oldStatus) ? newStatus : oldStatus;
    }

    private int statusRank(String status) {
        if (STATUS_READ.equals(status)) {
            return 4;
        }
        if (STATUS_DELIVERED.equals(status)) {
            return 3;
        }
        if (STATUS_SENT.equals(status)) {
            return 2;
        }
        return 1;
    }

    public static final class ChatMessage {
        public final String id;
        public final String kind;
        public final String body;
        public final String mediaBase64;
        public final long durationMs;
        public final boolean mine;
        public final long sentAt;
        public final String status;
        public final String replyToId;
        public final String replyPreview;

        ChatMessage(String id, String kind, String body, String mediaBase64, long durationMs,
                    boolean mine, long sentAt, String status, String replyToId, String replyPreview) {
            this.id = id;
            this.kind = kind;
            this.body = body;
            this.mediaBase64 = mediaBase64;
            this.durationMs = durationMs;
            this.mine = mine;
            this.sentAt = sentAt;
            this.status = status;
            this.replyToId = replyToId;
            this.replyPreview = replyPreview;
        }
    }

    public static final class ConversationInfo {
        public final String address;
        public final String lastBody;
        public final long lastAt;
        public final int unread;

        ConversationInfo(String address, String lastBody, long lastAt, int unread) {
            this.address = address;
            this.lastBody = lastBody;
            this.lastAt = lastAt;
            this.unread = unread;
        }
    }
}
