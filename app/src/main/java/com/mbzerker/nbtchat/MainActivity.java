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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputContentInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.QRCodeWriter;

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
import java.nio.ByteBuffer;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
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
    private static final int REQUEST_QR_CAMERA = 106;
    private static final int REQUEST_SCAN_QR = 107;
    private static final int REQUEST_PICK_NOTIFICATION_SOUND = 108;
    private static final int REQUEST_PICK_NOTIFICATION_SOUND_FILE = 109;
    private static final int MAX_GIF_BYTES = 640 * 1024;
    private static final int NAME_LIMIT = 12;
    private static final int LONG_MESSAGE_LIMIT = 360;

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
    private static final String UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/MBZerker/nBTChat/main/docs/update.json";
    private static final String DOWNLOAD_PAGE_URL = "https://mbzerker.github.io/nBTChat/";
    private static final Pattern LINK_PATTERN = Pattern.compile("(?i)\\b((?:https?://|www\\.)[^\\s<>()]+)");

    private final Map<String, BtChatManager.DeviceCandidate> discoveredDevices = new LinkedHashMap<>();
    private final Map<String, TextView> receiptViews = new LinkedHashMap<>();
    private final Map<String, VoiceControls> voiceControls = new LinkedHashMap<>();
    private final Set<String> renderedMessageIds = new HashSet<>();
    private final List<PendingOutgoing> pendingOutgoing = new ArrayList<>();
    private final List<PendingDelete> pendingDeletes = new ArrayList<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private ProfileStore profileStore;
    private IdentityStore identityStore;
    private MessageStore messageStore;
    private GadgetStore gadgetStore;
    private ThemeStore themeStore;
    private AppSettingsStore settingsStore;
    private BtChatManager btChatManager;
    private TextView stateText;
    private EditText conversationSearchInput;
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
    private String conversationFilter = "";
    private String pendingReplyAddress = "";
    private String pendingReplyId = "";
    private String pendingReplyPreview = "";
    private String currentTable100Text = "";
    private String table100ReturnScreen = "";
    private String pendingSharedKind = "";
    private String pendingSharedBody = "";
    private String pendingSharedMediaBase64 = "";
    private QrInvite.Invite pendingQrInvite;
    private long pendingQrStartedAt;
    private String pendingOpenChatAddress = "";
    private long openNextQrConnectionUntil;
    private View replyPreviewBar;
    private FrameLayout chatAvatarFrame;
    private TextView chatTitleText;
    private TextView chatSubtitleText;
    private ImageView chatConnectionIcon;
    private boolean messageReceiverRegistered;
    private boolean updateAvailable;
    private String updateVersionName = "";
    private String updatePageUrl = DOWNLOAD_PAGE_URL;
    private String updateApkUrl = "https://raw.githubusercontent.com/MBZerker/nBTChat/main/docs/nBTChat.apk";
    private MediaPlayer playingVoicePlayer;
    private File playingVoiceFile;
    private String playingVoiceId = "";
    private VoiceControls playingVoiceControls;
    private Runnable voiceTicker;
    private long lastDiscoverableRequestAt;
    private final Set<String> onlineAddresses = new HashSet<>();
    private final Map<String, String> contactPresence = new HashMap<>();
    private final Map<String, Long> remoteTypingUntil = new HashMap<>();
    private boolean localTypingSent;
    private long lastTypingSentAt;
    private Runnable typingStopRunnable;

    private final BroadcastReceiver messageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (GadgetStore.ACTION_GADGETS_CHANGED.equals(intent.getAction())) {
                String tableId = intent.getStringExtra(GadgetStore.EXTRA_TABLE_ID);
                if ("table100_play".equals(currentScreen) && tableId != null && currentTable100Text.contains(tableId)) {
                    refreshTable100PlayScreen();
                }
                return;
            }
            if (!MessageStore.ACTION_MESSAGES_CHANGED.equals(intent.getAction())) {
                return;
            }
            String address = intent.getStringExtra(MessageStore.EXTRA_ADDRESS);
            String id = intent.getStringExtra(MessageStore.EXTRA_MESSAGE_ID);
            String status = intent.getStringExtra(MessageStore.EXTRA_STATUS);
            boolean deleted = intent.getBooleanExtra(MessageStore.EXTRA_DELETED, false);
            handleStoredMessageChange(address, id, status, deleted);
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
        identityStore = new IdentityStore(this);
        messageStore = new MessageStore(this);
        gadgetStore = new GadgetStore(this);
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
        handleSharedImageIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sendLocalTyping(false);
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
        handleSharedImageIntent(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            tryStartBluetooth();
        } else if (requestCode == REQUEST_QR_CAMERA
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openQrScanner();
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
                sendMediaFromUri(uri);
            }
        } else if (requestCode == REQUEST_CAPTURE_CHAT_IMAGE && resultCode == RESULT_OK) {
            if (pendingCameraUri != null) {
                sendImageMessage(compressImage(pendingCameraUri));
                pendingCameraUri = null;
            }
        } else if (requestCode == REQUEST_CAPTURE_CHAT_IMAGE) {
            pendingCameraUri = null;
        } else if (requestCode == REQUEST_SCAN_QR && resultCode == RESULT_OK && data != null) {
            handleQrInvite(data.getStringExtra(QrScannerActivity.EXTRA_QR_TEXT));
        } else if ((requestCode == REQUEST_PICK_NOTIFICATION_SOUND || requestCode == REQUEST_PICK_NOTIFICATION_SOUND_FILE)
                && resultCode == RESULT_OK && data != null) {
            Uri uri = null;
            if (requestCode == REQUEST_PICK_NOTIFICATION_SOUND) {
                uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            }
            if (uri == null) {
                uri = data.getData();
            }
            if (uri != null) {
                try {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                settingsStore.setNotificationSound(uri.toString(), displayNameForSoundUri(uri));
                NotificationHelper.ensureChannels(this);
                if ("settings".equals(currentScreen)) {
                    showSettingsScreen();
                }
            }
        }
    }

    private boolean requestMissingPermissions() {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfMissing(missing, Manifest.permission.BLUETOOTH_SCAN);
            addIfMissing(missing, Manifest.permission.BLUETOOTH_CONNECT);
            addIfMissing(missing, Manifest.permission.BLUETOOTH_ADVERTISE);
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
        filter.addAction(GadgetStore.ACTION_GADGETS_CHANGED);
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

    private void handleSharedImageIntent(Intent intent) {
        if (intent == null || !profileStore.hasLocalProfile() || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }
        String type = intent.getType();
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("image/")) {
            return;
        }
        Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri == null) {
            return;
        }
        preparePendingSharedMedia(uri);
        intent.setAction("");
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
        addTopActions(top);
        root.addView(top);

        stateText = null;

        LinearLayout searchShell = horizontal();
        searchShell.setGravity(Gravity.CENTER_VERTICAL);
        searchShell.setPadding(dp(2), 0, dp(8), 0);
        searchShell.setBackground(rounded(surface(), dp(18), border()));
        conversationSearchInput = inlineInput("Pesquisar conversas");
        conversationSearchInput.setSingleLine(true);
        conversationSearchInput.setText(conversationFilter);
        conversationSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                conversationFilter = s == null ? "" : s.toString();
                renderContactList();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        searchShell.addView(conversationSearchInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageResource(R.drawable.ic_search_24);
        searchIcon.setColorFilter(color(secondary()));
        searchShell.addView(searchIcon, new LinearLayout.LayoutParams(dp(28), dp(28)));
        root.addView(searchShell, topMargin(dp(14)));

        FrameLayout listFrame = new FrameLayout(this);
        ScrollView listScroll = new ScrollView(this);
        contactList = vertical();
        contactList.setPadding(0, dp(10), 0, dp(98));
        listScroll.addView(contactList);
        listFrame.addView(listScroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        ImageButton scanButton = iconButton(R.drawable.ic_invite_24, "Encontrar aparelhos proximos", dp(76), v -> showNearbyScannerScreen(true));
        scanButton.setColorFilter(Color.WHITE);
        scanButton.setPadding(dp(15), dp(15), dp(15), dp(15));
        scanButton.setScaleType(ImageView.ScaleType.FIT_CENTER);
        scanButton.setBackground(rounded("#16A34A", dp(38), "#16A34A"));
        FrameLayout.LayoutParams scanParams = new FrameLayout.LayoutParams(dp(76), dp(76), Gravity.RIGHT | Gravity.BOTTOM);
        scanParams.setMargins(0, 0, dp(4), dp(18));
        listFrame.addView(scanButton, scanParams);

        root.addView(listFrame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        attachTopLevelSwipe(listFrame);
        attachTopLevelSwipe(listScroll);
        root.addView(bottomNavBar(), topMargin(dp(8)));
        renderContactList();

        attachTopLevelSwipe(root);
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

    private View bottomNavBar() {
        LinearLayout bar = horizontal();
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(12), dp(8), dp(12), dp(8));
        bar.setBackground(rounded(surface(), dp(22), border()));
        bar.addView(navButton(R.drawable.ic_home_24, "Conversas", "home".equals(currentScreen), v -> showHomeScreen()), new LinearLayout.LayoutParams(0, dp(62), 1));
        bar.addView(navButton(R.drawable.ic_social_updates_24, "Atualizacoes", "updates".equals(currentScreen), v -> showUpdatesScreen()), new LinearLayout.LayoutParams(0, dp(62), 1));
        bar.addView(navButton(R.drawable.ic_tent_24, "Loja", "store".equals(currentScreen), v -> showStoreScreen()), new LinearLayout.LayoutParams(0, dp(62), 1));
        return bar;
    }

    private void showUpdatesScreen() {
        currentScreen = "updates";
        messageList = null;

        LinearLayout root = vertical();
        root.setBackgroundColor(color(background()));
        applyRootInsets(root, dp(16), dp(10), dp(16), dp(12));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Atualizacoes", 27, primary(), Typeface.BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        addTopActions(top);
        root.addView(top);

        LinearLayout content = vertical();
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(18), dp(22), dp(18), dp(22));
        content.setBackground(rounded(surface(), dp(14), border()));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_social_updates_24);
        icon.setColorFilter(color("#16A34A"));
        icon.setPadding(dp(12), dp(12), dp(12), dp(12));
        icon.setBackground(rounded(darkMode ? "#18372C" : "#DDF7E8", dp(32), "#16A34A"));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        content.addView(icon, iconParams);
        TextView heading = text("Linha do tempo Bluetooth", 20, primary(), Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        content.addView(heading, topMargin(dp(14)));
        TextView body = text("Aqui vao ficar as postagens dos contatos pareados quando esse modulo for ativado.", 14, secondary(), Typeface.NORMAL);
        body.setGravity(Gravity.CENTER);
        body.setLineSpacing(dp(2), 1f);
        content.addView(body, topMargin(dp(8)));
        attachTopLevelSwipe(content);
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
        root.addView(bottomNavBar(), topMargin(dp(8)));

        attachTopLevelSwipe(root);
        setContentView(root);
        requestInsets(root);
    }

    private ImageButton navButton(int drawableId, String description, boolean active, View.OnClickListener listener) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawableId);
        button.setContentDescription(description);
        button.setPadding(dp(15), dp(12), dp(15), dp(12));
        button.setColorFilter(color(active ? "#FFFFFF" : primary()));
        button.setBackground(rounded(active ? "#16A34A" : surfaceAlt(), dp(20), active ? "#16A34A" : border()));
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setOnClickListener(listener);
        return button;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachTopLevelSwipe(View view) {
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX[0] = event.getRawX();
                downY[0] = event.getRawY();
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dx = event.getRawX() - downX[0];
                float dy = Math.abs(event.getRawY() - downY[0]);
                if (Math.abs(dx) > dp(110) && dy < dp(70)) {
                    navigateTopLevel(dx < 0);
                    return true;
                }
            }
            return false;
        });
    }

    private void navigateTopLevel(boolean forward) {
        if ("home".equals(currentScreen)) {
            if (forward) {
                showUpdatesScreen();
            }
            return;
        }
        if ("updates".equals(currentScreen)) {
            if (forward) {
                showStoreScreen();
            } else {
                showHomeScreen();
            }
            return;
        }
        if ("store".equals(currentScreen) && !forward) {
            showUpdatesScreen();
        }
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

        int rendered = 0;
        for (String address : orderedAddresses) {
            BtChatManager.DeviceCandidate candidate = candidates.get(address);
            if (!conversationMatches(address, candidate)) {
                continue;
            }
            contactList.addView(contactRow(address, candidate), topMargin(dp(8)));
            rendered++;
        }
        if (rendered == 0) {
            TextView empty = text("Nenhuma conversa encontrada.", 15, secondary(), Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            contactList.addView(empty, topMargin(dp(36)));
        }
    }

    private boolean conversationMatches(String address, BtChatManager.DeviceCandidate candidate) {
        String query = searchable(conversationFilter);
        if (query.isEmpty()) {
            return true;
        }
        UserProfile known = profileStore.loadContact(address);
        MessageStore.ConversationInfo conversation = messageStore.getConversationInfo(address);
        String name = known.isComplete() ? known.getDisplayName() : (candidate == null ? "Contato nBTChat" : candidate.name);
        String haystack = searchable(name + " " + known.getStatus() + " " + conversation.lastBody + " " + address);
        return haystack.contains(query);
    }

    private String searchable(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('ç', 'c')
                .replace('Ç', 'c')
                .toLowerCase(Locale.ROOT);
        return normalized.trim();
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
        addTopActions(top);
        root.addView(top);

        stateText = text("Procurando aparelhos com Bluetooth visivel.", 14, secondary(), Typeface.NORMAL);
        root.addView(stateText, topMargin(dp(8)));

        Button scanButton = pillButton("Escanear aparelhos proximos", accent(), darkMode ? "#12171D" : "#17212B");
        scanButton.setOnClickListener(v -> {
            requestDiscoverableForScanner();
            discoveredDevices.clear();
            renderNearbyDeviceList();
            btChatManager.startNearbyDiscovery();
        });
        root.addView(scanButton, topMargin(dp(16)));
        root.addView(qrActionRow(), topMargin(dp(10)));

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
        root.postDelayed(this::requestDiscoverableForScanner, 250);

        if (autoStart) {
            discoveredDevices.clear();
            renderNearbyDeviceList();
            root.postDelayed(() -> btChatManager.startNearbyDiscovery(), 200);
        } else {
            renderNearbyDeviceList();
        }
    }

    private View qrActionRow() {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER);
        Button mine = pillButton("Meu QR", surfaceAlt(), primary());
        mine.setOnClickListener(v -> showMyQrDialog());
        row.addView(mine, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button scan = pillButton("Ler QR", "#16A34A", "#FFFFFF");
        scan.setOnClickListener(v -> openQrScanner());
        LinearLayout.LayoutParams scanParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        scanParams.setMargins(dp(8), 0, 0, 0);
        row.addView(scan, scanParams);

        Button key = pillButton("Chave", surfaceAlt(), primary());
        key.setOnClickListener(v -> showPasteInviteDialog());
        LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        keyParams.setMargins(dp(8), 0, 0, 0);
        row.addView(key, keyParams);
        return row;
    }

    private void showQrActionsDialog() {
        LinearLayout content = vertical();
        content.setPadding(dp(20), dp(18), dp(20), dp(8));
        content.setBackgroundColor(color(surface()));

        TextView title = text("Conectar por QR", 22, primary(), Typeface.BOLD);
        content.addView(title);
        TextView subtitle = text("Use o QR para adicionar um nBTChat sem depender da lista de descoberta Bluetooth.", 14, secondary(), Typeface.NORMAL);
        subtitle.setLineSpacing(dp(2), 1f);
        content.addView(subtitle, topMargin(dp(6)));

        Button mine = pillButton("Mostrar meu QR", surfaceAlt(), primary());
        mine.setOnClickListener(v -> showMyQrDialog());
        content.addView(mine, topMargin(dp(16)));

        Button scan = pillButton("Ler QR de outra pessoa", "#16A34A", "#FFFFFF");
        scan.setOnClickListener(v -> openQrScanner());
        content.addView(scan, topMargin(dp(10)));

        Button paste = pillButton("Digitar chave", surfaceAlt(), primary());
        paste.setOnClickListener(v -> showPasteInviteDialog());
        content.addView(paste, topMargin(dp(10)));

        new AlertDialog.Builder(this)
                .setView(content)
                .setPositiveButton("Fechar", null)
                .show();
    }

    private String localInvitePayload() {
        return QrInvite.create(
                btChatManager.localBluetoothAddress(),
                btChatManager.localBluetoothName(),
                identityStore.getDeviceId(),
                identityStore.getPublicKeyBase64(),
                profileStore.loadLocalProfile()
        );
    }

    private void showMyQrDialog() {
        String payload = localInvitePayload();
        if (payload.isEmpty()) {
            Toast.makeText(this, "Nao foi possivel criar o QR.", Toast.LENGTH_LONG).show();
            return;
        }
        requestDiscoverableForQr();

        LinearLayout content = vertical();
        content.setPadding(dp(20), dp(18), dp(20), dp(12));
        content.setBackgroundColor(color(surface()));

        TextView title = text("Meu QR nBTChat", 22, primary(), Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        content.addView(title);

        ImageView qr = new ImageView(this);
        Bitmap qrBitmap = createQrBitmap(payload, dp(248));
        if (qrBitmap == null) {
            Toast.makeText(this, "Nao foi possivel gerar o QR. Use a chave de convite.", Toast.LENGTH_LONG).show();
            return;
        }
        qr.setImageBitmap(qrBitmap);
        qr.setPadding(dp(10), dp(10), dp(10), dp(10));
        qr.setBackground(rounded("#FFFFFF", dp(14), border()));
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(268), dp(268));
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.setMargins(0, dp(16), 0, 0);
        content.addView(qr, qrParams);

        String bluetoothName = btChatManager.localBluetoothName();
        String address = btChatManager.localBluetoothAddress();
        String detail = address.isEmpty()
                ? "O Android ocultou o endereco Bluetooth. Ao ler o QR, o outro aparelho vai procurar este nome no Bluetooth: " + (bluetoothName.isEmpty() ? "sem nome" : bluetoothName) + "."
                : "Endereco Bluetooth incluido para conexao direta.";
        TextView hint = text(detail, 13, secondary(), Typeface.NORMAL);
        hint.setGravity(Gravity.CENTER);
        hint.setLineSpacing(dp(2), 1f);
        content.addView(hint, topMargin(dp(10)));

        TextView key = text(payload, 10, secondary(), Typeface.NORMAL);
        key.setTextIsSelectable(true);
        key.setSingleLine(false);
        key.setPadding(dp(10), dp(8), dp(10), dp(8));
        key.setBackground(rounded(surfaceAlt(), dp(10), border()));
        content.addView(key, topMargin(dp(10)));

        new AlertDialog.Builder(this)
                .setView(content)
                .setPositiveButton("Copiar chave", (dialog, which) -> copyMessageText(payload))
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showDownloadQrDialog() {
        LinearLayout content = vertical();
        content.setPadding(dp(20), dp(18), dp(20), dp(12));
        content.setBackgroundColor(color(surface()));

        TextView title = text("Baixar nBTChat", 22, primary(), Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        content.addView(title);

        TextView subtitle = text("Aponte a camera de outro Android para abrir a pagina de download do app.", 14, secondary(), Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(dp(2), 1f);
        content.addView(subtitle, topMargin(dp(8)));

        ImageView qr = new ImageView(this);
        Bitmap qrBitmap = createQrBitmap(DOWNLOAD_PAGE_URL, dp(248));
        if (qrBitmap == null) {
            Toast.makeText(this, "Nao foi possivel gerar o QR de download.", Toast.LENGTH_LONG).show();
            return;
        }
        qr.setImageBitmap(qrBitmap);
        qr.setPadding(dp(10), dp(10), dp(10), dp(10));
        qr.setBackground(rounded("#FFFFFF", dp(14), border()));
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(268), dp(268));
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.setMargins(0, dp(16), 0, 0);
        content.addView(qr, qrParams);

        TextView link = text(DOWNLOAD_PAGE_URL, 13, secondary(), Typeface.BOLD);
        link.setGravity(Gravity.CENTER);
        link.setTextIsSelectable(true);
        content.addView(link, topMargin(dp(10)));

        new AlertDialog.Builder(this)
                .setView(content)
                .setPositiveButton("Copiar link", (dialog, which) -> copyMessageText(DOWNLOAD_PAGE_URL))
                .setNeutralButton("Compartilhar", (dialog, which) -> shareApp())
                .setNegativeButton("Fechar", null)
                .show();
    }

    private Bitmap createQrBitmap(String payload, int size) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints);
            int[] pixels = new int[size * size];
            for (int y = 0; y < size; y++) {
                int offset = y * size;
                for (int x = 0; x < size; x++) {
                    pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
            return bitmap;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void openQrScanner() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_QR_CAMERA);
            return;
        }
        try {
            startActivityForResult(new Intent(this, QrScannerActivity.class), REQUEST_SCAN_QR);
        } catch (Exception ex) {
            Toast.makeText(this, "Nao foi possivel abrir o leitor de QR.", Toast.LENGTH_LONG).show();
        }
    }

    private void showPasteInviteDialog() {
        EditText input = input("Cole a chave nBTChat");
        input.setSingleLine(false);
        input.setMinLines(4);
        input.setMaxLines(8);
        input.setGravity(Gravity.TOP);
        new AlertDialog.Builder(this)
                .setTitle("Digitar chave")
                .setView(input)
                .setPositiveButton("Conectar", (dialog, which) -> handleQrInvite(input.getText().toString()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void handleQrInvite(String raw) {
        QrInvite.Invite invite = QrInvite.parse(raw);
        if (invite == null) {
            Toast.makeText(this, "QR ou chave nBTChat invalida.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!invite.deviceId.isEmpty() && invite.deviceId.equals(identityStore.getDeviceId())) {
            Toast.makeText(this, "Este QR e do seu proprio aparelho.", Toast.LENGTH_LONG).show();
            return;
        }
        BtChatManager.DeviceCandidate candidate = btChatManager.getDirectCandidate(invite.address, invite.bluetoothName);
        String address = candidate == null ? invite.address : candidate.address;
        if (!QrInvite.validBluetoothAddress(address)) {
            pendingQrInvite = invite;
            pendingQrStartedAt = System.currentTimeMillis();
            Toast.makeText(this, "Vou procurar este aparelho pelo Bluetooth. Deixe o outro nBTChat visivel.", Toast.LENGTH_LONG).show();
            requestDiscoverableForQr();
            btChatManager.startNearbyDiscovery();
            uiHandler.postDelayed(() -> {
                if (pendingQrInvite == invite) {
                    pendingQrInvite = null;
                    Toast.makeText(this, "Nao encontrei o aparelho do QR. Abra Meu QR no outro celular e aceite ficar visivel.", Toast.LENGTH_LONG).show();
                }
            }, 35_000L);
            return;
        }
        if (candidate != null) {
            pendingOpenChatAddress = address;
            openNextQrConnectionUntil = System.currentTimeMillis() + 45_000L;
            Toast.makeText(this, "Conectando pelo QR...", Toast.LENGTH_SHORT).show();
            btChatManager.connectDirect(candidate);
        } else {
            Toast.makeText(this, "Nao consegui conectar diretamente. Pareie no Android ou deixe o aparelho visivel.", Toast.LENGTH_LONG).show();
        }
    }

    private boolean matchesPendingQr(BtChatManager.DeviceCandidate candidate) {
        if (pendingQrInvite == null || candidate == null) {
            return false;
        }
        if (System.currentTimeMillis() - pendingQrStartedAt > 40_000L) {
            pendingQrInvite = null;
            return false;
        }
        String expectedName = searchable(pendingQrInvite.bluetoothName);
        String candidateName = searchable(candidate.name);
        return !expectedName.isEmpty() && expectedName.equals(candidateName);
    }

    private void connectPendingQrCandidate(BtChatManager.DeviceCandidate candidate) {
        if (pendingQrInvite == null || candidate == null || !QrInvite.validBluetoothAddress(candidate.address)) {
            return;
        }
        pendingQrInvite = null;
        pendingOpenChatAddress = candidate.address;
        openNextQrConnectionUntil = System.currentTimeMillis() + 45_000L;
        Toast.makeText(this, "Aparelho encontrado. Conectando pelo QR...", Toast.LENGTH_SHORT).show();
        btChatManager.connectDirect(candidate);
    }

    private void requestDiscoverableForScanner() {
        if (!"scanner".equals(currentScreen) || isFinishing()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDiscoverableRequestAt < 110_000L) {
            return;
        }
        lastDiscoverableRequestAt = now;
        try {
            Intent discoverable = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverable.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
            startActivity(discoverable);
            showState("Seu aparelho ficara visivel por ate 2 minutos.");
        } catch (Exception ex) {
            showState("Nao foi possivel pedir visibilidade Bluetooth.");
        }
    }

    private void requestDiscoverableForQr() {
        openNextQrConnectionUntil = System.currentTimeMillis() + 120_000L;
        if (isFinishing()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDiscoverableRequestAt < 110_000L) {
            return;
        }
        lastDiscoverableRequestAt = now;
        try {
            Intent discoverable = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverable.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
            startActivity(discoverable);
        } catch (Exception ignored) {
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
        TextView titleView = text(safeName(title, "Aparelho nBTChat"), 16, primary(), Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(titleView);
        TextView subtitleView = text(subtitle, 13, secondary(), Typeface.NORMAL);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(subtitleView);
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
            showContactActionPopup(v, address);
            return true;
        });

        row.addView(avatarStatusFrame(known, contactPresenceStatus(address), dp(58), dp(18), dp(4), false, null),
                new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout info = vertical();
        String title = known.isComplete() ? known.getDisplayName() : (candidate == null ? "Contato nBTChat" : candidate.name);
        String subtitle = contactSubtitle(address, title, known, conversation);
        TextView titleView = text(safeName(title, "Contato nBTChat"), 17, primary(), Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(titleView);
        TextView subtitleView = text(subtitle, 13, secondary(), Typeface.NORMAL);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(subtitleView);
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

    private String contactSubtitle(String address, String title, UserProfile profile, MessageStore.ConversationInfo conversation) {
        if (isRemoteTyping(address)) {
            return safeName(title, "Contato") + " esta digitando...";
        }
        if (profileStore.isBlocked(address)) {
            return "Bloqueado";
        }
        String suffix = profileStore.isMuted(address) ? "Silenciado - " : "";
        if (conversation.lastBody != null && !conversation.lastBody.isEmpty()) {
            return suffix + conversation.lastBody;
        }
        if (profile != null && !profile.getStatus().isEmpty()) {
            return suffix + profile.getStatus();
        }
        return suffix + "Toque para abrir a conversa";
    }

    private String contactPresenceStatus(String address) {
        String value = contactPresence.get(address);
        if (AppSettingsStore.PRESENCE_INVISIBLE.equals(value) || "offline".equals(value)) {
            return AppSettingsStore.PRESENCE_INVISIBLE;
        }
        if ("busy".equals(value)) {
            return AppSettingsStore.PRESENCE_BUSY;
        }
        if ("online".equals(value) || onlineAddresses.contains(address) || btChatManager.isConnectedTo(address)) {
            return AppSettingsStore.PRESENCE_ONLINE;
        }
        return AppSettingsStore.PRESENCE_INVISIBLE;
    }

    private boolean isRemoteTyping(String address) {
        Long until = remoteTypingUntil.get(address);
        return until != null && until > System.currentTimeMillis();
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
        addTopActions(top);
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
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(NAME_LIMIT)});
        nameInput.setText(safeName(saved.getDisplayName(), ""));
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
            if (name.length() > NAME_LIMIT) {
                name = name.substring(0, NAME_LIMIT);
            }
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
        replyPreviewBar = null;

        LinearLayout root = vertical();
        root.setBackgroundColor(color(chatBackground()));
        applyRootInsets(root, dp(12), dp(8), dp(12), dp(8));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconButton(R.drawable.ic_back_24, "Voltar", dp(42), v -> showHomeScreen()));

        FrameLayout avatarFrame = avatarStatusFrame(currentRemoteProfile, contactPresenceStatus(currentRemoteAddress), dp(52), dp(18), dp(4), false, v -> showContactInfoDialog());
        chatAvatarFrame = avatarFrame;
        chatConnectionIcon = null;
        top.addView(avatarFrame, leftMargin(dp(10), dp(52), dp(52)));

        LinearLayout who = vertical();
        LinearLayout nameRow = horizontal();
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        String title = currentRemoteProfile.isComplete() ? currentRemoteProfile.getDisplayName() : "Conversa Bluetooth";
        chatTitleText = text(safeName(title, "Conversa Bluetooth"), 17, primary(), Typeface.BOLD);
        chatTitleText.setSingleLine(true);
        chatTitleText.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(chatTitleText, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        who.addView(nameRow);
        String subtitle = currentRemoteProfile.getStatus().isEmpty() ? "Bluetooth seguro" : currentRemoteProfile.getStatus();
        chatSubtitleText = text(subtitle, 12, secondary(), Typeface.NORMAL);
        chatSubtitleText.setSingleLine(true);
        chatSubtitleText.setEllipsize(TextUtils.TruncateAt.END);
        who.addView(chatSubtitleText);
        LinearLayout.LayoutParams whoParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        whoParams.setMargins(dp(14), 0, 0, 0);
        top.addView(who, whoParams);
        updateChatHeaderProfile();

        addTopActions(top);
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
        if (!profileStore.isMuted(currentRemoteAddress)) {
            for (String id : readIds) {
                btChatManager.sendReceipt(currentRemoteAddress, id, MessageStore.STATUS_READ);
            }
        }
        renderChatHistory(true);

        addReplyPreviewBar(root);

        LinearLayout composer = horizontal();
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(0, dp(6), 0, 0);
        composer.addView(iconButton(R.drawable.ic_emoji_24, "Emoji", dp(46), v -> showEmojiPicker(v)));

        LinearLayout inputShell = horizontal();
        inputShell.setGravity(Gravity.CENTER_VERTICAL);
        inputShell.setPadding(dp(2), 0, dp(4), 0);
        inputShell.setBackground(rounded(surface(), dp(18), border()));
        messageInput = richMessageInput("Mensagem");
        messageInput.setSingleLine(false);
        messageInput.setMinLines(1);
        messageInput.setMaxLines(4);
        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                handleLocalTypingChanged(s);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
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

    private void addReplyPreviewBar(LinearLayout root) {
        if (pendingReplyId.isEmpty() || !currentRemoteAddress.equals(pendingReplyAddress)) {
            return;
        }
        LinearLayout bar = horizontal();
        replyPreviewBar = bar;
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(9), dp(8), dp(9));
        bar.setBackground(rounded(darkMode ? "#1C2C2B" : "#DFF4EC", dp(14), "#16A34A"));

        LinearLayout texts = vertical();
        texts.addView(text("Respondendo", 12, "#16A34A", Typeface.BOLD));
        TextView preview = text(pendingReplyPreview, 14, primary(), Typeface.NORMAL);
        preview.setSingleLine(true);
        texts.addView(preview);
        bar.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(iconButton(R.drawable.ic_close_24, "Cancelar resposta", dp(38), v -> {
            clearPendingReply();
            showChatScreen(currentRemoteProfile, currentFingerprint);
        }));
        LinearLayout.LayoutParams params = topMargin(dp(4));
        params.setMargins(0, dp(4), 0, dp(4));
        root.addView(bar, params);
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
        showFullscreenImage(imageBase64, MessageStore.KIND_IMAGE);
    }

    private void showFullscreenImage(String imageBase64, String kind) {
        Drawable drawable = mediaDrawable(kind, imageBase64);
        if (drawable == null) {
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
        image.setImageDrawable(drawable);
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
            startAnimatedDrawable(drawable);
        });
        dialog.show();
    }

    private String activeReplyId() {
        return currentRemoteAddress.equals(pendingReplyAddress) ? pendingReplyId : "";
    }

    private String activeReplyPreview() {
        return currentRemoteAddress.equals(pendingReplyAddress) ? pendingReplyPreview : "";
    }

    private void clearPendingReply() {
        pendingReplyAddress = "";
        pendingReplyId = "";
        pendingReplyPreview = "";
        if (replyPreviewBar != null) {
            ViewGroup parent = (ViewGroup) replyPreviewBar.getParent();
            if (parent != null) {
                parent.removeView(replyPreviewBar);
            }
            replyPreviewBar = null;
        }
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
        sendLocalTyping(false);
        long sentAt = System.currentTimeMillis();
        String id = messageStore.createId();
        String replyToId = activeReplyId();
        String replyPreview = activeReplyPreview();
        messageStore.addMessage(currentRemoteAddress, id, MessageStore.KIND_TEXT, body, "", 0L, true, sentAt, MessageStore.STATUS_PENDING, false, replyToId, replyPreview);
        addMessageBubble(id, body, true, MessageStore.KIND_TEXT, "", 0L, MessageStore.STATUS_PENDING, replyToId, replyPreview, true);
        sendOrQueueOutgoing(currentRemoteAddress, id, MessageStore.KIND_TEXT, body, "", 0L, sentAt, replyToId, replyPreview);
        clearPendingReply();
    }

    private void handleLocalTypingChanged(CharSequence text) {
        if (!"chat".equals(currentScreen) || currentRemoteAddress == null || currentRemoteAddress.isEmpty()) {
            return;
        }
        boolean hasText = text != null && text.toString().trim().length() > 0;
        long now = System.currentTimeMillis();
        if (hasText) {
            if (!localTypingSent || now - lastTypingSentAt > 1800L) {
                sendLocalTyping(true);
            }
            if (typingStopRunnable != null) {
                uiHandler.removeCallbacks(typingStopRunnable);
            }
            typingStopRunnable = () -> sendLocalTyping(false);
            uiHandler.postDelayed(typingStopRunnable, 2600L);
        } else {
            sendLocalTyping(false);
        }
    }

    private void sendLocalTyping(boolean typing) {
        if (currentRemoteAddress == null || currentRemoteAddress.isEmpty() || profileStore.isBlocked(currentRemoteAddress)) {
            return;
        }
        if (typingStopRunnable != null && !typing) {
            uiHandler.removeCallbacks(typingStopRunnable);
            typingStopRunnable = null;
        }
        if (localTypingSent == typing) {
            return;
        }
        localTypingSent = typing;
        lastTypingSentAt = System.currentTimeMillis();
        btChatManager.sendTyping(currentRemoteAddress, typing);
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
        if (profileStore.isBlocked(currentRemoteAddress)) {
            Toast.makeText(this, "Contato bloqueado. Desbloqueie para enviar mensagens.", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void sendImageMessage(String imageBase64) {
        sendMediaMessage(MessageStore.KIND_IMAGE, "", imageBase64);
    }

    private void sendGifMessage(String gifBase64) {
        sendMediaMessage(MessageStore.KIND_GIF, "GIF", gifBase64);
    }

    private void sendMediaMessage(String kind, String body, String mediaBase64) {
        if (mediaBase64 == null || mediaBase64.isEmpty() || !ensureCanSendMedia()) {
            return;
        }
        long sentAt = System.currentTimeMillis();
        String id = messageStore.createId();
        String replyToId = activeReplyId();
        String replyPreview = activeReplyPreview();
        String cleanKind = kind == null || kind.isEmpty() ? MessageStore.KIND_IMAGE : kind;
        String cleanBody = body == null ? "" : body;
        messageStore.addMessage(currentRemoteAddress, id, cleanKind, cleanBody, mediaBase64, 0L, true, sentAt, MessageStore.STATUS_PENDING, false, replyToId, replyPreview);
        addMessageBubble(id, cleanBody, true, cleanKind, mediaBase64, 0L, MessageStore.STATUS_PENDING, replyToId, replyPreview, true);
        sendOrQueueOutgoing(currentRemoteAddress, id, cleanKind, cleanBody, mediaBase64, 0L, sentAt, replyToId, replyPreview);
        clearPendingReply();
    }

    private void sendMediaFromUri(Uri uri) {
        MediaPayload payload = mediaPayloadFromUri(uri);
        if (payload == null) {
            return;
        }
        if (MessageStore.KIND_GIF.equals(payload.kind)) {
            sendGifMessage(payload.mediaBase64);
        } else {
            sendImageMessage(payload.mediaBase64);
        }
    }

    private boolean handleComposerContentUri(Uri uri) {
        if (uri == null || !"chat".equals(currentScreen) || currentRemoteAddress == null || currentRemoteAddress.isEmpty()) {
            Toast.makeText(this, "Abra uma conversa para enviar este item.", Toast.LENGTH_LONG).show();
            return false;
        }
        sendMediaFromUri(uri);
        return true;
    }

    private void preparePendingSharedMedia(Uri uri) {
        MediaPayload payload = mediaPayloadFromUri(uri);
        if (payload == null) {
            return;
        }
        pendingSharedKind = payload.kind;
        pendingSharedBody = payload.body;
        pendingSharedMediaBase64 = payload.mediaBase64;
        if ("chat".equals(currentScreen) && currentRemoteAddress != null && !currentRemoteAddress.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Enviar imagem?")
                    .setMessage("Enviar este item para " + safeName(currentRemoteProfile.getDisplayName(), "este contato") + "?")
                    .setPositiveButton("Enviar", (dialog, which) -> sendPendingSharedMediaTo(currentRemoteAddress))
                    .setNegativeButton("Escolher contato", (dialog, which) -> showPendingSharedMediaChooser())
                    .show();
        } else {
            showPendingSharedMediaChooser();
        }
    }

    private void showPendingSharedMediaChooser() {
        if (pendingSharedMediaBase64.isEmpty()) {
            return;
        }
        Map<String, UserProfile> contacts = profileStore.loadContacts();
        List<String> addresses = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, UserProfile> entry : contacts.entrySet()) {
            addresses.add(entry.getKey());
            UserProfile profile = entry.getValue();
            names.add(safeName(profile.isComplete() ? profile.getDisplayName() : "Contato nBTChat", "Contato"));
        }
        if (addresses.isEmpty()) {
            Toast.makeText(this, "Nenhum contato nBTChat para receber a imagem.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Compartilhar imagem com")
                .setItems(names.toArray(new String[0]), (dialog, which) -> sendPendingSharedMediaTo(addresses.get(which)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void sendPendingSharedMediaTo(String address) {
        if (address == null || address.trim().isEmpty() || pendingSharedMediaBase64.isEmpty()) {
            return;
        }
        long sentAt = System.currentTimeMillis();
        String id = messageStore.createId();
        messageStore.addMessage(address, id, pendingSharedKind, pendingSharedBody, pendingSharedMediaBase64, 0L, true, sentAt, MessageStore.STATUS_PENDING, false);
        sendOrQueueOutgoing(address, id, pendingSharedKind, pendingSharedBody, pendingSharedMediaBase64, 0L, sentAt);
        pendingSharedKind = "";
        pendingSharedBody = "";
        pendingSharedMediaBase64 = "";
        Toast.makeText(this, "Imagem compartilhada no nBTChat.", Toast.LENGTH_SHORT).show();
        if ("home".equals(currentScreen)) {
            renderContactList();
        }
    }

    private void sendVoiceMessage(String audioBase64, long durationMs) {
        if (audioBase64 == null || audioBase64.isEmpty() || !ensureCanSendMedia()) {
            return;
        }
        long sentAt = System.currentTimeMillis();
        String id = messageStore.createId();
        String replyToId = activeReplyId();
        String replyPreview = activeReplyPreview();
        messageStore.addMessage(currentRemoteAddress, id, MessageStore.KIND_VOICE, "", audioBase64, durationMs, true, sentAt, MessageStore.STATUS_PENDING, false, replyToId, replyPreview);
        addMessageBubble(id, "", true, MessageStore.KIND_VOICE, audioBase64, durationMs, MessageStore.STATUS_PENDING, replyToId, replyPreview, true);
        sendOrQueueOutgoing(currentRemoteAddress, id, MessageStore.KIND_VOICE, "", audioBase64, durationMs, sentAt, replyToId, replyPreview);
        clearPendingReply();
    }

    private void sendTable100ToAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        if (!gadgetStore.hasTable100()) {
            Toast.makeText(this, "Compre a Tabela 100 na loja para enviar.", Toast.LENGTH_LONG).show();
            showStoreScreen();
            return;
        }
        GadgetStore.Table100Payload payload = gadgetStore.table100Payload();
        if (payload.copyText.trim().isEmpty()) {
            Toast.makeText(this, "Configure a Tabela 100 antes de enviar.", Toast.LENGTH_LONG).show();
            showTable100ConfigScreen();
            return;
        }
        String body = payload.toMessageBody();
        long sentAt = System.currentTimeMillis();
        String id = messageStore.createId();
        messageStore.addMessage(address, id, MessageStore.KIND_TABLE_100, body, "", 0L, true, sentAt, MessageStore.STATUS_PENDING, false);
        sendOrQueueOutgoing(address, id, MessageStore.KIND_TABLE_100, body, "", 0L, sentAt);
        Toast.makeText(this, "Tabela 100 enviada.", Toast.LENGTH_SHORT).show();
        if ("home".equals(currentScreen)) {
            renderContactList();
        }
    }

    private void showStoreScreen() {
        currentScreen = "store";
        messageList = null;

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = vertical();
        root.setBackgroundColor(color(background()));
        applyRootInsets(root, dp(16), dp(10), dp(16), dp(16));
        scrollView.addView(root, matchWrap());

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconButton(R.drawable.ic_back_24, "Voltar", dp(42), v -> showHomeScreen()));
        TextView title = text("Loja", 28, primary(), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(12), 0, 0, 0);
        top.addView(title, titleParams);
        addTopActions(top);
        root.addView(top);

        TextView subtitle = text("Gadgets oficiais para usar dentro do nBTChat.", 14, secondary(), Typeface.NORMAL);
        root.addView(subtitle, topMargin(dp(8)));

        root.addView(table100StoreItem(), topMargin(dp(18)));

        attachTopLevelSwipe(root);
        attachTopLevelSwipe(scrollView);
        setContentView(scrollView);
        requestInsets(root);
    }

    private View table100StoreItem() {
        LinearLayout item = vertical();
        item.setPadding(dp(14), dp(14), dp(14), dp(14));
        item.setBackground(rounded(surface(), dp(12), border()));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_table_24);
        icon.setColorFilter(color("#16A34A"));
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        icon.setBackground(rounded(darkMode ? "#18372C" : "#DDF7E8", dp(18), "#16A34A"));
        header.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout texts = vertical();
        TextView name = text("Tabela 100", 19, primary(), Typeface.BOLD);
        texts.addView(name);
        TextView description = text("100 numeros interativos para enviar em uma conversa.", 13, secondary(), Typeface.NORMAL);
        description.setLineSpacing(dp(2), 1f);
        texts.addView(description, topMargin(dp(2)));
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        textsParams.setMargins(dp(14), 0, 0, 0);
        header.addView(texts, textsParams);
        item.addView(header);

        if (gadgetStore.hasTable100()) {
            TextView active = text("Ativo ate " + formatFullDate(gadgetStore.table100Until()), 13, "#16A34A", Typeface.BOLD);
            item.addView(active, topMargin(dp(14)));
            String configured = gadgetStore.table100CopyText().isEmpty()
                    ? "Sem texto configurado."
                    : "Mensagem: " + (gadgetStore.table100OwnerMessage().isEmpty() ? "sem mensagem" : gadgetStore.table100OwnerMessage())
                    + "\nCopiar: " + gadgetStore.table100CopyText();
            TextView current = text(configured, 14, primary(), Typeface.NORMAL);
            current.setPadding(dp(12), dp(10), dp(12), dp(10));
            current.setBackground(rounded(surfaceAlt(), dp(12), border()));
            item.addView(current, topMargin(dp(8)));
            Button options = pillButton("Opcoes", "#16A34A", "#FFFFFF");
            options.setOnClickListener(v -> showTable100OptionsDialog());
            item.addView(options, topMargin(dp(12)));
            item.setOnClickListener(v -> showTable100OptionsDialog());
        } else {
            TextView price = text("Teste: uso liberado por 7 dias.", 13, secondary(), Typeface.BOLD);
            item.addView(price, topMargin(dp(14)));
            Button buy = pillButton("Comprar teste", "#16A34A", "#FFFFFF");
            buy.setOnClickListener(v -> {
                gadgetStore.buyTable100();
                Toast.makeText(this, "Tabela 100 liberada por 7 dias.", Toast.LENGTH_SHORT).show();
                showTable100ConfigScreen();
            });
            item.addView(buy, topMargin(dp(12)));
            item.setOnClickListener(v -> buy.performClick());
        }
        return item;
    }

    private void showTable100OptionsDialog() {
        List<String> actions = new ArrayList<>();
        actions.add("Abrir");
        actions.add("Configurar");
        actions.add("Compartilhar");
        new AlertDialog.Builder(this)
                .setTitle("Tabela 100")
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if ("Abrir".equals(action)) {
                        showTable100PlayScreen(gadgetStore.table100Payload().toMessageBody());
                    } else if ("Configurar".equals(action)) {
                        showTable100ConfigScreen();
                    } else if ("Compartilhar".equals(action)) {
                        showTable100ShareChooser();
                    }
                })
                .show();
    }

    private void showTable100ShareChooser() {
        if (!gadgetStore.hasTable100()) {
            Toast.makeText(this, "Compre a Tabela 100 antes de compartilhar.", Toast.LENGTH_LONG).show();
            return;
        }
        if (gadgetStore.table100CopyText().trim().isEmpty()) {
            Toast.makeText(this, "Configure a Tabela 100 antes de compartilhar.", Toast.LENGTH_LONG).show();
            showTable100ConfigScreen();
            return;
        }
        Map<String, UserProfile> contacts = profileStore.loadContacts();
        List<String> addresses = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Map.Entry<String, UserProfile> entry : contacts.entrySet()) {
            if (btChatManager.getPairedCandidate(entry.getKey()) == null) {
                continue;
            }
            addresses.add(entry.getKey());
            UserProfile profile = entry.getValue();
            names.add(safeName(profile.isComplete() ? profile.getDisplayName() : "Contato", "Contato"));
        }
        if (addresses.isEmpty()) {
            Toast.makeText(this, "Nenhum contato pareado para compartilhar.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Compartilhar Tabela 100")
                .setItems(names.toArray(new String[0]), (dialog, which) -> sendTable100ToAddress(addresses.get(which)))
                .show();
    }

    private void showTable100ConfigScreen() {
        currentScreen = "store_config";
        messageList = null;

        LinearLayout root = vertical();
        root.setBackgroundColor(color(background()));
        applyRootInsets(root, dp(18), dp(12), dp(18), dp(16));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconButton(R.drawable.ic_back_24, "Voltar", dp(42), v -> showStoreScreen()));
        TextView title = text("Tabela 100", 25, primary(), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(12), 0, 0, 0);
        top.addView(title, titleParams);
        addTopActions(top);
        root.addView(top);

        TextView subtitle = text("Configure o que aparece para quem usa a tabela e o texto que sera copiado ao confirmar um numero.", 14, secondary(), Typeface.NORMAL);
        subtitle.setLineSpacing(dp(2), 1f);
        root.addView(subtitle, topMargin(dp(12)));

        root.addView(label("Mensagem para quem usar"));
        EditText messageInput = input("Mensagem exibida depois da escolha");
        messageInput.setSingleLine(false);
        messageInput.setMinLines(3);
        messageInput.setMaxLines(6);
        messageInput.setText(gadgetStore.table100OwnerMessage());
        root.addView(messageInput, topMargin(dp(6)));

        root.addView(label("Texto copiavel"));
        EditText copyInput = input("Texto que sera copiado");
        copyInput.setSingleLine(false);
        copyInput.setMinLines(3);
        copyInput.setMaxLines(7);
        copyInput.setText(gadgetStore.table100CopyText());
        root.addView(copyInput, topMargin(dp(6)));

        Button save = pillButton("Salvar", "#16A34A", "#FFFFFF");
        save.setOnClickListener(v -> {
            gadgetStore.saveTable100Texts(messageInput.getText().toString(), copyInput.getText().toString());
            hideKeyboard(copyInput);
            Toast.makeText(this, "Tabela 100 salva.", Toast.LENGTH_SHORT).show();
            showStoreScreen();
        });
        root.addView(save, topMargin(dp(16)));

        setContentView(root);
        requestInsets(root);
    }

    private void showTable100PlayScreen(String configuredText) {
        table100ReturnScreen = currentScreen;
        currentTable100Text = configuredText == null ? "" : configuredText.trim();
        GadgetStore.Table100Payload payload = GadgetStore.Table100Payload.parse(currentTable100Text);
        boolean owner = table100IsOwner(payload);
        currentScreen = "table100_play";
        messageList = null;

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = vertical();
        root.setBackgroundColor(color(background()));
        applyRootInsets(root, dp(16), dp(10), dp(16), dp(18));
        scrollView.addView(root, matchWrap());

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconButton(R.drawable.ic_back_24, "Voltar", dp(42), v -> {
            if ("chat".equals(table100ReturnScreen)) {
                showChatScreen(currentRemoteProfile, currentFingerprint);
            } else {
                showStoreScreen();
            }
        }));
        TextView title = text("Tabela 100", 26, primary(), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(12), 0, 0, 0);
        top.addView(title, titleParams);
        addTopActions(top);
        root.addView(top);

        TextView subtitle = text(owner
                ? "Confirme abaixo os numeros assinalados pelos contatos."
                : "Toque em um numero para confirmar sua escolha.", 14, secondary(), Typeface.NORMAL);
        subtitle.setLineSpacing(dp(2), 1f);
        root.addView(subtitle, topMargin(dp(10)));

        LinearLayout board = vertical();
        board.setPadding(dp(10), dp(10), dp(10), dp(10));
        board.setBackground(rounded(darkMode ? "#101C18" : "#F0FBF4", dp(14), "#16A34A"));
        board.addView(table100Grid(payload, false, true, owner));
        root.addView(board, topMargin(dp(18)));

        if (owner) {
            root.addView(table100OwnerChoices(payload), topMargin(dp(18)));
        }

        setContentView(scrollView);
        requestInsets(root);
    }

    private void refreshTable100PlayScreen() {
        String text = currentTable100Text;
        String returnScreen = table100ReturnScreen;
        showTable100PlayScreen(text);
        table100ReturnScreen = returnScreen;
    }

    private void sendOrQueueOutgoing(String address, String id, String kind, String body, String mediaBase64, long durationMs, long sentAt) {
        sendOrQueueOutgoing(address, id, kind, body, mediaBase64, durationMs, sentAt, "", "");
    }

    private void sendOrQueueOutgoing(String address, String id, String kind, String body, String mediaBase64, long durationMs, long sentAt, String replyToId, String replyPreview) {
        PendingOutgoing outgoing = new PendingOutgoing(address, id, kind, body, mediaBase64, durationMs, sentAt, replyToId, replyPreview);
        if (btChatManager.canSendTo(address)) {
            btChatManager.sendChatMessage(address, id, kind, body, mediaBase64, durationMs, sentAt, replyToId, replyPreview);
            return;
        }
        pendingOutgoing.add(outgoing);
        connectForAddress(address);
        Toast.makeText(this, "Vou enviar assim que o Bluetooth conectar.", Toast.LENGTH_SHORT).show();
    }

    private void flushPendingOutgoing(String address) {
        if (address == null || address.isEmpty() || !btChatManager.canSendTo(address)) {
            return;
        }
        if (!pendingOutgoing.isEmpty()) {
            List<PendingOutgoing> sent = new ArrayList<>();
            for (PendingOutgoing outgoing : pendingOutgoing) {
                if (address.equals(outgoing.address)) {
                    btChatManager.sendChatMessage(outgoing.address, outgoing.id, outgoing.kind, outgoing.body, outgoing.mediaBase64, outgoing.durationMs, outgoing.sentAt, outgoing.replyToId, outgoing.replyPreview);
                    sent.add(outgoing);
                }
            }
            pendingOutgoing.removeAll(sent);
        }
        if (!pendingDeletes.isEmpty()) {
            List<PendingDelete> sentDeletes = new ArrayList<>();
            for (PendingDelete delete : pendingDeletes) {
                if (address.equals(delete.address)) {
                    btChatManager.sendDeleteMessage(delete.address, delete.id);
                    sentDeletes.add(delete);
                }
            }
            pendingDeletes.removeAll(sentDeletes);
        }
    }

    private void resendUndeliveredMessages(String address) {
        if (address == null || address.isEmpty() || !btChatManager.canSendTo(address)) {
            return;
        }
        for (MessageStore.ChatMessage message : messageStore.undeliveredOutgoingMessages(address)) {
            btChatManager.sendChatMessage(address, message.id, message.kind, message.body, message.mediaBase64,
                    message.durationMs, message.sentAt, message.replyToId, message.replyPreview);
        }
    }

    private void sendOrQueueDelete(String address, String id) {
        if (address == null || address.isEmpty() || id == null || id.isEmpty()) {
            return;
        }
        if (btChatManager.canSendTo(address)) {
            btChatManager.sendDeleteMessage(address, id);
            return;
        }
        pendingDeletes.add(new PendingDelete(address, id));
        connectForAddress(address);
        Toast.makeText(this, "Vou apagar para todos assim que o Bluetooth conectar.", Toast.LENGTH_SHORT).show();
    }

    private void connectForAddress(String address) {
        BtChatManager.DeviceCandidate target = btChatManager.getPairedCandidate(address);
        if (target != null && !btChatManager.isConnectedTo(address)) {
            btChatManager.connect(target);
        }
    }

    private void pickChatImage() {
        startImagePicker(REQUEST_PICK_CHAT_IMAGE);
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
            configureVoicePlaybackRoute();
            playingVoicePlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(settingsStore.playVoiceOnPhone()
                            ? AudioAttributes.USAGE_VOICE_COMMUNICATION
                            : AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
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
        resetVoicePlaybackRoute();
        if (!keepButtonState && playingVoiceControls != null) {
            resetVoiceControls(playingVoiceControls);
        }
        playingVoiceControls = null;
        playingVoiceId = "";
    }

    private void configureVoicePlaybackRoute() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }
        try {
            if (settingsStore.playVoiceOnPhone()) {
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    for (AudioDeviceInfo deviceInfo : audioManager.getAvailableCommunicationDevices()) {
                        if (deviceInfo.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                            audioManager.setCommunicationDevice(deviceInfo);
                            return;
                        }
                    }
                }
                audioManager.setSpeakerphoneOn(true);
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice();
                }
                audioManager.setSpeakerphoneOn(false);
                audioManager.setMode(AudioManager.MODE_NORMAL);
            }
        } catch (Exception ignored) {
        }
    }

    private void resetVoicePlaybackRoute() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice();
            }
            audioManager.setSpeakerphoneOn(false);
            audioManager.setMode(AudioManager.MODE_NORMAL);
        } catch (Exception ignored) {
        }
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
                addMessageBubble(message.id, message.body, message.mine, message.kind, message.mediaBase64, message.durationMs, message.status, message.replyToId, message.replyPreview, false);
            }
        }
        if (scrollBottom) {
            scrollMessagesToBottom();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void attachMessageGestures(View view, String id) {
        if (id == null || id.isEmpty()) {
            return;
        }
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        view.setOnLongClickListener(v -> {
            showMessageActionDialog(id);
            return true;
        });
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX[0] = event.getX();
                downY[0] = event.getY();
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dx = event.getX() - downX[0];
                float dy = Math.abs(event.getY() - downY[0]);
                if (Math.abs(dx) > dp(72) && dy < dp(36)) {
                    beginReplyToMessage(id);
                    return true;
                }
            }
            return false;
        });
    }

    private TextView messageText(String body, boolean mine) {
        TextView view = text(body == null ? "" : body, 16, mine ? "#FFFFFF" : primary(), Typeface.NORMAL);
        applyLinkSpans(view, body == null ? "" : body, mine);
        return view;
    }

    private void addTextContentToBubble(LinearLayout bubble, String id, String body, boolean mine) {
        String value = body == null ? "" : body;
        if (value.length() <= LONG_MESSAGE_LIMIT) {
            TextView content = messageText(value, mine);
            attachMessageGestures(content, id);
            bubble.addView(content);
            return;
        }

        String preview = value.substring(0, LONG_MESSAGE_LIMIT).trim() + "...";
        TextView content = messageText(preview, mine);
        attachMessageGestures(content, id);
        bubble.addView(content);

        LinearLayout footer = horizontal();
        footer.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        ImageButton expand = iconButton(R.drawable.ic_expand_down_24, "Mostrar mensagem completa", dp(34), null);
        expand.setColorFilter(Color.WHITE);
        expand.setBackground(rounded("#2563EB", dp(17), "#60A5FA"));
        expand.setPadding(dp(7), dp(7), dp(7), dp(7));
        expand.setOnClickListener(v -> {
            content.setText(value);
            applyLinkSpans(content, value, mine);
            expand.setVisibility(View.GONE);
            scrollMessagesToBottom();
        });
        footer.addView(expand, new LinearLayout.LayoutParams(dp(34), dp(34)));
        bubble.addView(footer, topMargin(dp(6)));
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
        addMessageBubble("", body, mine, MessageStore.KIND_TEXT, "", 0L, mine ? MessageStore.STATUS_SENT : MessageStore.STATUS_DELIVERED, "", "", true);
    }

    private void addMessageBubble(String body, boolean mine, String kind, String mediaBase64, long durationMs, String status) {
        addMessageBubble("", body, mine, kind, mediaBase64, durationMs, status, "", "", true);
    }

    private void addMessageBubble(String id, String body, boolean mine, String kind, String mediaBase64, long durationMs, String status, String replyToId, String replyPreview, boolean scrollBottom) {
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
        attachMessageGestures(bubble, id);

        if (replyPreview != null && !replyPreview.trim().isEmpty()) {
            TextView reply = text(replyPreview, 13, mine ? "#D7FBE8" : secondary(), Typeface.BOLD);
            reply.setSingleLine(true);
            reply.setPadding(dp(8), dp(6), dp(8), dp(6));
            reply.setBackground(rounded(mine ? "#0C5F58" : surfaceAlt(), dp(10), mine ? "#0C5F58" : border()));
            attachMessageGestures(reply, id);
            bubble.addView(reply, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        if (MessageStore.KIND_TABLE_100.equals(kind)) {
            TextView title = text("Tabela 100", 16, mine ? "#FFFFFF" : primary(), Typeface.BOLD);
            title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_table_24, 0, 0, 0);
            title.setCompoundDrawablePadding(dp(8));
            bubble.addView(title);
            TextView hint = text("Toque para abrir", 12, mine ? "#D7FBE8" : secondary(), Typeface.NORMAL);
            bubble.addView(hint, topMargin(dp(2)));
            View.OnClickListener openTable = v -> showTable100PlayScreen(body);
            bubble.setOnClickListener(openTable);
            title.setOnClickListener(openTable);
            hint.setOnClickListener(openTable);
        } else if ((MessageStore.KIND_IMAGE.equals(kind) || MessageStore.KIND_GIF.equals(kind)) && mediaBase64 != null && !mediaBase64.isEmpty()) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            applyMediaToImageView(image, kind, mediaBase64);
            image.setOnClickListener(v -> showFullscreenImage(mediaBase64, kind));
            attachMessageGestures(image, id);
            bubble.addView(image, new LinearLayout.LayoutParams(dp(210), dp(150)));
            if (body != null && !body.isEmpty()) {
                TextView caption = messageText(body, mine);
                attachMessageGestures(caption, id);
                bubble.addView(caption, topMargin(dp(6)));
            }
        } else if (MessageStore.KIND_VOICE.equals(kind)) {
            LinearLayout voiceRow = horizontal();
            voiceRow.setGravity(Gravity.CENTER_VERTICAL);
            attachMessageGestures(voiceRow, id);
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
            addTextContentToBubble(bubble, id, body, mine);
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

    private GridLayout table100Grid(GadgetStore.Table100Payload payload, boolean mine, boolean fullScreen, boolean owner) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(fullScreen ? 10 : 5);
        grid.setPadding(0, dp(4), 0, 0);
        int available = Math.max(dp(280), getResources().getDisplayMetrics().widthPixels - dp(56));
        int cellSize = fullScreen ? Math.max(dp(29), Math.min(dp(42), available / 10 - dp(4))) : dp(40);
        for (int i = 1; i <= 100; i++) {
            final int number = i;
            Button cell = new Button(this);
            cell.setText(String.valueOf(i));
            cell.setTextSize(fullScreen ? 12 : 11);
            cell.setTextColor(color(fullScreen ? "#FFFFFF" : (mine ? "#FFFFFF" : primary())));
            cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cell.setAllCaps(false);
            cell.setMinWidth(0);
            cell.setMinimumWidth(0);
            cell.setMinHeight(0);
            cell.setMinimumHeight(0);
            cell.setPadding(0, 0, 0, 0);
            String fill = table100CellColor(payload, number, owner, fullScreen, mine);
            String stroke = fullScreen ? "#BBF7D0" : (mine ? "#7DD3FC" : border());
            cell.setBackground(rounded(fill, dp(fullScreen ? 12 : 8), stroke));
            cell.setOnClickListener(v -> showTable100NumberDialog(number, payload, owner));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = cellSize;
            params.height = fullScreen ? cellSize : dp(32);
            params.setMargins(dp(2), dp(2), dp(2), dp(2));
            grid.addView(cell, params);
        }
        return grid;
    }

    private String table100CellColor(GadgetStore.Table100Payload payload, int number, boolean owner, boolean fullScreen, boolean mine) {
        int status = table100NumberStatus(payload, number, owner);
        if (status == 2) {
            return "#16A34A";
        }
        if (status == 1) {
            return darkMode ? "#4B5563" : "#9CA3AF";
        }
        if (!fullScreen) {
            return mine ? "#0C5F58" : surfaceAlt();
        }
        int palette = number % 5;
        if (palette == 0) {
            return "#0F766E";
        }
        if (palette == 1) {
            return "#16A34A";
        }
        if (palette == 2) {
            return "#0284C7";
        }
        if (palette == 3) {
            return "#7C3AED";
        }
        return "#EA580C";
    }

    private int table100NumberStatus(GadgetStore.Table100Payload payload, int number, boolean owner) {
        if (payload == null || payload.tableId.isEmpty()) {
            return 0;
        }
        if (!owner) {
            return gadgetStore.choiceStatus(payload.tableId, currentRemoteAddress, number);
        }
        int status = 0;
        for (GadgetStore.Table100Choice choice : gadgetStore.loadChoices(payload.tableId)) {
            if (choice.number == number) {
                status = choice.confirmed ? 2 : Math.max(status, 1);
            }
        }
        return status;
    }

    private void showTable100NumberDialog(int number, GadgetStore.Table100Payload payload, boolean owner) {
        if (payload == null) {
            return;
        }
        if (owner) {
            showTable100ResultDialog(number, payload);
            return;
        }
        if (table100NumberStatus(payload, number, false) == 2) {
            showTable100ResultDialog(number, payload);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Confirmar escolha")
                .setMessage("Voce escolheu o numero " + number + ", deseja confirmar sua escolha?")
                .setPositiveButton("Sim", (dialog, which) -> {
                    markTable100Choice(payload, number);
                    showTable100ResultDialog(number, payload);
                })
                .setNegativeButton("Nao", null)
                .show();
    }

    private void showTable100ResultDialog(int number, GadgetStore.Table100Payload payload) {
        String message = payload.ownerMessage.trim().isEmpty()
                ? "Escolha registrada."
                : payload.ownerMessage.trim();
        String copyText = payload.copyText.trim().isEmpty()
                ? "Nenhum texto configurado para esta tabela."
                : payload.copyText.trim();
        copyToClipboard(copyText, false);

        LinearLayout content = vertical();
        content.setPadding(dp(18), dp(14), dp(18), dp(4));
        TextView messageView = text(message, 16, primary(), Typeface.BOLD);
        messageView.setLineSpacing(dp(2), 1f);
        content.addView(messageView);
        TextView box = text(copyText, 15, primary(), Typeface.NORMAL);
        box.setTextIsSelectable(true);
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.setBackground(rounded(surfaceAlt(), dp(12), border()));
        content.addView(box, topMargin(dp(12)));

        new AlertDialog.Builder(this)
                .setTitle("Numero " + number)
                .setView(content)
                .setPositiveButton("Copiar", (dialog, which) -> copyMessageText(copyText))
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void markTable100Choice(GadgetStore.Table100Payload payload, int number) {
        if (payload == null || payload.tableId.isEmpty() || currentRemoteAddress == null || currentRemoteAddress.isEmpty()) {
            return;
        }
        UserProfile owner = profileStore.loadContact(currentRemoteAddress);
        gadgetStore.saveChoice(payload.tableId, currentRemoteAddress, number, owner.isComplete() ? owner.getDisplayName() : "Contato", false);
        sendTable100Choice(payload, number);
    }

    private View table100OwnerChoices(GadgetStore.Table100Payload payload) {
        LinearLayout container = vertical();
        container.setPadding(dp(14), dp(14), dp(14), dp(14));
        container.setBackground(rounded(surface(), dp(14), border()));
        container.addView(text("Escolhas dos contatos", 18, primary(), Typeface.BOLD));

        List<GadgetStore.Table100Choice> choices = gadgetStore.loadChoices(payload.tableId);
        if (choices.isEmpty()) {
            TextView empty = text("Nenhum contato assinalou esta tabela ainda.", 14, secondary(), Typeface.NORMAL);
            container.addView(empty, topMargin(dp(10)));
            return container;
        }

        boolean anyPending = false;
        for (GadgetStore.Table100Choice choice : choices) {
            if (!choice.confirmed) {
                if (!anyPending) {
                    container.addView(text("Pendentes", 13, secondary(), Typeface.BOLD), topMargin(dp(12)));
                    anyPending = true;
                }
                container.addView(table100ChoiceRow(payload, choice), topMargin(dp(8)));
            }
        }

        boolean anyConfirmed = false;
        for (GadgetStore.Table100Choice choice : choices) {
            if (choice.confirmed) {
                if (!anyConfirmed) {
                    container.addView(text("Confirmados", 13, secondary(), Typeface.BOLD), topMargin(dp(14)));
                    anyConfirmed = true;
                }
                container.addView(table100ChoiceRow(payload, choice), topMargin(dp(8)));
            }
        }
        return container;
    }

    private View table100ChoiceRow(GadgetStore.Table100Payload payload, GadgetStore.Table100Choice choice) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));
        row.setBackground(rounded(surfaceAlt(), dp(12), border()));

        UserProfile profile = profileStore.loadContact(choice.address);
        String name = profile.isComplete() ? profile.getDisplayName() : (choice.name.isEmpty() ? "Contato" : choice.name);
        TextView label = text(safeName(name, "Contato") + " - numero " + choice.number, 15, primary(), Typeface.BOLD);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Switch toggle = new Switch(this);
        toggle.setChecked(choice.confirmed);
        toggle.setOnClickListener(v -> {
            boolean target = toggle.isChecked();
            String action = target ? "confirmar" : "remover a confirmacao de";
            new AlertDialog.Builder(this)
                    .setTitle(target ? "Confirmar escolha?" : "Remover confirmacao?")
                    .setMessage("Deseja " + action + " " + safeName(name, "Contato") + " no numero " + choice.number + "?")
                    .setPositiveButton("Sim", (dialog, which) -> {
                        gadgetStore.setChoiceConfirmed(payload.tableId, choice.address, choice.number, target);
                        sendTable100Confirmation(payload, choice.address, choice.number, target);
                        refreshTable100PlayScreen();
                    })
                    .setNegativeButton("Nao", (dialog, which) -> toggle.setChecked(!target))
                    .show();
        });
        row.addView(toggle);
        return row;
    }

    private boolean table100IsOwner(GadgetStore.Table100Payload payload) {
        return payload != null && !payload.tableId.isEmpty() && payload.tableId.equals(gadgetStore.table100InstanceId());
    }

    private void sendTable100Choice(GadgetStore.Table100Payload payload, int number) {
        try {
            JSONObject json = new JSONObject();
            json.put("tableId", payload.tableId);
            json.put("number", number);
            json.put("name", profileStore.loadLocalProfile().getDisplayName());
            sendOrQueueOutgoing(currentRemoteAddress, messageStore.createId(), MessageStore.KIND_TABLE_100_CHOICE,
                    json.toString(), "", 0L, System.currentTimeMillis());
        } catch (Exception ignored) {
        }
    }

    private void sendTable100Confirmation(GadgetStore.Table100Payload payload, String address, int number, boolean confirmed) {
        try {
            JSONObject json = new JSONObject();
            json.put("tableId", payload.tableId);
            json.put("number", number);
            json.put("confirmed", confirmed);
            sendOrQueueOutgoing(address, messageStore.createId(), MessageStore.KIND_TABLE_100_CONFIRM,
                    json.toString(), "", 0L, System.currentTimeMillis());
        } catch (Exception ignored) {
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
                String name = json.optString("name", "");
                gadgetStore.saveChoice(tableId, address, number, name, false);
            } else {
                boolean confirmed = json.optBoolean("confirmed", false);
                gadgetStore.setChoiceConfirmed(tableId, address, number, confirmed);
            }
            if ("table100_play".equals(currentScreen) && currentTable100Text.contains(tableId)) {
                refreshTable100PlayScreen();
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    private void updateChatHeaderStatus() {
        String presence = contactPresenceStatus(currentRemoteAddress);
        if (chatAvatarFrame != null) {
            chatAvatarFrame.setBackground(roundedStroke(surface(), dp(18), presenceColor(presence), dp(4)));
        }
        if (chatConnectionIcon != null) {
            chatConnectionIcon.setImageResource(presenceDrawable(presence));
            chatConnectionIcon.setContentDescription(presenceLabel(presence));
            chatConnectionIcon.setBackground(rounded("#FFFFFF", dp(11), "#FFFFFF"));
        }
    }

    private void updateChatHeaderProfile() {
        if (chatTitleText != null) {
            String title = currentRemoteProfile.isComplete() ? currentRemoteProfile.getDisplayName() : "Conversa Bluetooth";
            chatTitleText.setText(safeName(title, "Conversa Bluetooth"));
        }
        if (chatSubtitleText != null) {
            String subtitle;
            if (isRemoteTyping(currentRemoteAddress)) {
                String name = currentRemoteProfile.isComplete() ? currentRemoteProfile.getDisplayName() : "Contato";
                subtitle = safeName(name, "Contato") + " esta digitando...";
            } else if (profileStore.isBlocked(currentRemoteAddress)) {
                subtitle = "Bloqueado";
            } else {
                subtitle = currentRemoteProfile.getStatus().isEmpty() ? "Bluetooth seguro" : currentRemoteProfile.getStatus();
            }
            chatSubtitleText.setText(subtitle);
        }
        updateChatHeaderStatus();
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

    private String formatFullDate(long when) {
        if (when <= 0L) {
            return "";
        }
        return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date(when));
    }

    private String safeName(String value, String fallback) {
        String name = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        while (name.contains("  ")) {
            name = name.replace("  ", " ");
        }
        if (name.isEmpty()) {
            name = fallback;
        }
        return name.length() > NAME_LIMIT ? name.substring(0, NAME_LIMIT) : name;
    }

    @Override
    public void onBluetoothState(String state) {
        if (!"chat".equals(currentScreen)) {
            showState(state);
        }
    }

    @Override
    public void onDeviceFound(BtChatManager.DeviceCandidate candidate) {
        if (candidate.address == null || candidate.address.isEmpty()) {
            return;
        }
        if (matchesPendingQr(candidate)) {
            connectPendingQrCandidate(candidate);
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
        if ("home".equals(currentScreen)) {
            renderContactList();
        }
    }

    @Override
    public void onDiscoveryFinished() {
        showState("Busca finalizada.");
        if ("scanner".equals(currentScreen)) {
            renderNearbyDeviceList();
        } else if ("home".equals(currentScreen)) {
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
        if (address.isEmpty()) {
            return;
        }
        UserProfile updatedProfile = profile == null ? UserProfile.empty() : profile;
        profileStore.saveContact(address, updatedProfile);
        if (address.equals(currentRemoteAddress)) {
            currentRemoteProfile = updatedProfile;
        }
        if ("home".equals(currentScreen)) {
            renderContactList();
        } else if ("chat".equals(currentScreen) && address.equals(currentRemoteAddress)) {
            updateChatHeaderProfile();
        }
    }

    @Override
    public void onRemoteIdentity(String remoteAddress, String deviceId, String identityPublicKey) {
        profileStore.saveIdentity(remoteAddress, deviceId, identityPublicKey);
    }

    @Override
    public void onConnected(String remoteAddress, UserProfile profile, String fingerprint) {
        String address = remoteAddress == null ? "" : remoteAddress;
        if (address.isEmpty()) {
            return;
        }
        if (profileStore.isBlocked(address)) {
            btChatManager.disconnectCurrent();
            onlineAddresses.remove(address);
            contactPresence.put(address, AppSettingsStore.PRESENCE_INVISIBLE);
            renderContactList();
            return;
        }
        UserProfile profileValue = profile == null ? UserProfile.empty() : profile;
        String fingerprintValue = fingerprint == null ? "" : fingerprint;
        onlineAddresses.add(address);
        contactPresence.put(address, AppSettingsStore.PRESENCE_ONLINE);
        profileStore.saveContact(address, profileValue);
        profileStore.saveFingerprint(address, fingerprintValue);
        btChatManager.sendPresence(presenceForPeer(settingsStore.userPresence()));

        boolean activeChat = "chat".equals(currentScreen) && address.equals(currentRemoteAddress);
        boolean qrOpenedConnection = (address.equals(pendingOpenChatAddress) || (pendingOpenChatAddress.isEmpty() && openNextQrConnectionUntil > System.currentTimeMillis()))
                && openNextQrConnectionUntil > System.currentTimeMillis();
        if (qrOpenedConnection) {
            pendingOpenChatAddress = "";
            openNextQrConnectionUntil = 0L;
        }
        if (activeChat || "scanner".equals(currentScreen) || qrOpenedConnection) {
            currentRemoteAddress = address;
            currentRemoteProfile = profileValue;
            currentFingerprint = fingerprintValue;
            if (activeChat) {
                updateChatHeaderProfile();
            } else {
                showChatScreen(currentRemoteProfile, currentFingerprint);
            }
        } else if ("home".equals(currentScreen)) {
            renderContactList();
        }
        flushPendingOutgoing(address);
        resendUndeliveredMessages(address);
    }

    @Override
    public void onMessageReceived(String remoteAddress, String id, String kind, String body, String mediaBase64, long durationMs, long sentAt, String replyToId, String replyPreview) {
        String address = remoteAddress == null ? "" : remoteAddress;
        if (address.isEmpty()) {
            return;
        }
        if (profileStore.isBlocked(address)) {
            return;
        }
        if (handleTable100Event(address, kind, body)) {
            return;
        }
        boolean activeChat = "chat".equals(currentScreen) && address.equals(currentRemoteAddress);
        boolean inserted = messageStore.addMessage(address, id, kind, body, mediaBase64, durationMs, false, sentAt, MessageStore.STATUS_DELIVERED, !activeChat, replyToId, replyPreview);
        if (activeChat) {
            messageStore.markRead(address);
            if (!profileStore.isMuted(address)) {
                btChatManager.sendReceipt(address, id, MessageStore.STATUS_READ);
            }
            if (inserted) {
                addMessageBubble(id, body, false, kind, mediaBase64, durationMs, MessageStore.STATUS_DELIVERED, replyToId, replyPreview, true);
            }
        } else if ("home".equals(currentScreen)) {
            renderContactList();
        }
    }

    @Override
    public void onReceiptReceived(String remoteAddress, String id, String status) {
        String address = remoteAddress == null || remoteAddress.isEmpty() ? currentRemoteAddress : remoteAddress;
        if (profileStore.isMuted(address) && !MessageStore.STATUS_SENT.equals(status)) {
            return;
        }
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
    public void onMessageDeleted(String remoteAddress, String id) {
        String address = remoteAddress == null || remoteAddress.isEmpty() ? currentRemoteAddress : remoteAddress;
        messageStore.deleteMessage(address, id);
        if ("chat".equals(currentScreen) && address.equals(currentRemoteAddress)) {
            renderChatHistory(false);
        } else if ("home".equals(currentScreen)) {
            renderContactList();
        }
    }

    @Override
    public void onTypingReceived(String remoteAddress, boolean typing) {
        String address = remoteAddress == null ? "" : remoteAddress;
        if (address.isEmpty() || profileStore.isBlocked(address)) {
            return;
        }
        if (typing) {
            remoteTypingUntil.put(address, System.currentTimeMillis() + 4500L);
            uiHandler.postDelayed(() -> {
                if (!isRemoteTyping(address)) {
                    remoteTypingUntil.remove(address);
                    if ("chat".equals(currentScreen) && address.equals(currentRemoteAddress)) {
                        updateChatHeaderProfile();
                    } else if ("home".equals(currentScreen)) {
                        renderContactList();
                    }
                }
            }, 4600L);
        } else {
            remoteTypingUntil.remove(address);
        }
        if ("chat".equals(currentScreen) && address.equals(currentRemoteAddress)) {
            updateChatHeaderProfile();
        } else if ("home".equals(currentScreen)) {
            renderContactList();
        }
    }

    @Override
    public void onPresenceReceived(String remoteAddress, String status) {
        String address = remoteAddress == null ? "" : remoteAddress;
        if (address.isEmpty()) {
            return;
        }
        String clean = "busy".equals(status)
                ? AppSettingsStore.PRESENCE_BUSY
                : ("online".equals(status) ? AppSettingsStore.PRESENCE_ONLINE : AppSettingsStore.PRESENCE_INVISIBLE);
        contactPresence.put(address, clean);
        if (AppSettingsStore.PRESENCE_INVISIBLE.equals(clean)) {
            onlineAddresses.remove(address);
        } else {
            onlineAddresses.add(address);
        }
        if ("chat".equals(currentScreen) && address.equals(currentRemoteAddress)) {
            updateChatHeaderStatus();
        } else if ("home".equals(currentScreen)) {
            renderContactList();
        }
    }

    @Override
    public void onDisconnected(String remoteAddress) {
        String address = remoteAddress == null ? "" : remoteAddress;
        if (!address.isEmpty()) {
            onlineAddresses.remove(address);
            contactPresence.put(address, AppSettingsStore.PRESENCE_INVISIBLE);
            remoteTypingUntil.remove(address);
        }
        if ("chat".equals(currentScreen) && address.equals(currentRemoteAddress)) {
            updateChatHeaderStatus();
        } else if ("home".equals(currentScreen)) {
            renderContactList();
        }
    }

    @Override
    public void onError(String message) {
        if (openNextQrConnectionUntil > System.currentTimeMillis()) {
            pendingOpenChatAddress = "";
            openNextQrConnectionUntil = 0L;
        }
        if ("chat".equals(currentScreen) && isQuietConnectionMessage(message)) {
            updateChatHeaderStatus();
            return;
        }
        if (!"chat".equals(currentScreen)) {
            showState(message);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showState(String message) {
        if (stateText != null) {
            stateText.setText(message);
        }
    }

    private boolean isQuietConnectionMessage(String message) {
        if (message == null) {
            return false;
        }
        String value = message.toLowerCase(Locale.ROOT);
        return value.contains("encerrou")
                || value.contains("encerrada")
                || value.contains("erro na conexao")
                || value.contains("nenhuma conversa conectada");
    }

    private void handleStoredMessageChange(String address, String id, String status, boolean deleted) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        if (deleted) {
            if ("chat".equals(currentScreen) && address.equals(currentRemoteAddress)) {
                renderChatHistory(false);
            } else if ("home".equals(currentScreen)) {
                renderContactList();
            }
            return;
        }
        if (status != null && !status.trim().isEmpty()) {
            onReceiptReceived(address, id, status);
            return;
        }
        if ("chat".equals(currentScreen) && address.equals(currentRemoteAddress)) {
            messageStore.markRead(address);
            if (id != null && !id.trim().isEmpty() && !profileStore.isMuted(address)) {
                btChatManager.sendReceipt(address, id, MessageStore.STATUS_READ);
            }
            MessageStore.ChatMessage message = messageStore.findMessage(address, id);
            if (message != null) {
                addMessageBubble(message.id, message.body, message.mine, message.kind, message.mediaBase64, message.durationMs, message.status, message.replyToId, message.replyPreview, true);
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
        menu.getMenu().add(0, 12, order++, "Conectar por QR");
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
            if (item.getItemId() == 12) {
                showQrActionsDialog();
                return true;
            }
            if (item.getItemId() == 2) {
                shareApp();
                return true;
            }
            if (item.getItemId() == 4) {
                showSettingsScreen();
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
        String url = DOWNLOAD_PAGE_URL;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "nBTChat");
        intent.putExtra(Intent.EXTRA_TEXT, "Baixe o nBTChat para conversar por Bluetooth: " + url);
        startActivity(Intent.createChooser(intent, "Compartilhar nBTChat"));
    }

    private void showSettingsScreen() {
        currentScreen = "settings";
        messageList = null;

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = vertical();
        root.setBackgroundColor(color(background()));
        applyRootInsets(root, dp(18), dp(12), dp(18), dp(16));
        scrollView.addView(root, matchWrap());

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconButton(R.drawable.ic_back_24, "Voltar", dp(42), v -> showHomeScreen()));
        TextView title = text("Configuracoes", 27, primary(), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(12), 0, 0, 0);
        top.addView(title, titleParams);
        addTopActions(top);
        root.addView(top);

        LinearLayout card = vertical();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(rounded(surface(), dp(12), border()));
        Button profile = pillButton("Editar perfil", surfaceAlt(), primary());
        profile.setOnClickListener(v -> showProfileScreen());
        card.addView(profile);

        LinearLayout notificationRow = horizontal();
        notificationRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("Notificacoes", 16, primary(), Typeface.BOLD);
        notificationRow.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(settingsStore.notificationsEnabled());
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> settingsStore.setNotificationsEnabled(isChecked));
        notificationRow.addView(toggle);
        card.addView(notificationRow, topMargin(dp(18)));

        TextView soundTitle = text("Som de notificacao", 16, primary(), Typeface.BOLD);
        card.addView(soundTitle, topMargin(dp(18)));
        String soundName = settingsStore.notificationSoundName();
        TextView sound = text(soundName.isEmpty() ? "Padrao do Android" : soundName, 13, secondary(), Typeface.NORMAL);
        sound.setSingleLine(true);
        sound.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(sound, topMargin(dp(5)));

        LinearLayout soundActions = vertical();
        LinearLayout soundPickers = horizontal();
        Button choose = pillButton("Sons do aparelho", "#16A34A", "#FFFFFF");
        choose.setOnClickListener(v -> pickNotificationSound());
        soundPickers.addView(choose, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button file = pillButton("Arquivo", surfaceAlt(), primary());
        file.setOnClickListener(v -> pickNotificationSoundFile());
        LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        fileParams.setMargins(dp(8), 0, 0, 0);
        soundPickers.addView(file, fileParams);
        soundActions.addView(soundPickers);
        Button clear = pillButton("Padrao do Android", surfaceAlt(), primary());
        clear.setOnClickListener(v -> {
            settingsStore.clearNotificationSound();
            NotificationHelper.ensureChannels(this);
            showSettingsScreen();
        });
        soundActions.addView(clear, topMargin(dp(8)));
        card.addView(soundActions, topMargin(dp(10)));

        TextView outputTitle = text("Audio das mensagens de voz", 16, primary(), Typeface.BOLD);
        card.addView(outputTitle, topMargin(dp(20)));
        TextView outputHint = text(settingsStore.playVoiceOnPhone()
                ? "Tocando no aparelho."
                : "Usando Bluetooth ou a rota padrao do sistema.", 13, secondary(), Typeface.NORMAL);
        card.addView(outputHint, topMargin(dp(5)));

        LinearLayout outputActions = horizontal();
        boolean phoneOutput = settingsStore.playVoiceOnPhone();
        Button phone = pillButton("Aparelho", phoneOutput ? "#16A34A" : surfaceAlt(), phoneOutput ? "#FFFFFF" : primary());
        phone.setOnClickListener(v -> {
            settingsStore.setVoiceOutput(AppSettingsStore.VOICE_OUTPUT_PHONE);
            showSettingsScreen();
        });
        outputActions.addView(phone, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button bluetooth = pillButton("Bluetooth", phoneOutput ? surfaceAlt() : "#16A34A", phoneOutput ? primary() : "#FFFFFF");
        bluetooth.setOnClickListener(v -> {
            settingsStore.setVoiceOutput(AppSettingsStore.VOICE_OUTPUT_BLUETOOTH);
            showSettingsScreen();
        });
        LinearLayout.LayoutParams bluetoothParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        bluetoothParams.setMargins(dp(8), 0, 0, 0);
        outputActions.addView(bluetooth, bluetoothParams);
        card.addView(outputActions, topMargin(dp(10)));

        root.addView(card, topMargin(dp(18)));

        setContentView(scrollView);
        requestInsets(root);
    }

    private void pickNotificationSound() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Som de notificacao");
        String currentSound = settingsStore.notificationSoundUri();
        if (!currentSound.isEmpty()) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(currentSound));
        }
        try {
            startActivityForResult(intent, REQUEST_PICK_NOTIFICATION_SOUND);
        } catch (Exception ex) {
            pickNotificationSoundFile();
        }
    }

    private void pickNotificationSoundFile() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_NOTIFICATION_SOUND_FILE);
        } catch (Exception ex) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("audio/*");
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(fallback, REQUEST_PICK_NOTIFICATION_SOUND_FILE);
            } catch (Exception ignored) {
                Toast.makeText(this, "Nao foi possivel abrir os sons do aparelho.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private String displayNameForSoundUri(Uri uri) {
        try {
            Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone != null) {
                String title = ringtone.getTitle(this);
                if (title != null && !title.trim().isEmpty()) {
                    return title.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return displayNameForUri(uri);
    }

    private String displayNameForUri(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (name != null && !name.trim().isEmpty()) {
                        return name.trim();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String segment = uri.getLastPathSegment();
        return segment == null || segment.trim().isEmpty() ? "Som escolhido" : segment;
    }

    private void showContactActionPopup(View anchor, String address) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        PopupMenu menu = new PopupMenu(this, anchor);
        boolean muted = profileStore.isMuted(address);
        boolean blocked = profileStore.isBlocked(address);
        menu.getMenu().add(0, 1, 0, "Apagar conversa");
        menu.getMenu().add(0, 2, 1, muted ? "Ativar notificacoes" : "Silenciar contato");
        menu.getMenu().add(0, 3, 2, blocked ? "Desbloquear contato" : "Bloquear contato");
        menu.getMenu().add(0, 4, 3, "Remover contato e pareamento");
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                confirmDeleteConversation(address);
                return true;
            }
            if (item.getItemId() == 2) {
                profileStore.setMuted(address, !muted);
                Toast.makeText(this, muted ? "Notificacoes ativadas." : "Contato silenciado.", Toast.LENGTH_SHORT).show();
                renderContactList();
                return true;
            }
            if (item.getItemId() == 3) {
                profileStore.setBlocked(address, !blocked);
                if (!blocked && address.equals(currentRemoteAddress)) {
                    btChatManager.disconnectCurrent();
                }
                Toast.makeText(this, blocked ? "Contato desbloqueado." : "Contato bloqueado.", Toast.LENGTH_SHORT).show();
                renderContactList();
                return true;
            }
            if (item.getItemId() == 4) {
                confirmRemoveContact(address);
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void confirmRemoveContact(String address) {
        UserProfile profile = profileStore.loadContact(address);
        String name = profile.isComplete() ? profile.getDisplayName() : "este contato";
        new AlertDialog.Builder(this)
                .setTitle("Remover contato?")
                .setMessage("Vou remover " + name + " deste aparelho, apagar a conversa e tentar desfazer o pareamento Bluetooth no Android.")
                .setPositiveButton("Remover", (dialog, which) -> removeContactCompletely(address))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void removeContactCompletely(String address) {
        boolean unpaired = btChatManager.unpair(address);
        profileStore.removeContact(address);
        messageStore.deleteConversation(address);
        discoveredDevices.remove(address);
        onlineAddresses.remove(address);
        contactPresence.remove(address);
        remoteTypingUntil.remove(address);
        if (address.equals(currentRemoteAddress)) {
            currentRemoteAddress = "";
            currentRemoteProfile = UserProfile.empty();
            currentFingerprint = "";
        }
        Toast.makeText(this, unpaired ? "Contato removido e pareamento encerrado." : "Contato removido. Se ainda aparecer no Android, remova o pareamento nas configuracoes Bluetooth.", Toast.LENGTH_LONG).show();
        showInitialScreen();
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
        actions.add("Copiar");
        actions.add("Compartilhar");
        actions.add("Remover");
        actions.add("Responder");
        new AlertDialog.Builder(this)
                .setTitle("Mensagem selecionada")
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if ("Copiar".equals(action)) {
                        if (message.body != null && !message.body.trim().isEmpty()) {
                            copyMessageText(message.body);
                        } else {
                            Toast.makeText(this, "Esta mensagem nao tem texto para copiar.", Toast.LENGTH_SHORT).show();
                        }
                    } else if ("Compartilhar".equals(action)) {
                        showInternalShareChooser(message);
                    } else if ("Remover".equals(action)) {
                        showRemoveMessageDialog(message);
                    } else if ("Responder".equals(action)) {
                        beginReplyToMessage(message.id);
                    }
                })
                .show();
    }

    private void showRemoveMessageDialog(MessageStore.ChatMessage message) {
        new AlertDialog.Builder(this)
                .setTitle("Remover mensagem")
                .setItems(new String[]{"Apagar para todos", "Apagar so para mim", "Cancelar"}, (dialog, which) -> {
                    if (which == 0) {
                        deleteMessageForEveryone(message.id);
                    } else if (which == 1) {
                        deleteMessageForMe(message.id);
                    }
                })
                .show();
    }

    private void deleteMessageForMe(String id) {
        messageStore.deleteMessage(currentRemoteAddress, id);
        renderChatHistory(false);
        if ("home".equals(currentScreen)) {
            renderContactList();
        }
    }

    private void deleteMessageForEveryone(String id) {
        messageStore.deleteMessage(currentRemoteAddress, id);
        renderChatHistory(false);
        sendOrQueueDelete(currentRemoteAddress, id);
    }

    private void beginReplyToMessage(String id) {
        MessageStore.ChatMessage message = messageStore.findMessage(currentRemoteAddress, id);
        if (message == null) {
            return;
        }
        pendingReplyAddress = currentRemoteAddress;
        pendingReplyId = id;
        pendingReplyPreview = messagePreview(message);
        showChatScreen(currentRemoteProfile, currentFingerprint);
        if (messageInput != null) {
            messageInput.requestFocus();
            messageInput.postDelayed(() -> {
                InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (manager != null) {
                    manager.showSoftInput(messageInput, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 160);
        }
    }

    private void copyMessageText(String body) {
        copyToClipboard(body, true);
    }

    private void copyToClipboard(String body, boolean showToast) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("nBTChat", body == null ? "" : body));
            if (showToast) {
                Toast.makeText(this, "Texto copiado.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String messagePreview(MessageStore.ChatMessage message) {
        if (message == null) {
            return "";
        }
        if (MessageStore.KIND_IMAGE.equals(message.kind) || MessageStore.KIND_GIF.equals(message.kind)) {
            return message.mine ? "Voce enviou uma imagem" : "Imagem";
        }
        if (MessageStore.KIND_VOICE.equals(message.kind)) {
            return message.mine ? "Voce enviou uma mensagem de voz" : "Mensagem de voz";
        }
        if (MessageStore.KIND_TABLE_100.equals(message.kind)) {
            return message.mine ? "Voce enviou uma tabela 100" : "Tabela 100";
        }
        String body = message.body == null ? "" : message.body.trim();
        if (body.length() > 80) {
            return body.substring(0, 77) + "...";
        }
        return body;
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
                .setPositiveButton("Baixar APK", (dialog, which) -> openExternalLink(Uri.parse(updateApkUrl)))
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
                    boolean critical = json.optBoolean("critical", false);
                    int currentCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                    runOnUiThread(() -> {
                        updateAvailable = latestCode > currentCode;
                        updateVersionName = latestName;
                        updatePageUrl = pageUrl;
                        updateApkUrl = apkUrl;
                        if (updateAvailable && critical && settingsStore.shouldNotifyCriticalUpdate(latestName)) {
                            NotificationHelper.showCriticalUpdateNotification(this, latestName, updateApkUrl);
                        }
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
        } else if ("settings".equals(currentScreen)) {
            showSettingsScreen();
        } else if ("updates".equals(currentScreen)) {
            showUpdatesScreen();
        } else if ("store".equals(currentScreen)) {
            showStoreScreen();
        } else if ("store_config".equals(currentScreen)) {
            showTable100ConfigScreen();
        } else if ("table100_play".equals(currentScreen)) {
            refreshTable100PlayScreen();
        } else {
            showInitialScreen();
        }
    }

    private void pickPhoto() {
        startImagePicker(REQUEST_PICK_PHOTO);
    }

    private void startImagePicker(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, requestCode);
        } catch (Exception ex) {
            Intent fallback = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            fallback.setType("image/*");
            fallback.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(fallback, requestCode);
            } catch (Exception ignored) {
                Toast.makeText(this, "Nao foi possivel abrir a galeria.", Toast.LENGTH_LONG).show();
            }
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

    private MediaPayload mediaPayloadFromUri(Uri uri) {
        if (uri == null) {
            return null;
        }
        if (isGifUri(uri)) {
            String gifBase64 = uriToBase64(uri, MAX_GIF_BYTES);
            if (gifBase64.isEmpty()) {
                Toast.makeText(this, "Este GIF e grande demais para enviar por Bluetooth.", Toast.LENGTH_LONG).show();
                return null;
            }
            return new MediaPayload(MessageStore.KIND_GIF, "GIF", gifBase64);
        }
        String imageBase64 = compressImage(uri);
        if (imageBase64.isEmpty()) {
            return null;
        }
        return new MediaPayload(MessageStore.KIND_IMAGE, "", imageBase64);
    }

    private boolean isGifUri(Uri uri) {
        try {
            String type = getContentResolver().getType(uri);
            if (type != null && type.toLowerCase(Locale.ROOT).contains("gif")) {
                return true;
            }
        } catch (Exception ignored) {
        }
        String name = displayNameForUri(uri).toLowerCase(Locale.ROOT);
        return name.endsWith(".gif");
    }

    private String uriToBase64(Uri uri, int maxBytes) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                return "";
            }
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = inputStream.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    return "";
                }
                outputStream.write(buffer, 0, read);
            }
            return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) {
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

    private void applyMediaToImageView(ImageView imageView, String kind, String base64) {
        Drawable drawable = mediaDrawable(kind, base64);
        if (drawable != null) {
            imageView.setImageDrawable(drawable);
            startAnimatedDrawable(drawable);
        }
    }

    private Drawable mediaDrawable(String kind, String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            if (MessageStore.KIND_GIF.equals(kind) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Drawable drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)));
                return drawable;
            }
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            return bitmap == null ? null : new BitmapDrawable(getResources(), bitmap);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void startAnimatedDrawable(Drawable drawable) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable instanceof AnimatedImageDrawable) {
            ((AnimatedImageDrawable) drawable).start();
        }
    }

    private FrameLayout avatarStatusFrame(UserProfile profile, String presence, int size, int radius, int borderWidth, boolean badge, View.OnClickListener clickListener) {
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(borderWidth, borderWidth, borderWidth, borderWidth);
        frame.setBackground(roundedStroke(surface(), radius, presenceColor(presence), borderWidth));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            frame.setClipToOutline(true);
        }
        ImageView avatar = new ImageView(this);
        applyAvatar(avatar, profile);
        avatar.setBackground(rounded(surfaceAlt(), Math.max(dp(8), radius - borderWidth), surfaceAlt()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            avatar.setClipToOutline(true);
        }
        if (clickListener != null) {
            avatar.setOnClickListener(clickListener);
            frame.setOnClickListener(clickListener);
        }
        frame.addView(avatar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        if (badge) {
            ImageView status = new ImageView(this);
            status.setTag("presence");
            status.setImageResource(presenceDrawable(presence));
            status.setBackground(rounded("#FFFFFF", dp(11), "#FFFFFF"));
            status.setContentDescription(presenceLabel(presence));
            FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.RIGHT | Gravity.BOTTOM);
            statusParams.setMargins(0, 0, 0, 0);
            frame.addView(status, statusParams);
        }
        frame.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return frame;
    }

    private void applyAvatar(ImageView imageView, UserProfile profile) {
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setBackground(rounded(surfaceAlt(), dp(18), surfaceAlt()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            imageView.setClipToOutline(true);
        }
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

    private final class GifEditText extends EditText {
        GifEditText(Context context) {
            super(context);
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            InputConnection base = super.onCreateInputConnection(outAttrs);
            outAttrs.contentMimeTypes = new String[]{"image/gif", "image/*"};
            if (base == null) {
                return null;
            }
            return new InputConnectionWrapper(base, false) {
                @Override
                public boolean commitContent(InputContentInfo inputContentInfo, int flags, Bundle opts) {
                    if (inputContentInfo == null || inputContentInfo.getContentUri() == null) {
                        return false;
                    }
                    try {
                        if ((flags & InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                            inputContentInfo.requestPermission();
                        }
                    } catch (Exception ignored) {
                    }
                    return handleComposerContentUri(inputContentInfo.getContentUri());
                }
            };
        }
    }

    private static final class MediaPayload {
        final String kind;
        final String body;
        final String mediaBase64;

        MediaPayload(String kind, String body, String mediaBase64) {
            this.kind = kind == null ? MessageStore.KIND_IMAGE : kind;
            this.body = body == null ? "" : body;
            this.mediaBase64 = mediaBase64 == null ? "" : mediaBase64;
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
        final String replyToId;
        final String replyPreview;

        PendingOutgoing(String address, String id, String kind, String body, String mediaBase64, long durationMs, long sentAt, String replyToId, String replyPreview) {
            this.address = address;
            this.id = id;
            this.kind = kind;
            this.body = body;
            this.mediaBase64 = mediaBase64;
            this.durationMs = durationMs;
            this.sentAt = sentAt;
            this.replyToId = replyToId == null ? "" : replyToId;
            this.replyPreview = replyPreview == null ? "" : replyPreview;
        }
    }

    private static final class PendingDelete {
        final String address;
        final String id;

        PendingDelete(String address, String id) {
            this.address = address;
            this.id = id;
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

    private EditText richMessageInput(String hint) {
        GifEditText editText = new GifEditText(this);
        editText.setHint(hint);
        editText.setTextColor(color(primary()));
        editText.setHintTextColor(color(secondary()));
        editText.setTextSize(16);
        editText.setSingleLine(false);
        editText.setPadding(dp(12), dp(8), dp(8), dp(8));
        editText.setBackgroundColor(Color.TRANSPARENT);
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

    private void addTopActions(LinearLayout top) {
        if ("home".equals(currentScreen)) {
            LinearLayout.LayoutParams presenceParams = new LinearLayout.LayoutParams(dp(42), dp(42));
            presenceParams.setMargins(dp(8), 0, 0, 0);
            top.addView(presenceButton(), presenceParams);
        }
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        qrParams.setMargins(dp(8), 0, dp(8), 0);
        top.addView(iconButton(R.drawable.ic_qr_download_24, "QR para baixar o app", dp(42), v -> showDownloadQrDialog()), qrParams);
        top.addView(menuButton());
    }

    private ImageButton presenceButton() {
        String presence = settingsStore.userPresence();
        ImageButton button = iconButton(presenceDrawable(presence), "Status: " + presenceLabel(presence), dp(42), v -> showPresenceDialog());
        button.setColorFilter(null);
        button.setBackground(rounded(surface(), dp(22), presenceColor(presence)));
        return button;
    }

    private void showPresenceDialog() {
        String[] labels = {"Online", "Ocupado", "Invisivel"};
        String[] values = {
                AppSettingsStore.PRESENCE_ONLINE,
                AppSettingsStore.PRESENCE_BUSY,
                AppSettingsStore.PRESENCE_INVISIBLE
        };
        new AlertDialog.Builder(this)
                .setTitle("Seu status")
                .setItems(labels, (dialog, which) -> {
                    settingsStore.setUserPresence(values[which]);
                    btChatManager.sendPresence(presenceForPeer(values[which]));
                    if ("home".equals(currentScreen)) {
                        showHomeScreen();
                    }
                })
                .show();
    }

    private String presenceForPeer(String presence) {
        return AppSettingsStore.PRESENCE_INVISIBLE.equals(presence)
                ? "offline"
                : presence;
    }

    private int presenceDrawable(String presence) {
        if (AppSettingsStore.PRESENCE_BUSY.equals(presence) || "busy".equals(presence)) {
            return R.drawable.ic_status_busy_24;
        }
        if (AppSettingsStore.PRESENCE_ONLINE.equals(presence) || "online".equals(presence)) {
            return R.drawable.ic_status_online_24;
        }
        return R.drawable.ic_status_offline_24;
    }

    private String presenceLabel(String presence) {
        if (AppSettingsStore.PRESENCE_BUSY.equals(presence) || "busy".equals(presence)) {
            return "Ocupado";
        }
        if (AppSettingsStore.PRESENCE_ONLINE.equals(presence) || "online".equals(presence)) {
            return "Online";
        }
        return "Invisivel";
    }

    private String presenceColor(String presence) {
        if (AppSettingsStore.PRESENCE_BUSY.equals(presence) || "busy".equals(presence)) {
            return "#DC2626";
        }
        if (AppSettingsStore.PRESENCE_ONLINE.equals(presence) || "online".equals(presence)) {
            return "#16A34A";
        }
        return "#9CA3AF";
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
        return roundedStroke(fill, radius, stroke, dp(1));
    }

    private GradientDrawable roundedStroke(String fill, int radius, String stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color(fill));
        drawable.setCornerRadius(radius);
        drawable.setStroke(strokeWidth, color(stroke));
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
