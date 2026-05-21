package com.mbzerker.nbtchat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MainActivity extends Activity implements BtChatManager.Listener {
    private static final int REQUEST_PERMISSIONS = 100;
    private static final int REQUEST_ENABLE_BT = 101;
    private static final int REQUEST_PICK_PHOTO = 102;
    private static final int REQUEST_CAPTURE_PHOTO = 103;
    private static final int REQUEST_PICK_CHAT_IMAGE = 104;
    private static final int REQUEST_CAPTURE_CHAT_IMAGE = 105;

    private static final String[] GENDER_LABELS = {
            "Masculino",
            "Feminino",
            "Outro / prefiro nao informar"
    };

    private static final String[] EMOJIS = {
            "\uD83D\uDE00", "\uD83D\uDE02", "\uD83D\uDE0D", "\uD83D\uDE0E", "\uD83E\uDD14", "\uD83D\uDE22",
            "\uD83D\uDE21", "\uD83D\uDC4D", "\uD83D\uDE4F", "\u2764\uFE0F", "\uD83D\uDD25", "\uD83C\uDF89",
            "\uD83D\uDE09", "\uD83D\uDE05", "\uD83E\uDD17", "\uD83D\uDE31", "\uD83D\uDC4F", "\uD83E\uDD1D",
            "\uD83D\uDCAA", "\uD83C\uDF7A", "\uD83C\uDFB5", "\uD83D\uDCA1", "\u2705", "\uD83D\uDE80"
    };
    private static final String UPDATE_MANIFEST_URL = "https://mbzerker.github.io/nBTChat/update.json";
    private static final Pattern LINK_PATTERN = Pattern.compile("(?i)\\b((?:https?://|www\\.)[^\\s<>()]+)");

    private final Map<String, BtChatManager.DeviceCandidate> discoveredDevices = new LinkedHashMap<>();
    private final Map<String, TextView> receiptViews = new LinkedHashMap<>();
    private final Map<String, VoiceControls> voiceControls = new LinkedHashMap<>();
    private final Set<String> renderedMessageIds = new HashSet<>();
    private final List<PendingOutgoing> pendingOutgoing = new ArrayList<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private ProfileStore profileStore;
    private MessageStore messageStore;
    private ThemeStore themeStore;
    private AppSettingsStore settingsStore;
    private BtChatManager btChatManager;
    private TextView stateText;
    private LinearLayout contactList;
    private LinearLayout messageList;
    private ScrollView messageScroll;
    private EditText messageInput;
    private ImageView profilePreview;
    private boolean darkMode;
    private boolean scanSuggestionShown;
    private boolean recordingVoice;
    private String editingPhotoBase64 = "";
    private MediaRecorder mediaRecorder;
    private File voiceFile;
    private long voiceStartedAt;
    private Uri pendingCameraUri;
    private String currentRemoteAddress = "";
    private UserProfile currentRemoteProfile = UserProfile.empty();
    private String currentFingerprint = "";
    private String currentScreen = "";
    private boolean messageReceiverRegistered;
    private boolean updateAvailable;
    private String updateVersionName = "";
    private String updatePageUrl = "https://mbzerker.github.io/nBTChat/";
    private String updateApkUrl = "https://mbzerker.github.io/nBTChat/nBTChat.apk";
    private MediaPlayer playingVoicePlayer;
    private File playingVoiceFile;
    private String playingVoiceId = "";
    private VoiceControls playingVoiceControls;
    private Runnable voiceTicker;

    private final BroadcastReceiver messageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!MessageStore.ACTION_MESSAGES_CHANGED.equals(intent.getAction())) {
                return;
            }
            String address = intent.getStringExtra(MessageStore.EXTRA_ADDRESS);
            String id = intent.getStringExtra(MessageStore.EXTRA_MESSAGE_ID);
            String status = intent.getStringExtra(MessageStore.EXTRA_STATUS);
            handleStoredMessageChange(address, id, status);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        profileStore = new ProfileStore(this);
        messageStore = new MessageStore(this);
        themeStore = new ThemeStore(this);
        settingsStore = new AppSettingsStore(this);
        darkMode = themeStore.isDarkMode();
        btChatManager = new BtChatManager(this, this);
        NotificationHelper.ensureChannels(this);
        registerMessageReceiver();
        applySystemBars();
        checkForUpdates(false);

        if (profileStore.hasLocalProfile()) {
            showInitialScreen();
        } else {
            showProfileScreen();
        }
        if (!requestMissingPermissions()) {
            tryStartBluetooth();
        }
        openChatFromIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVoicePlayback(false);
        if (btChatManager != null) {
            btChatManager.stop();
        }
        unregisterMessageReceiver();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openChatFromIntent(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            tryStartBluetooth();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            tryStartBluetooth();
        } else if (requestCode == REQUEST_PICK_PHOTO && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                updateEditingPhoto(compressImage(uri));
            }
        } else if (requestCode == REQUEST_CAPTURE_PHOTO && resultCode == RESULT_OK && data != null) {
            if (pendingCameraUri != null) {
                updateEditingPhoto(compressImage(pendingCameraUri));
                pendingCameraUri = null;
            } else {
                Object photo = data.getExtras() == null ? null : data.getExtras().get("data");
                if (photo instanceof Bitmap) {
                    updateEditingPhoto(compressBitmap((Bitmap) photo));
                }
            }
        } else if (requestCode == REQUEST_CAPTURE_PHOTO && resultCode == RESULT_OK) {
            if (pendingCameraUri != null) {
                updateEditingPhoto(compressImage(pendingCameraUri));
                pendingCameraUri = null;
            }
        } else if (requestCode == REQUEST_CAPTURE_PHOTO) {
            if (pendingCameraUri != null) {
                try {
                    getContentResolver().delete(pendingCameraUri, null, null);
                } catch (Exception ignored) {
                }
            }
            pendingCameraUri = null;
        } else if (requestCode == REQUEST_PICK_CHAT_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                sendImageMessage(compressImage(uri));
            }
        } else if (requestCode == REQUEST_CAPTURE_CHAT_IMAGE && resultCode == RESULT_OK) {
            if (pendingCameraUri != null) {
                sendImageMessage(compressImage(pendingCameraUri));
                pendingCameraUri = null;
            }
        } else if (requestCode == REQUEST_CAPTURE_CHAT_IMAGE) {
            pendingCameraUri = null;
        }
    }

    private boolean requestMissingPermissions() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfMissing(missing, Manifest.permission.BLUETOOTH_SCAN);
            addIfMissing(missing, Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(missing, Manifest.permission.POST_NOTIFICATIONS);
        }
        addIfMissing(missing, Manifest.permission.RECORD_AUDIO);
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
            return true;
        }
        return false;
    }

    private void addIfMissing(List<String> missing, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission);
        }
    }

    private void registerMessageReceiver() {
        if (messageReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(MessageStore.ACTION_MESSAGES_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(messageChangedReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(messageChangedReceiver, filter);
        }
        messageReceiverRegistered = true;
    }

    private void unregisterMessageReceiver() {
        if (!messageReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(messageChangedReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        messageReceiverRegistered = false;
    }

    private void startOnlineService() {
        if (!profileStore.hasLocalProfile()) {
            return;
        }
        Intent serviceIntent = new Intent(this, BluetoothForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void notifyProfileUpdated() {
        if (!profileStore.hasLocalProfile()) {
            return;
        }
        Intent serviceIntent = new Intent(this, BluetoothForegroundService.class);
        serviceIntent.setAction(BluetoothForegroundService.ACTION_PROFILE_UPDATED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void openChatFromIntent(Intent intent) {
        if (intent == null || !profileStore.hasLocalProfile()) {
            return;
        }
        String address = intent.getStringExtra(MessageStore.EXTRA_ADDRESS);
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        currentRemoteAddress = address;
        currentRemoteProfile = profileStore.loadContact(address);
        currentFingerprint = profileStore.loadFingerprint(address);
        showChatScreen(currentRemoteProfile, currentFingerprint);
    }

    @SuppressLint("MissingPermission")
    private void tryStartBluetooth() {
        if (btChatManager == null || !btChatManager.isBluetoothAvailable()) {
            showState("Bluetooth indisponivel neste aparelho.");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            showState("Permissao Bluetooth pendente.");
            return;
        }
        if (!btChatManager.isBluetoothEnabled()) {
            try {
                startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
            } catch (SecurityException ex) {
                showState("Libere a permissao Bluetooth nas configuracoes do app.");
            }
            return;
        }
        startOnlineService();
        if ("scanner".equals(currentScreen) && discoveredDevices.isEmpty()) {
            btChatManager.startNearbyDiscovery();
        }
    }

    private void showInitialScreen() {
        if (conversationCount() == 0) {
            showNearbyScannerScreen(true);
        } else {
            showHomeScreen();
        }
    }

    private void showHomeScreen() {
        int contactCount = conversationCount();
        if (contactCount == 0) {
            showNearbyScannerScreen(true);
            return;
        }

        currentScreen = "home";
        messageList = null;

        LinearLayout root = vertical();
        root.setBackgroundColor(color(background()));
        applyRootInsets(root, dp(16), dp(10), dp(16), dp(12));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("Conversas", 28, primary(), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        top.addView(title, titleParams);
        top.addView(menuButton());
        root.addView(top);

        stateText = text("Pronto para conversar por Bluetooth.", 14, secondary(), Typeface.NORMAL);
        root.addView(stateText, topMargin(dp(8)));

        Button scanButton = pillButton("Encontrar aparelhos proximos", accent(), darkMode ? "#12171D" : "#17212B");
        scanButton.setOnClickListener(v -> showNearbyScannerScreen(true));
        root.addView(scanButton, topMargin(dp(16)));

        ScrollView listScroll = new ScrollView(this);
        contactList = vertical();
        contactList.setPadding(0, dp(10), 0, dp(8));
        listScroll.addView(contactList);
        root.addView(listScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        renderContactList();

        setContentView(root);
        requestInsets(root);

        if (!scanSuggestionShown && settingsStore.shouldPromptNearbyScan(contactCount)) {
            scanSuggestionShown = true;
            root.postDelayed(() -> suggestNearbyScan(contactCount), 250);
        }
    }

    private int conversationCount() {
        Set<String> addresses = new HashSet<>();
        addresses.addAll(profileStore.loadContacts().keySet());
        addresses.addAll(messageStore.loadConversationInfo().keySet());
        return addresses.size();
    }

    private void renderContactList() {
        if (contactList == null) {
            return;
        }
        contactList.removeAllViews();

        LinkedHashMap<String, BtChatManager.DeviceCandidate> candidates = new LinkedHashMap<>();
        Set<String> addresses = new HashSet<>();
        for (String address : profileStore.loadContacts().keySet()) {
            addresses.add(address);
            BtChatManager.DeviceCandidate candidate = btChatManager.getPairedCandidate(address);
            if (candidate != null) {
                candidates.put(address, candidate);
            }
        }
        addresses.addAll(messageStore.loadConversationInfo().keySet());
        for (BtChatManager.DeviceCandidate candidate : discoveredDevices.values()) {
            if (candidate.paired && candidate.appAvailable) {
                addresses.add(candidate.address);
                candidates.put(candidate.address, candidate);
            }
        }

        if (addresses.isEmpty()) {
            TextView empty = text("Nenhum nBTChat pareado ainda.\nAbra o app no outro aparelho pareado e toque em procurar.", 15, secondary(), Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(dp(3), 1f);
            contactList.addView(empty, topMargin(dp(36)));
            return;
        }

        List<String> orderedAddresses = new ArrayList<>(addresses);
        Collections.sort(orderedAddresses, (left, right) -> {
            long leftAt = messageStore.getConversationInfo(left).lastAt;
            long rightAt = messageStore.getConversationInfo(right).lastAt;
            return Long.compare(rightAt, leftAt);
        });

        for (String address : orderedAddresses) {
            contactList.addView(contactRow(address, candidates.get(address)), topMargin(dp(8)));
        }
    }

    private void suggestNearbyScan(int contactCount) {
        if (!"home".equals(currentScreen) || isFinishing()) {
            return;
        }
        String message = contactCount == 1
                ? "Voce tem 1 contato. Quer procurar aparelhos proximos para adicionar mais conversas?"
                : "Voce tem " + contactCount + " contatos. Quer procurar aparelhos proximos para adicionar mais conversas?";
        new AlertDialog.Builder(this)
                .setTitle("Encontrar aparelhos")
                .setMessage(message)
                .setPositiveButton("Escanear", (dialog, which) -> showNearbyScannerScreen(true))
                .setNegativeButton("Agora nao", null)
                .show();
    }

    private void showNearbyScannerScreen(boolean autoStart) {
        currentScreen = "scanner";
        messageList = null;

        LinearLayout root = vertical();
        root.setBackgroundColor(color(background()));
        applyRootInsets(root, dp(16), dp(10), dp(16), dp(12));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        if (!profileStore.loadContacts().isEmpty()) {
            top.addView(iconButton(R.drawable.ic_back_24, "Voltar", dp(42), v -> showHomeScreen()));
        }

        TextView title = text("Encontrar proximos", 25, primary(), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(profileStore.loadContacts().isEmpty() ? 0 : dp(12), 0, 0, 0);
        top.addView(title, titleParams);
        top.addView(menuButton());
        root.addView(top);

        stateText = text("Procurando aparelhos com Bluetooth visivel.", 14, secondary(), Typeface.NORMAL);
        root.addView(stateText, topMargin(dp(8)));

        Button scanButton = pillButton("Escanear aparelhos proximos", accent(), darkMode ? "#12171D" : "#17212B");
        scanButton.setOnClickListener(v -> {
            discoveredDevices.clear();
            renderNearbyDeviceList();
            btChatManager.startNearbyDiscovery();
        });
        root.addView(scanButton, topMargin(dp(16)));

        TextView hint = text("No outro aparelho, deixe o nBTChat aberto e o Bluetooth visivel. Toque em Convidar para tentar conectar.", 13, secondary(), Typeface.NORMAL);
        hint.setLineSpacing(dp(2), 1f);
        root.addView(hint, topMargin(dp(10)));

        ScrollView listScroll = new ScrollView(this);
        contactList = vertical();
        contactList.setPadding(0, dp(10), 0, dp(8));
        listScroll.addView(contactList);
        root.addView(listScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        setContentView(root);
        requestInsets(root);

        if (autoStart) {
            discoveredDevices.clear();
            renderNearbyDeviceList();
            root.postDelayed(() -> btChatManager.startNearbyDiscovery(), 200);
        } else {
            renderNearbyDeviceList();
        }
    }

    private void renderNearbyDeviceList() {
        if (contactList == null) {
            return;
        }
        contactList.removeAllViews();
        if (discoveredDevices.isEmpty()) {
            TextView empty = text("Buscando aparelhos proximos...", 15, secondary(), Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            contactList.addView(empty, topMargin(dp(34)));
            return;
        }
        for (BtChatManager.DeviceCandidate candidate : discoveredDevices.values()) {
            contactList.addView(nearbyDeviceRow(candidate), topMargin(dp(8)));
        }
    }

    private View nearbyDeviceRow(BtChatManager.DeviceCandidate candidate) {
        UserProfile known = profileStore.loadContact(candidate.address);
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(rounded(surface(), dp(12), border()));

        ImageView avatar = new ImageView(this);
        applyAvatar(avatar, known);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout info = vertical();
        String title = known.isComplete() ? known.getDisplayName() : candidate.name;
        String subtitle;
        if (known.isComplete()) {
            subtitle = known.getStatus().isEmpty() ? "Contato conhecido" : known.getStatus();
        } else if (candidate.paired) {
            subtitle = "Pareado - tente convidar pelo nBTChat";
        } else {
            subtitle = "Proximo - pode pedir pareamento ao convidar";
        }
        info.addView(text(title, 16, primary(), Typeface.BOLD));
        info.addView(text(subtitle, 13, secondary(), Typeface.NORMAL));
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoParams.setMargins(dp(18), 0, 0, 0);
        row.addView(info, infoParams);

        ImageButton invite = iconButton(R.drawable.ic_invite_24, "Convidar", dp(48), null);
        invite.setOnClickListener(v -> {
            invite.setEnabled(false);
            invite.setColorFilter(color(darkMode ? "#12171D" : "#17212B"));
            invite.setBackground(rounded(accent(), dp(18), accent()));
            row.setBackground(rounded(darkMode ? "#2A332B" : "#FFF4E8", dp(12), accent()));
            Toast.makeText(this, "Convite enviado para " + candidate.name + ".", Toast.LENGTH_SHORT).show();
            btChatManager.connect(candidate);
            invite.postDelayed(() -> {
                if ("scanner".equals(currentScreen)) {
                    invite.setEnabled(true);
                    invite.setColorFilter(color(primary()));
                    invite.setBackground(rounded(surfaceAlt(), dp(18), border()));
                    row.setBackground(rounded(surface(), dp(12), border()));
                }
            }, 3500);
        });
        row.addView(invite, new LinearLayout.LayoutParams(dp(52), dp(48)));
        return row;
    }

    private View contactRow(String address, BtChatManager.DeviceCandidate candidate) {
        UserProfile known = profileStore.loadContact(address);
        MessageStore.ConversationInfo conversation = messageStore.getConversationInfo(address);
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(rounded(surface(), dp(12), border()));
        row.setOnClickListener(v -> {
            currentRemoteAddress = address;
            currentRemoteProfile = known;
            currentFingerprint = profileStore.loadFingerprint(address);
            showChatScreen(currentRemoteProfile, currentFingerprint);
            BtChatManager.DeviceCandidate target = candidate == null ? btChatManager.getPairedCandidate(address) : candidate;
            if (target != null) {
                btChatManager.connect(target);
            }
        });
        row.setOnLongClickListener(v -> {
            confirmDeleteConversation(address);
            return true;
        });

        ImageView avatar = new ImageView(this);
        applyAvatar(avatar, known);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout info = vertical();
        String title = known.isComplete() ? known.getDisplayName() : (candidate == null ? "Contato nBTChat" : candidate.name);
        String subtitle = conversation.lastBody.isEmpty()
                ? (known.getStatus().isEmpty() ? "Toque para abrir a conversa" : known.getStatus())
                : conversation.lastBody;
        info.addView(text(title, 17, primary(), Typeface.BOLD));
        info.addView(text(subtitle, 13, secondary(), Typeface.NORMAL));
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        infoParams.setMargins(dp(20), 0, 0, 0);
        row.addView(info, infoParams);

        LinearLayout meta = vertical();
        meta.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        TextView when = text(formatConversationTime(conversation.lastAt), 12, secondary(), Typeface.NORMAL);
        when.setGravity(Gravity.RIGHT);
        meta.addView(when);
        if (conversation.unread > 0) {
            TextView badge = text(String.valueOf(Math.min(conversation.unread, 99)), 12, "#FFFFFF", Typeface.BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(rounded("#16A34A", dp(12), "#16A34A"));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(conversation.unread > 9 ? 30 : 24), dp(24));
            badgeParams.setMargins(0, dp(7), 0, 0);
            meta.addView(badge, badgeParams);
        }
        row.addView(meta, new LinearLayout.LayoutParams(dp(78), LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void showProfileScreen() {
        currentScreen = "profile";
        UserProfile saved = profileStore.loadLocalProfile();
        editingPhotoBase64 = saved.getPhotoBase64();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = vertical();
        root.setBackgroundColor(color(background()));
        applyRootInsets(root, dp(22), dp(14), dp(22), dp(18));
        scrollView.addView(root, matchWrap());

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(saved.isComplete() ? "Editar perfil" : "Criar perfil", 28, primary(), Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(menuButton());
        root.addView(top);
        TextView subtitle = text("Este perfil aparece quando outro nBTChat se conecta a voce.", 15, secondary(), Typeface.NORMAL);
        root.addView(subtitle, topMargin(dp(6)));

        profilePreview = new ImageView(this);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(112), dp(112));
        avatarParams.gravity = Gravity.CENTER_HORIZONTAL;
        avatarParams.setMargins(0, dp(26), 0, dp(12));
        root.addView(profilePreview, avatarParams);
        applyAvatar(profilePreview, saved);

        LinearLayout photoActions = horizontal();
        photoActions.setGravity(Gravity.CENTER);
        Button cameraButton = pillButton("Camera", surfaceAlt(), primary());
        cameraButton.setOnClickListener(v -> capturePhoto());
        photoActions.addView(cameraButton, new LinearLayout.LayoutParams(dp(132), LinearLayout.LayoutParams.WRAP_CONTENT));
        Button galleryButton = pillButton("Galeria", surfaceAlt(), primary());
        galleryButton.setOnClickListener(v -> pickPhoto());
        photoActions.addView(galleryButton, leftMargin(dp(10), dp(132), LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(photoActions);

        EditText nameInput = input("Nome");
        nameInput.setText(saved.getDisplayName());
        root.addView(label("Nome"));
        root.addView(nameInput, topMargin(dp(6)));

        EditText statusInput = input("Recado");
        statusInput.setText(saved.getStatus());
        root.addView(label("Recado"));
        root.addView(statusInput, topMargin(dp(6)));

        Spinner genderSpinner = new Spinner(this);
        genderSpinner.setAdapter(createGenderAdapter());
        genderSpinner.setBackground(rounded(surface(), dp(12), border()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            genderSpinner.setPopupBackgroundDrawable(new ColorDrawable(color(surface())));
        }
        genderSpinner.setSelection(positionForGender(saved.getGender()));
        root.addView(label("Sexo"));
        root.addView(genderSpinner, topMargin(dp(6)));

        Button saveButton = pillButton("Salvar", accent(), darkMode ? "#12171D" : "#17212B");
        saveButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                nameInput.setError("Informe seu nome");
                return;
            }
            UserProfile profile = new UserProfile(
                    name,
                    statusInput.getText().toString(),
                    genderForPosition(genderSpinner.getSelectedItemPosition()),
                    editingPhotoBase64
            );
            profileStore.saveLocalProfile(profile);
            btChatManager.sendProfileUpdate();
            notifyProfileUpdated();
            hideKeyboard(nameInput);
            showInitialScreen();
            tryStartBluetooth();
        });
        root.addView(saveButton, topMargin(dp(26)));

        if (saved.isComplete()) {
            Button cancelButton = pillButton("Voltar", surfaceAlt(), primary());
            cancelButton.setOnClickListener(v -> showHomeScreen());
            root.addView(cancelButton, topMargin(dp(10)));
        }

        setContentView(scrollView);
        requestInsets(root);
    }

    private void showChatScreen(UserProfile profile, String fingerprint) {
        currentScreen = "chat";
        currentRemoteProfile = profile == null ? UserProfile.empty() : profile;
        currentFingerprint = fingerprint == null ? "" : fingerprint;

        LinearLayout root = vertical();
        root.setBackgroundColor(color(chatBackground()));
        applyRootInsets(root, dp(12), dp(8), dp(12), dp(8));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconButton(R.drawable.ic_back_24, "Voltar", dp(42), v -> showHomeScreen()));

        ImageView avatar = new ImageView(this);
        applyAvatar(avatar, currentRemoteProfile);
        avatar.setOnClickListener(v -> showContactInfoDialog());
        top.addView(avatar, leftMargin(dp(10), dp(46), dp(46)));

        LinearLayout who = vertical();
        String title = currentRemoteProfile.isComplete() ? currentRemoteProfile.getDisplayName() : "Conversa Bluetooth";
        who.addView(text(title, 17, primary(), Typeface.BOLD));
        String subtitle = currentRemoteProfile.getStatus().isEmpty() ? "Bluetooth seguro" : currentRemoteProfile.getStatus();
        who.addView(text(subtitle, 12, secondary(), Typeface.NORMAL));
        LinearLayout.LayoutParams whoParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        whoParams.setMargins(dp(14), 0, 0, 0);
        top.addView(who, whoParams);

        top.addView(menuButton());
        root.addView(top);

        messageScroll = new ScrollView(this);
        messageList = vertical();
        messageList.setPadding(0, dp(12), 0, dp(12));
        messageScroll.addView(messageList);
        root.addView(messageScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        List<String> readIds = messageStore.unreadMessageIds(currentRemoteAddress);
        messageStore.markRead(currentRemoteAddress);
        for (String id : readIds) {
            btChatManager.sendReceipt(currentRemoteAddress, id, MessageStore.STATUS_READ);
        }
        renderChatHistory(true);

        LinearLayout composer = horizontal();
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(0, dp(6), 0, 0);
        composer.addView(iconButton(R.drawable.ic_emoji_24, "Emoji", dp(46), v -> showEmojiPicker(v)));

        LinearLayout inputShell = horizontal();
        inputShell.setGravity(Gravity.CENTER_VERTICAL);
        inputShell.setPadding(dp(2), 0, dp(4), 0);
        inputShell.setBackground(rounded(surface(), dp(18), border()));
        messageInput = inlineInput("Mensagem");
        messageInput.setSingleLine(false);
        messageInput.setMinLines(1);
        messageInput.setMaxLines(4);
        inputShell.addView(messageInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        inputShell.addView(iconButton(R.drawable.ic_camera_24, "Camera", dp(38), v -> captureChatImage()));
        inputShell.addView(iconButton(R.drawable.ic_clip_24, "Galeria", dp(38), v -> pickChatImage()));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        inputParams.setMargins(dp(8), 0, dp(8), 0);
        composer.addView(inputShell, inputParams);

        composer.addView(voiceRecordButton());
        composer.addView(iconButton(R.drawable.ic_send_24, "Enviar", dp(46), v -> sendCurrentMessage()));
        root.addView(composer);

        setContentView(root);
        requestInsets(root);
    }

    private void showEmojiPicker(View anchor) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        grid.setPadding(dp(10), dp(10), dp(10), dp(10));
        grid.setBackground(rounded(surface(), dp(18), border()));
        for (String emoji : EMOJIS) {
            TextView chip = text(emoji, 24, primary(), Typeface.NORMAL);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(rounded(surfaceAlt(), dp(16), border()));
            chip.setOnClickListener(v -> {
                if (messageInput != null) {
                    int start = Math.max(messageInput.getSelectionStart(), 0);
                    messageInput.getText().insert(start, emoji);
                }
            });
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dp(44);
            params.height = dp(40);
            params.setMargins(dp(3), dp(3), dp(3), dp(3));
            grid.addView(chip, params);
        }

        PopupWindow popup = new PopupWindow(grid, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.showAsDropDown(anchor, 0, -dp(238));
    }

    private void showContactInfoDialog() {
        LinearLayout content = vertical();
        content.setPadding(dp(22), dp(20), dp(22), dp(16));
        content.setBackgroundColor(color(surface()));

        ImageView avatar = new ImageView(this);
        applyAvatar(avatar, currentRemoteProfile);
        avatar.setOnClickListener(v -> showFullscreenPhoto(currentRemoteProfile));
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(118), dp(118));
        avatarParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(avatar, avatarParams);

        String name = currentRemoteProfile.isComplete() ? currentRemoteProfile.getDisplayName() : "Contato nBTChat";
        TextView nameView = text(name, 23, primary(), Typeface.BOLD);
        nameView.setGravity(Gravity.CENTER);
        content.addView(nameView, topMargin(dp(12)));

        String status = currentRemoteProfile.getStatus().isEmpty() ? "Sem recado" : currentRemoteProfile.getStatus();
        TextView statusView = text(status, 15, secondary(), Typeface.NORMAL);
        statusView.setGravity(Gravity.CENTER);
        content.addView(statusView, topMargin(dp(4)));

        String fingerprint = currentFingerprint.isEmpty()
                ? profileStore.loadFingerprint(currentRemoteAddress)
                : currentFingerprint;
        TextView keyLabel = text("Chave", 12, secondary(), Typeface.BOLD);
        content.addView(keyLabel, topMargin(dp(18)));
        TextView key = text(fingerprint == null || fingerprint.isEmpty() ? "Ainda nao confirmada nesta sessao." : fingerprint, 15, primary(), Typeface.NORMAL);
        key.setPadding(dp(12), dp(10), dp(12), dp(10));
        key.setBackground(rounded(surfaceAlt(), dp(12), border()));
        content.addView(key, topMargin(dp(6)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(content)
                .setPositiveButton("Fechar", null)
                .create();
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });
        dialog.show();
    }

    private void showFullscreenPhoto(UserProfile profile) {
        LinearLayout root = vertical();
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.BLACK);

        ImageButton close = iconButton(R.drawable.ic_close_24, "Fechar", dp(46), v -> {
            View parent = (View) root.getParent();
            if (parent != null) {
                parent.setVisibility(View.GONE);
            }
        });
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        closeParams.gravity = Gravity.RIGHT;
        root.addView(close, closeParams);

        ImageView photo = new ImageView(this);
        applyAvatar(photo, profile);
        photo.setAdjustViewBounds(true);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(photo, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(root)
                .create();
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
                dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            }
        });
        dialog.show();
    }

    private void showFullscreenImage(String imageBase64) {
        Bitmap bitmap = decodePhoto(imageBase64);
        if (bitmap == null) {
            Toast.makeText(this, "Nao foi possivel abrir a imagem.", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout root = vertical();
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        root.setBackgroundColor(Color.BLACK);

        ImageButton close = iconButton(R.drawable.ic_close_24, "Fechar", dp(44), null);
        close.setAlpha(0.72f);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        closeParams.gravity = Gravity.RIGHT;
        root.addView(close, closeParams);

        ImageView image = new ImageView(this);
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(root)
                .create();
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnShowListener(d -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
                dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            }
        });
        dialog.show();
    }

    private void sendCurrentMessage() {
        if (messageInput == null) {
            return;
        }
        String body = messageInput.getText().toString().trim();
        if (body.isEmpty()) {
            return;
        }
        if (!hasCurrentConversation()) {
            return;
        }
        messageInput.setText("");
        long sentAt = System.currentTimeMillis();
        String id = messageStore.createId();
        messageStore.addMessage(currentRemoteAddress, id, MessageStore.KIND_TEXT, body, "", 0L, true, sentAt, MessageStore.STATUS_PENDING, false);
        addMessageBubble(id, body, true, MessageStore.KIND_TEXT, "", 0L, MessageStore.STATUS_PENDING, true);
        sendOrQueueOutgoing(currentRemoteAddress, id, MessageStore.KIND_TEXT, body, "", 0L, sentAt);
    }

    private boolean ensureCanSendMedia() {
        if (!hasCurrentConversation()) {
            return false;
        }
        return true;
    }

    private boolean hasCurrentConversation() {
        if (currentRemoteAddress == null || currentRemoteAddress.isEmpty()) {
            Toast.makeText(this, "Abra uma conversa antes de enviar.", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void sendImageMessage(String imageBase64) {
        if (imageBase64 == null || imageBase64.isEmpty() || !ensureCanSendMedia()) {
            return;
        }
        long sentAt = System.currentTimeMillis();
        String id = messageStore.createId();
        messageStore.addMessage(currentRemoteAddress, id, MessageStore.KIND_IMAGE, "", imageBase64, 0L, true, sentAt, MessageStore.STATUS_PENDING, false);
        addMessageBubble(id, "", true, MessageStore.KIND_IMAGE, imageBase64, 0L, MessageStore.STATUS_PENDING, true);
        sendOrQueueOutgoing(currentRemoteAddress, id, MessageStore.KIND_IMAGE, "", imageBase64, 0L, sentAt);
    }

    private void sendVoiceMessage(String audioBase64, long durationMs) {
        if (audioBase64 == null || audioBase64.isEmpty() || !ensureCanSendMedia()) {
            return;
        }
        long sentAt = System.currentTimeMillis();
        String id = messageStore.createId();
        messageStore.addMessage(currentRemoteAddress, id, MessageStore.KIND_VOICE, "", audioBase64, durationMs, true, sentAt, MessageStore.STATUS_PENDING, false);
        addMessageBubble(id, "", true, MessageStore.KIND_VOICE, audioBase64, durationMs, MessageStore.STATUS_PENDING, true);
        sendOrQueueOutgoing(currentRemoteAddress, id, MessageStore.KIND_VOICE, "", audioBase64, durationMs, sentAt);
    }

    private void sendOrQueueOutgoing(String address, String id, String kind, String body, String mediaBase64, long durationMs, long sentAt) {
        PendingOutgoing outgoing = new PendingOutgoing(address, id, kind, body, mediaBase64, durationMs, sentAt);
        if (btChatManager.canSendTo(address)) {
            btChatManager.sendChatMessage(address, id, kind, body, mediaBase64, durationMs, sentAt);
            return;
        }
        pendingOutgoing.add(outgoing);
        connectForAddress(address);
        Toast.makeText(this, "Vou enviar assim que o Bluetooth conectar.", Toast.LENGTH_SHORT).show();
    }

    private void flushPendingOutgoing(String address) {
        if (address == null || address.isEmpty() || pendingOutgoing.isEmpty() || !btChatManager.canSendTo(address)) {
            return;
        }
        List<PendingOutgoing> sent = new ArrayList<>();
        for (PendingOutgoing outgoing : pendingOutgoing) {
            if (address.equals(outgoing.address)) {
                btChatManager.sendChatMessage(outgoing.address, outgoing.id, outgoing.kind, outgoing.body, outgoing.mediaBase64, outgoing.durationMs, outgoing.sentAt);
                sent.add(outgoing);
            }
        }
        pendingOutgoing.removeAll(sent);
    }

    private void connectForAddress(String address) {
        BtChatManager.DeviceCandidate target = btChatManager.getPairedCandidate(address);
        if (target != null && !btChatManager.isConnectedTo(address)) {
            btChatManager.connect(target);
        }
    }

    private void pickChatImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_CHAT_IMAGE);
        } catch (Exception ex) {
            Toast.makeText(this, "Nao foi possivel abrir a galeria.", Toast.LENGTH_LONG).show();
        }
    }

    private void captureChatImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra("android.intent.extras.CAMERA_FACING", 1);
        intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
        try {
            pendingCameraUri = createCameraImageUri();
            if (pendingCameraUri != null) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            startActivityForResult(intent, REQUEST_CAPTURE_CHAT_IMAGE);
        } catch (Exception ex) {
            pendingCameraUri = null;
            Toast.makeText(this, "Nenhum app de camera encontrado.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean startVoiceRecording() {
        if (!ensureCanSendMedia()) {
            return false;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMissingPermissions();
            return false;
        }
        try {
            voiceFile = new File(getCacheDir(), "voice_" + System.currentTimeMillis() + ".m4a");
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(32000);
            mediaRecorder.setAudioSamplingRate(16000);
            mediaRecorder.setOutputFile(voiceFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
            voiceStartedAt = System.currentTimeMillis();
            recordingVoice = true;
            return true;
        } catch (Exception ex) {
            recordingVoice = false;
            mediaRecorder = null;
            Toast.makeText(this, "Nao foi possivel gravar audio.", Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void stopVoiceRecording() {
        if (!recordingVoice || mediaRecorder == null) {
            return;
        }
        long duration = Math.max(500L, System.currentTimeMillis() - voiceStartedAt);
        try {
            mediaRecorder.stop();
        } catch (Exception ignored) {
        }
        try {
            mediaRecorder.release();
        } catch (Exception ignored) {
        }
        mediaRecorder = null;
        recordingVoice = false;
        if (voiceFile != null && voiceFile.exists()) {
            sendVoiceMessage(fileToBase64(voiceFile), duration);
            voiceFile.delete();
        }
        voiceFile = null;
    }

    private String fileToBase64(File file) {
        try (FileInputStream inputStream = new FileInputStream(file);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) {
            return "";
        }
    }

    private void toggleVoicePlayback(String id, String audioBase64, VoiceControls controls) {
        if (id != null && id.equals(playingVoiceId) && playingVoicePlayer != null) {
            if (playingVoicePlayer.isPlaying()) {
                playingVoicePlayer.pause();
                controls.button.setImageResource(R.drawable.ic_play_24);
            } else {
                playingVoicePlayer.start();
                controls.button.setImageResource(R.drawable.ic_pause_24);
                scheduleVoiceTicker();
            }
            return;
        }
        startVoicePlayback(id, audioBase64, controls, true);
    }

    private void startVoicePlayback(String id, String audioBase64, VoiceControls controls, boolean autoNext) {
        if (audioBase64 == null || audioBase64.isEmpty() || controls == null) {
            return;
        }
        stopVoicePlayback(false);
        try {
            playingVoiceFile = new File(getCacheDir(), "play_" + System.currentTimeMillis() + ".m4a");
            try (FileOutputStream outputStream = new FileOutputStream(playingVoiceFile)) {
                outputStream.write(Base64.decode(audioBase64, Base64.NO_WRAP));
            }
            playingVoicePlayer = new MediaPlayer();
            playingVoicePlayer.setDataSource(playingVoiceFile.getAbsolutePath());
            playingVoicePlayer.setOnCompletionListener(mp -> {
                String completedId = playingVoiceId;
                stopVoicePlayback(false);
                if (autoNext) {
                    playNextVoice(completedId);
                }
            });
            playingVoicePlayer.prepare();
            playingVoiceId = id == null ? "" : id;
            playingVoiceControls = controls;
            controls.seekBar.setMax(Math.max(1, playingVoicePlayer.getDuration()));
            controls.button.setImageResource(R.drawable.ic_pause_24);
            playingVoicePlayer.start();
            scheduleVoiceTicker();
        } catch (Exception ex) {
            stopVoicePlayback(false);
            Toast.makeText(this, "Nao foi possivel tocar o audio.", Toast.LENGTH_LONG).show();
        }
    }

    private void stopVoicePlayback(boolean keepButtonState) {
        if (voiceTicker != null) {
            uiHandler.removeCallbacks(voiceTicker);
            voiceTicker = null;
        }
        if (playingVoicePlayer != null) {
            try {
                playingVoicePlayer.stop();
            } catch (Exception ignored) {
            }
            playingVoicePlayer.release();
            playingVoicePlayer = null;
        }
        if (playingVoiceFile != null) {
            playingVoiceFile.delete();
            playingVoiceFile = null;
        }
        if (!keepButtonState && playingVoiceControls != null) {
            resetVoiceControls(playingVoiceControls);
        }
        playingVoiceControls = null;
        playingVoiceId = "";
    }

    private void scheduleVoiceTicker() {
        if (playingVoicePlayer == null || playingVoiceControls == null) {
            return;
        }
        if (voiceTicker != null) {
            uiHandler.removeCallbacks(voiceTicker);
        }
        voiceTicker = () -> {
            updateVoiceProgress();
            if (playingVoicePlayer != null && playingVoicePlayer.isPlaying()) {
                uiHandler.postDelayed(voiceTicker, 250);
            }
        };
        uiHandler.post(voiceTicker);
    }

    private void updateVoiceProgress() {
        if (playingVoicePlayer == null || playingVoiceControls == null) {
            return;
        }
        int duration = Math.max(1, playingVoicePlayer.getDuration());
        int position = Math.max(0, playingVoicePlayer.getCurrentPosition());
        playingVoiceControls.seekBar.setMax(duration);
        playingVoiceControls.seekBar.setProgress(Math.min(position, duration));
        playingVoiceControls.time.setText(formatDuration(position) + "/" + formatDuration(duration));
    }

    private void resetVoiceControls(VoiceControls controls) {
        controls.button.setImageResource(R.drawable.ic_play_24);
        controls.seekBar.setProgress(0);
        controls.time.setText("00:00/" + formatDuration(controls.durationMs));
    }

    private void playNextVoice(String completedId) {
        boolean found = false;
        for (MessageStore.ChatMessage message : messageStore.loadMessages(currentRemoteAddress)) {
            if (!found) {
                found = completedId != null && completedId.equals(message.id);
                continue;
            }
            if (MessageStore.KIND_VOICE.equals(message.kind) && message.mediaBase64 != null && !message.mediaBase64.isEmpty()) {
                VoiceControls controls = voiceControls.get(message.id);
                if (controls != null) {
                    startVoicePlayback(message.id, message.mediaBase64, controls, true);
                }
                return;
            }
        }
    }

    private String formatDuration(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        return String.format(Locale.getDefault(), "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    private void renderChatHistory(boolean scrollBottom) {
        if (messageList == null) {
            return;
        }
        messageList.removeAllViews();
        receiptViews.clear();
        voiceControls.clear();
        renderedMessageIds.clear();
        List<MessageStore.ChatMessage> history = messageStore.loadMessages(currentRemoteAddress);
        if (history.isEmpty()) {
            addSystemMessage("Conversa criptografada por Bluetooth.");
        } else {
            for (MessageStore.ChatMessage message : history) {
                addMessageBubble(message.id, message.body, message.mine, message.kind, message.mediaBase64, message.durationMs, message.status, false);
            }
        }
        if (scrollBottom) {
            scrollMessagesToBottom();
        }
    }

    private TextView messageText(String body, boolean mine) {
        TextView view = text(body == null ? "" : body, 16, mine ? "#FFFFFF" : primary(), Typeface.NORMAL);
        applyLinkSpans(view, body == null ? "" : body, mine);
        return view;
    }

    private void applyLinkSpans(TextView view, String body, boolean mine) {
        Matcher matcher = LINK_PATTERN.matcher(body);
        if (!matcher.find()) {
            return;
        }
        SpannableString spannable = new SpannableString(body);
        matcher.reset();
        while (matcher.find()) {
            String rawUrl = matcher.group(1);
            String url = rawUrl.toLowerCase(Locale.ROOT).startsWith("www.") ? "https://" + rawUrl : rawUrl;
            spannable.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View widget) {
                    openRecognizedLink(url);
                }

                @Override
                public void updateDrawState(TextPaint ds) {
                    super.updateDrawState(ds);
                    ds.setColor(color(mine ? "#B9E8FF" : "#0F766E"));
                    ds.setUnderlineText(true);
                }
            }, matcher.start(1), matcher.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        view.setText(spannable);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setHighlightColor(Color.TRANSPARENT);
    }

    private void openRecognizedLink(String url) {
        Uri uri;
        try {
            uri = Uri.parse(url);
        } catch (Exception ex) {
            Toast.makeText(this, "Link invalido.", Toast.LENGTH_LONG).show();
            return;
        }
        if (isTrustedLink(uri)) {
            openExternalLink(uri);
            return;
        }
        String host = uri.getHost() == null ? url : uri.getHost();
        new AlertDialog.Builder(this)
                .setTitle("Abrir link desconhecido?")
                .setMessage("Este link aponta para " + host + ". Abra apenas se confiar em quem enviou.")
                .setPositiveButton("Abrir", (dialog, which) -> openExternalLink(uri))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private boolean isTrustedLink(Uri uri) {
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("youtu.be")
                || host.endsWith(".youtube.com")
                || host.equals("youtube.com")
                || host.equals("google.com")
                || host.endsWith(".google.com")
                || host.equals("openai.com")
                || host.endsWith(".openai.com")
                || host.equals("chatgpt.com")
                || host.endsWith(".chatgpt.com")
                || host.equals("github.com")
                || host.endsWith(".github.com")
                || host.endsWith(".github.io")
                || host.equals("android.com")
                || host.endsWith(".android.com");
    }

    private void openExternalLink(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception ex) {
            Toast.makeText(this, "Nao foi possivel abrir o link.", Toast.LENGTH_LONG).show();
        }
    }

    private void addSystemMessage(String body) {
        if (messageList == null) {
            return;
        }
        TextView message = text(body, 12, secondary(), Typeface.NORMAL);
        message.setGravity(Gravity.CENTER);
        messageList.addView(message, topMargin(dp(8)));
        scrollMessagesToBottom();
    }

    private void addMessageBubble(String body, boolean mine) {
        addMessageBubble("", body, mine, MessageStore.KIND_TEXT, "", 0L, mine ? MessageStore.STATUS_SENT : MessageStore.STATUS_DELIVERED, true);
    }

    private void addMessageBubble(String body, boolean mine, String kind, String mediaBase64, long durationMs, String status) {
        addMessageBubble("", body, mine, kind, mediaBase64, durationMs, status, true);
    }

    private void addMessageBubble(String id, String body, boolean mine, String kind, String mediaBase64, long durationMs, String status, boolean scrollBottom) {
        if (messageList == null) {
            showChatScreen(currentRemoteProfile, currentFingerprint);
        }
        if (id != null && !id.isEmpty()) {
            if (renderedMessageIds.contains(id)) {
                return;
            }
            renderedMessageIds.add(id);
        }
        LinearLayout bubble = vertical();
        bubble.setPadding(dp(13), dp(9), dp(13), dp(8));
        bubble.setBackground(rounded(mine ? "#0F766E" : surface(), dp(16), mine ? "#0F766E" : border()));
        bubble.setOnLongClickListener(v -> {
            showMessageActionDialog(id);
            return true;
        });

        if (MessageStore.KIND_IMAGE.equals(kind) && mediaBase64 != null && !mediaBase64.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap bitmap = decodePhoto(mediaBase64);
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
            }
            image.setOnClickListener(v -> showFullscreenImage(mediaBase64));
            bubble.addView(image, new LinearLayout.LayoutParams(dp(210), dp(150)));
            if (body != null && !body.isEmpty()) {
                bubble.addView(messageText(body, mine), topMargin(dp(6)));
            }
        } else if (MessageStore.KIND_VOICE.equals(kind)) {
            LinearLayout voiceRow = horizontal();
            voiceRow.setGravity(Gravity.CENTER_VERTICAL);
            ImageButton play = iconButton(R.drawable.ic_play_24, "Tocar audio", dp(42), null);
            SeekBar seekBar = new SeekBar(this);
            TextView time = text("00:00/" + formatDuration(durationMs), 12, mine ? "#D7FBE8" : secondary(), Typeface.BOLD);
            VoiceControls controls = new VoiceControls(play, seekBar, time, durationMs);
            if (id != null && !id.isEmpty()) {
                voiceControls.put(id, controls);
            }
            play.setOnClickListener(v -> toggleVoicePlayback(id, mediaBase64, controls));
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && playingVoicePlayer != null && id != null && id.equals(playingVoiceId)) {
                        playingVoicePlayer.seekTo(progress);
                        updateVoiceProgress();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
            voiceRow.addView(play);
            voiceRow.addView(seekBar, new LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT));
            voiceRow.addView(time, leftMargin(dp(4), LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            bubble.addView(voiceRow);
        } else {
            bubble.addView(messageText(body, mine));
        }
        if (mine) {
            TextView receipt = text(statusIcon(status), 11, statusColor(status), Typeface.BOLD);
            receipt.setGravity(Gravity.RIGHT);
            if (id != null && !id.isEmpty()) {
                receiptViews.put(id, receipt);
            }
            bubble.addView(receipt, topMargin(dp(3)));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = mine ? Gravity.RIGHT : Gravity.LEFT;
        params.setMargins(dp(18), dp(5), dp(18), dp(5));
        messageList.addView(bubble, params);
        if (scrollBottom) {
            scrollMessagesToBottom();
        }
    }

    private String statusIcon(String status) {
        if (MessageStore.STATUS_READ.equals(status)) {
            return "\u2713\u2713";
        }
        if (MessageStore.STATUS_DELIVERED.equals(status)) {
            return "\u2713\u2713";
        }
        if (MessageStore.STATUS_SENT.equals(status)) {
            return "\u2713";
        }
        return "\u25F7";
    }

    private String statusColor(String status) {
        if (MessageStore.STATUS_READ.equals(status)) {
            return "#60A5FA";
        }
        return "#D7FBE8";
    }

    private void scrollMessagesToBottom() {
        if (messageScroll != null) {
            messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private String formatConversationTime(long when) {
        if (when <= 0L) {
            return "";
        }
        Calendar messageDay = Calendar.getInstance();
        messageDay.setTimeInMillis(when);
        Calendar today = Calendar.getInstance();
        boolean sameDay = messageDay.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                && messageDay.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
        String pattern = sameDay ? "HH:mm" : "dd/MM/yyyy";
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(when));
    }

    @Override
    public void onBluetoothState(String state) {
        showState(state);
    }

    @Override
    public void onDeviceFound(BtChatManager.DeviceCandidate candidate) {
        if (candidate.address == null || candidate.address.isEmpty()) {
            return;
        }
        if ("scanner".equals(currentScreen)) {
            discoveredDevices.put(candidate.address, candidate);
            renderNearbyDeviceList();
            return;
        }
        if (!candidate.paired || !candidate.appAvailable) {
            return;
        }
        discoveredDevices.put(candidate.address, candidate);
        renderContactList();
    }

    @Override
    public void onDiscoveryFinished() {
        showState("Busca finalizada.");
        if ("scanner".equals(currentScreen)) {
            renderNearbyDeviceList();
        } else {
            renderContactList();
        }
    }

    @Override
    public void onIncomingConnection(String remoteName, String remoteAddress) {
        // Conexoes de aparelhos ja pareados acontecem automaticamente para receber mensagens.
    }

    @Override
    public void onRemoteProfile(String remoteAddress, UserProfile profile) {
        String address = remoteAddress == null ? "" : remoteAddress;
        UserProfile updatedProfile = profile == null ? UserProfile.empty() : profile;
        profileStore.saveContact(address, updatedProfile);
        if (address.equals(currentRemoteAddress)) {
            currentRemoteProfile = updatedProfile;
        }
        if ("home".equals(currentScreen)) {
            renderContactList();
        } else if ("chat".equals(currentScreen) && address.equals(currentRemoteAddress)) {
            showChatScreen(currentRemoteProfile, currentFingerprint);
        }
    }

    @Override
    public void onRemoteIdentity(String remoteAddress, String deviceId, String identityPublicKey) {
        profileStore.saveIdentity(remoteAddress, deviceId, identityPublicKey);
    }

    @Override
    public void onConnected(String remoteAddress, UserProfile profile, String fingerprint) {
        currentRemoteAddress = remoteAddress == null ? "" : remoteAddress;
        currentRemoteProfile = profile == null ? UserProfile.empty() : profile;
        currentFingerprint = fingerprint == null ? "" : fingerprint;
        profileStore.saveContact(currentRemoteAddress, currentRemoteProfile);
        profileStore.saveFingerprint(currentRemoteAddress, currentFingerprint);
        showChatScreen(currentRemoteProfile, currentFingerprint);
        flushPendingOutgoing(currentRemoteAddress);
    }

    @Override
    public void onMessageReceived(String remoteAddress, String id, String kind, String body, String mediaBase64, long durationMs, long sentAt) {
        String address = remoteAddress == null ? "" : remoteAddress;
        if (address.isEmpty()) {
            return;
        }
        boolean activeChat = "chat".equals(currentScreen) && address.equals(currentRemoteAddress);
        boolean inserted = messageStore.addMessage(address, id, kind, body, mediaBase64, durationMs, false, sentAt, MessageStore.STATUS_DELIVERED, !activeChat);
        if (activeChat) {
            messageStore.markRead(address);
            btChatManager.sendReceipt(address, id, MessageStore.STATUS_READ);
            if (inserted) {
                addMessageBubble(id, body, false, kind, mediaBase64, durationMs, MessageStore.STATUS_DELIVERED, true);
            }
        } else if ("home".equals(currentScreen)) {
            renderContactList();
        }
    }

    @Override
    public void onReceiptReceived(String remoteAddress, String id, String status) {
        String address = remoteAddress == null || remoteAddress.isEmpty() ? currentRemoteAddress : remoteAddress;
        messageStore.updateStatus(address, id, status);
        if ("chat".equals(currentScreen) && address.equals(currentRemoteAddress)) {
            TextView receipt = receiptViews.get(id);
            if (receipt != null) {
                receipt.setText(statusIcon(status));
                receipt.setTextColor(color(statusColor(status)));
            }
        } else if ("home".equals(currentScreen)) {
            renderContactList();
        }
    }

    @Override
    public void onError(String message) {
        showState(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showState(String message) {
        if (stateText != null) {
            stateText.setText(message);
        }
    }

    private void handleStoredMessageChange(String address, String id, String status) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        if (status != null && !status.trim().isEmpty()) {
            onReceiptReceived(address, id, status);
            return;
        }
        if ("chat".equals(currentScreen) && address.equals(currentRemoteAddress)) {
            messageStore.markRead(address);
            if (id != null && !id.trim().isEmpty()) {
                btChatManager.sendReceipt(address, id, MessageStore.STATUS_READ);
            }
            MessageStore.ChatMessage message = messageStore.findMessage(address, id);
            if (message != null) {
                addMessageBubble(message.id, message.body, message.mine, message.kind, message.mediaBase64, message.durationMs, message.status, true);
            }
        } else if ("home".equals(currentScreen)) {
            renderContactList();
        }
    }

    private void showMainMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        int order = 0;
        if (updateAvailable) {
            menu.getMenu().add(0, 10, order++, "Baixar atualizacao" + (updateVersionName.isEmpty() ? "" : " " + updateVersionName));
        }
        if ("chat".equals(currentScreen) && currentRemoteAddress != null && !currentRemoteAddress.isEmpty()) {
            menu.getMenu().add(0, 11, order++, "Apagar conversa");
        }
        menu.getMenu().add(0, 1, order++, "Editar perfil");
        menu.getMenu().add(0, 2, order++, "Compartilhar app");
        menu.getMenu().add(0, 4, order++, "Configuracoes");
        menu.getMenu().add(0, 3, order, darkMode ? "Tema claro" : "Tema escuro");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 10) {
                showUpdateDialog();
                return true;
            }
            if (item.getItemId() == 11) {
                confirmDeleteConversation(currentRemoteAddress);
                return true;
            }
            if (item.getItemId() == 1) {
                showProfileScreen();
                return true;
            }
            if (item.getItemId() == 2) {
                shareApp();
                return true;
            }
            if (item.getItemId() == 4) {
                showSettingsDialog();
                return true;
            }
            if (item.getItemId() == 3) {
                darkMode = !darkMode;
                themeStore.setDarkMode(darkMode);
                applySystemBars();
                refreshCurrentScreen();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void shareApp() {
        String url = "https://mbzerker.github.io/nBTChat/";
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "nBTChat");
        intent.putExtra(Intent.EXTRA_TEXT, "Baixe o nBTChat para conversar por Bluetooth: " + url);
        startActivity(Intent.createChooser(intent, "Compartilhar nBTChat"));
    }

    private void showSettingsDialog() {
        LinearLayout content = vertical();
        content.setPadding(dp(20), dp(16), dp(20), dp(8));
        content.setBackgroundColor(color(surface()));

        TextView title = text("Configuracoes", 22, primary(), Typeface.BOLD);
        content.addView(title);

        LinearLayout notificationRow = horizontal();
        notificationRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("Notificacoes de mensagens", 16, primary(), Typeface.BOLD);
        notificationRow.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(settingsStore.notificationsEnabled());
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> settingsStore.setNotificationsEnabled(isChecked));
        notificationRow.addView(toggle);
        content.addView(notificationRow, topMargin(dp(18)));

        TextView sound = text("Som: padrao do Android", 13, secondary(), Typeface.NORMAL);
        content.addView(sound, topMargin(dp(4)));

        new AlertDialog.Builder(this)
                .setView(content)
                .setPositiveButton("Fechar", null)
                .show();
    }

    private void confirmDeleteConversation(String address) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        UserProfile profile = profileStore.loadContact(address);
        String name = profile.isComplete() ? profile.getDisplayName() : "esta conversa";
        new AlertDialog.Builder(this)
                .setTitle("Apagar conversa?")
                .setMessage("Todas as mensagens com " + name + " serao removidas deste aparelho.")
                .setPositiveButton("Apagar", (dialog, which) -> {
                    messageStore.deleteConversation(address);
                    if ("chat".equals(currentScreen) && address.equals(currentRemoteAddress)) {
                        renderChatHistory(true);
                    } else if ("home".equals(currentScreen)) {
                        renderContactList();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showMessageActionDialog(String id) {
        MessageStore.ChatMessage message = messageStore.findMessage(currentRemoteAddress, id);
        if (message == null) {
            return;
        }
        List<String> actions = new ArrayList<>();
        if (MessageStore.KIND_TEXT.equals(message.kind) && message.body != null && !message.body.trim().isEmpty()) {
            actions.add("Copiar texto");
        }
        actions.add("Compartilhar no nBTChat");
        actions.add("Cancelar");
        new AlertDialog.Builder(this)
                .setTitle("Mensagem selecionada")
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if ("Copiar texto".equals(action)) {
                        copyMessageText(message.body);
                    } else if ("Compartilhar no nBTChat".equals(action)) {
                        showInternalShareChooser(message);
                    }
                })
                .show();
    }

    private void copyMessageText(String body) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("nBTChat", body == null ? "" : body));
            Toast.makeText(this, "Texto copiado.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showInternalShareChooser(MessageStore.ChatMessage source) {
        Map<String, UserProfile> contacts = profileStore.loadContacts();
        List<String> addresses = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, UserProfile> entry : contacts.entrySet()) {
            if (entry.getKey().equals(currentRemoteAddress)) {
                continue;
            }
            addresses.add(entry.getKey());
            UserProfile profile = entry.getValue();
            names.add(profile.isComplete() ? profile.getDisplayName() : "Contato nBTChat");
        }
        if (addresses.isEmpty()) {
            Toast.makeText(this, "Nenhum outro contato nBTChat para compartilhar.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Compartilhar com")
                .setItems(names.toArray(new String[0]), (dialog, which) -> shareMessageToContact(addresses.get(which), source))
                .show();
    }

    private void shareMessageToContact(String address, MessageStore.ChatMessage source) {
        long sentAt = System.currentTimeMillis();
        String id = messageStore.createId();
        String body = source.body == null ? "" : source.body;
        String media = source.mediaBase64 == null ? "" : source.mediaBase64;
        messageStore.addMessage(address, id, source.kind, body, media, source.durationMs, true, sentAt, MessageStore.STATUS_PENDING, false);
        sendOrQueueOutgoing(address, id, source.kind, body, media, source.durationMs, sentAt);
        Toast.makeText(this, "Mensagem compartilhada no nBTChat.", Toast.LENGTH_SHORT).show();
        if ("home".equals(currentScreen)) {
            renderContactList();
        }
    }

    private void showUpdateDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Atualizacao disponivel")
                .setMessage(updateVersionName.isEmpty() ? "Uma nova versao do nBTChat esta pronta para baixar." : "Versao " + updateVersionName + " disponivel.")
                .setPositiveButton("Baixar", (dialog, which) -> openExternalLink(Uri.parse(updatePageUrl)))
                .setNegativeButton("Agora nao", null)
                .show();
    }

    private void checkForUpdates(boolean showIfCurrent) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(UPDATE_MANIFEST_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                    JSONObject json = new JSONObject(builder.toString());
                    int latestCode = json.optInt("versionCode", 0);
                    String latestName = json.optString("versionName", "");
                    String pageUrl = json.optString("pageUrl", updatePageUrl);
                    String apkUrl = json.optString("apkUrl", updateApkUrl);
                    int currentCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                    runOnUiThread(() -> {
                        updateAvailable = latestCode > currentCode;
                        updateVersionName = latestName;
                        updatePageUrl = pageUrl;
                        updateApkUrl = apkUrl;
                        if (updateAvailable && ("home".equals(currentScreen) || "chat".equals(currentScreen) || "scanner".equals(currentScreen))) {
                            refreshCurrentScreen();
                        } else if (showIfCurrent && !updateAvailable) {
                            Toast.makeText(this, "O nBTChat ja esta atualizado.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception ignored) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "nBTChat-update").start();
    }

    private void refreshCurrentScreen() {
        if ("chat".equals(currentScreen)) {
            showChatScreen(currentRemoteProfile, currentFingerprint);
        } else if ("scanner".equals(currentScreen)) {
            showNearbyScannerScreen(false);
        } else if ("profile".equals(currentScreen)) {
            showProfileScreen();
        } else {
            showInitialScreen();
        }
    }

    private void pickPhoto() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_PHOTO);
        } catch (Exception ex) {
            Toast.makeText(this, "Nao foi possivel abrir a galeria.", Toast.LENGTH_LONG).show();
        }
    }

    private void capturePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra("android.intent.extras.CAMERA_FACING", 1);
        intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
        try {
            pendingCameraUri = createCameraImageUri();
            if (pendingCameraUri != null) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            startActivityForResult(intent, REQUEST_CAPTURE_PHOTO);
        } catch (Exception ex) {
            pendingCameraUri = null;
            Toast.makeText(this, "Nenhum app de camera encontrado.", Toast.LENGTH_LONG).show();
        }
    }

    private Uri createCameraImageUri() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "nbtchat_profile_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/nBTChat");
            }
            return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void updateEditingPhoto(String photoBase64) {
        if (photoBase64 == null || photoBase64.isEmpty()) {
            return;
        }
        editingPhotoBase64 = photoBase64;
        if (profilePreview != null) {
            Bitmap bitmap = decodePhoto(editingPhotoBase64);
            if (bitmap != null) {
                profilePreview.setImageBitmap(bitmap);
            }
        }
    }

    private String compressImage(Uri uri) {
        try {
            Bitmap original = decodeBitmapWithOrientation(uri);
            if (original == null) {
                return "";
            }
            return compressBitmap(original);
        } catch (Exception ex) {
            Toast.makeText(this, "Nao foi possivel carregar a foto.", Toast.LENGTH_LONG).show();
            return "";
        }
    }

    private Bitmap decodeBitmapWithOrientation(Uri uri) {
        int orientation = readImageOrientation(uri);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(inputStream, null, bounds);
        } catch (Exception ignored) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 1200);
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            Bitmap decoded = BitmapFactory.decodeStream(inputStream, null, options);
            return rotateBitmap(decoded, orientation);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int readImageOrientation(Uri uri) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            ExifInterface exif = new ExifInterface(inputStream);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (Exception ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private int sampleSize(int width, int height, int targetMax) {
        int sample = 1;
        while ((width / sample) > targetMax || (height / sample) > targetMax) {
            sample *= 2;
        }
        return Math.max(1, sample);
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
        if (bitmap == null) {
            return null;
        }
        int degrees;
        if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
            degrees = 90;
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
            degrees = 180;
        } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
            degrees = 270;
        } else {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private String compressBitmap(Bitmap original) {
        int max = Math.max(original.getWidth(), original.getHeight());
        float scale = max > 256 ? 256f / max : 1f;
        Bitmap scaled = Bitmap.createScaledBitmap(
                original,
                Math.max(1, Math.round(original.getWidth() * scale)),
                Math.max(1, Math.round(original.getHeight() * scale)),
                true
        );
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 84, outputStream);
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
    }

    private Bitmap decodePhoto(String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyAvatar(ImageView imageView, UserProfile profile) {
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bitmap = profile == null ? null : decodePhoto(profile.getPhotoBase64());
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            return;
        }
        String gender = profile == null ? UserProfile.GENDER_OTHER : profile.getGender();
        if (UserProfile.GENDER_MALE.equals(gender)) {
            imageView.setImageResource(R.drawable.avatar_male);
        } else if (UserProfile.GENDER_FEMALE.equals(gender)) {
            imageView.setImageResource(R.drawable.avatar_female);
        } else {
            imageView.setImageResource(R.drawable.avatar_neutral);
        }
    }

    private int positionForGender(String gender) {
        if (UserProfile.GENDER_MALE.equals(gender)) {
            return 0;
        }
        if (UserProfile.GENDER_FEMALE.equals(gender)) {
            return 1;
        }
        return 2;
    }

    private String genderForPosition(int position) {
        if (position == 0) {
            return UserProfile.GENDER_MALE;
        }
        if (position == 1) {
            return UserProfile.GENDER_FEMALE;
        }
        return UserProfile.GENDER_OTHER;
    }

    private void hideKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private static final class PendingOutgoing {
        final String address;
        final String id;
        final String kind;
        final String body;
        final String mediaBase64;
        final long durationMs;
        final long sentAt;

        PendingOutgoing(String address, String id, String kind, String body, String mediaBase64, long durationMs, long sentAt) {
            this.address = address;
            this.id = id;
            this.kind = kind;
            this.body = body;
            this.mediaBase64 = mediaBase64;
            this.durationMs = durationMs;
            this.sentAt = sentAt;
        }
    }

    private static final class VoiceControls {
        final ImageButton button;
        final SeekBar seekBar;
        final TextView time;
        final long durationMs;

        VoiceControls(ImageButton button, SeekBar seekBar, TextView time, long durationMs) {
            this.button = button;
            this.seekBar = seekBar;
            this.time = time;
            this.durationMs = durationMs;
        }
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(color(darkMode ? "#071015" : "#0F766E"));
        getWindow().setNavigationBarColor(color(background()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = darkMode ? 0 : View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
        }
    }

    private void applyRootInsets(LinearLayout root, int left, int top, int right, int bottom) {
        root.setPadding(left, top + systemBarHeight("status_bar"), right, bottom + systemBarHeight("navigation_bar"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                int topInset = insets.getSystemWindowInsetTop();
                int bottomInset = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Insets status = insets.getInsets(WindowInsets.Type.statusBars());
                    android.graphics.Insets nav = insets.getInsets(WindowInsets.Type.navigationBars());
                    android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                    topInset = status.top;
                    bottomInset = Math.max(nav.bottom, ime.bottom);
                }
                view.setPadding(left, top + topInset, right, bottom + bottomInset);
                return insets;
            });
        }
    }

    private void requestInsets(View root) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            root.requestApplyInsets();
        }
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private TextView text(String value, int sp, String color, int style) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color(color));
        textView.setTypeface(Typeface.DEFAULT, style);
        textView.setIncludeFontPadding(true);
        return textView;
    }

    private TextView label(String value) {
        TextView label = text(value.toUpperCase(Locale.ROOT), 12, secondary(), Typeface.BOLD);
        LinearLayout.LayoutParams params = topMargin(dp(16));
        label.setLayoutParams(params);
        return label;
    }

    private ArrayAdapter<String> createGenderAdapter() {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, GENDER_LABELS) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return spinnerText(position, false);
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                return spinnerText(position, true);
            }
        };
    }

    private TextView spinnerText(int position, boolean dropdown) {
        TextView textView = text(GENDER_LABELS[position], 16, primary(), Typeface.NORMAL);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setPadding(dp(12), dropdown ? dp(12) : dp(8), dp(12), dropdown ? dp(12) : dp(8));
        textView.setBackgroundColor(color(dropdown ? surface() : surface()));
        return textView;
    }

    private EditText input(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setTextColor(color(primary()));
        editText.setHintTextColor(color(secondary()));
        editText.setTextSize(16);
        editText.setSingleLine(true);
        editText.setPadding(dp(14), dp(10), dp(14), dp(10));
        editText.setBackground(rounded(surface(), dp(16), border()));
        return editText;
    }

    private EditText inlineInput(String hint) {
        EditText editText = input(hint);
        editText.setBackgroundColor(Color.TRANSPARENT);
        editText.setPadding(dp(12), dp(8), dp(8), dp(8));
        return editText;
    }

    private Button pillButton(String value, String background, String foreground) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(color(foreground));
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        button.setBackground(rounded(background, dp(18), background));
        return button;
    }

    private ImageButton iconButton(int drawableId, String description, int size, View.OnClickListener listener) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawableId);
        button.setColorFilter(color(primary()));
        button.setContentDescription(description);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        button.setBackground(rounded(surface(), dp(22), border()));
        button.setOnClickListener(listener);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return button;
    }

    private ImageButton menuButton() {
        ImageButton button = iconButton(updateAvailable ? R.drawable.ic_update_24 : R.drawable.ic_menu_24, "Menu", dp(42), v -> showMainMenu(v));
        if (updateAvailable) {
            button.setColorFilter(Color.WHITE);
            button.setBackground(rounded("#16A34A", dp(22), "#16A34A"));
        }
        return button;
    }

    @SuppressLint("ClickableViewAccessibility")
    private ImageButton voiceRecordButton() {
        ImageButton button = iconButton(R.drawable.ic_mic_24, "Mensagem de voz", dp(46), null);
        button.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (startVoiceRecording()) {
                    button.animate().scaleX(1.22f).scaleY(1.22f).setDuration(90).start();
                    button.setColorFilter(Color.WHITE);
                    button.setBackground(rounded("#16A34A", dp(28), "#86EFAC"));
                }
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                if (recordingVoice) {
                    stopVoiceRecording();
                }
                button.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
                button.setColorFilter(color(primary()));
                button.setBackground(rounded(surface(), dp(22), border()));
                return true;
            }
            return true;
        });
        return button;
    }

    private GradientDrawable rounded(String fill, int radius, String stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(fill));
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), color(stroke));
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams topMargin(int top) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, top, 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams leftMargin(int left, int width, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(left, 0, 0, 0);
        return params;
    }

    private int systemBarHeight(String name) {
        int id = getResources().getIdentifier(name, "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int color(String value) {
        return Color.parseColor(value);
    }

    private String background() {
        return darkMode ? "#101820" : "#F7F8F5";
    }

    private String chatBackground() {
        return darkMode ? "#0D1418" : "#EFF6F3";
    }

    private String surface() {
        return darkMode ? "#18232C" : "#FFFFFF";
    }

    private String surfaceAlt() {
        return darkMode ? "#24313B" : "#EEF2F0";
    }

    private String primary() {
        return darkMode ? "#F7F8F5" : "#17212B";
    }

    private String secondary() {
        return darkMode ? "#A7B0BA" : "#52606D";
    }

    private String border() {
        return darkMode ? "#2F3B45" : "#D7DDD8";
    }

    private String accent() {
        return "#F97316";
    }
}
