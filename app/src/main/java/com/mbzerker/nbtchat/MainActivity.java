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
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
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
import android.provider.Settings;
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
import android.view.animation.LinearInterpolator;
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

import org.json.JSONException;
import org.json.JSONArray;
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
import java.nio.charset.StandardCharsets;
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
    private static final int REQUEST_RESTORE_BACKUP = 110;
    private static final int REQUEST_PROFILE_CAMERA = 111;
    private static final int REQUEST_VOICE_RECORD = 112;
    private static final int REQUEST_CHAT_CAMERA_PERMISSION = 113;
    private static final int TERMS_VERSION = 1;
    private static final int MAX_GIF_BYTES = 640 * 1024;
    private static final int PROFILE_IMAGE_MAX_SIDE = 256;
    private static final int CHAT_IMAGE_MAX_SIDE = 960;
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
    private static final long PENDING_WAKE_DELAY_MS = 1800L;
    private static final long PENDING_REPAIR_DELAY_MS = 30_000L;
    private static final long WAKE_THROTTLE_MS = 30_000L;
    private static final long PAIR_REPAIR_THROTTLE_MS = 5L * 60L * 1000L;
    private static final long UPDATE_CHECK_INTERVAL_MS = 60_000L;
    private static final long SCANNER_DISCOVERY_BURST_MS = 25_000L;
    private static final long SCANNER_DISCOVERY_REST_MS = 90_000L;
    private static final long CONNECT_BACKOFF_MS = 15_000L;
    private static final long OWNED_CARTELA_SYNC_INTERVAL_MS = 60_000L;

    private final Map<String, BtChatManager.DeviceCandidate> discoveredDevices = new LinkedHashMap<>();
    private final Map<String, TextView> receiptViews = new LinkedHashMap<>();
    private final Map<String, VoiceControls> voiceControls = new LinkedHashMap<>();
    private final Set<String> renderedMessageIds = new HashSet<>();
    private final List<PendingOutgoing> pendingOutgoing = new ArrayList<>();
    private final List<PendingDelete> pendingDeletes = new ArrayList<>();
    private final Map<String, Long> lastWakeAttemptAt = new HashMap<>();
    private final Map<String, Long> lastPairRepairAt = new HashMap<>();
    private final Map<String, Long> lastCartelaSyncAt = new HashMap<>();
    private final Map<String, Long> lastConnectAttemptAt = new HashMap<>();
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private ProfileStore profileStore;
    private IdentityStore identityStore;
    private MessageStore messageStore;
    private GadgetStore gadgetStore;
    private StorePaymentClient storePaymentClient;
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
    private Uri pendingDeepLinkUri;
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
    private boolean cartelaSyncInProgress;
    private boolean cartelaOnlineSyncInProgress;
    private boolean bluetoothEnablePromptShown;
    private boolean updateAvailable;
    private String updateVersionName = "";
    private String updatePageUrl = DOWNLOAD_PAGE_URL;
    private String updateApkUrl = "https://raw.githubusercontent.com/MBZerker/nBTChat/main/docs/nBTChat.apk";
    private String updateChangelog = "";
    private String updateOrigin = "GitHub Pages oficial do nBTChat";
    private MediaPlayer playingVoicePlayer;
    private File playingVoiceFile;
    private String playingVoiceId = "";
    private VoiceControls playingVoiceControls;
    private Runnable voiceTicker;
    private Runnable updateCheckRunnable;
    private Runnable table100AutoSyncRunnable;
    private Runnable scannerDiscoveryRunnable;
    private long lastOwnedCartelaSyncAt;
    private long lastDiscoverableRequestAt;
    private final Set<String> onlineAddresses = new HashSet<>();
    private final Map<String, String> contactPresence = new HashMap<>();
    private final Map<String, Long> remoteTypingUntil = new HashMap<>();
    private final List<CartelaPurchaseLine> cartelaPurchaseLines = new ArrayList<>();
    private boolean localTypingSent;
    private long lastTypingSentAt;
    private Runnable typingStopRunnable;
    private boolean chatAvatarSpinning;

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
        storePaymentClient = new StorePaymentClient();
        themeStore = new ThemeStore(this);
        settingsStore = new AppSettingsStore(this);
        darkMode = themeStore.isDarkMode();
        btChatManager = new BtChatManager(this, this);
        NotificationHelper.ensureChannels(this);
        NotificationHelper.cancelBackgroundNotification(this);
        try {
            stopService(new Intent(this, BluetoothForegroundService.class));
        } catch (Exception ignored) {
        }
        registerMessageReceiver();
        applySystemBars();
        startPeriodicUpdateChecks(true);

        if (!settingsStore.termsAccepted(TERMS_VERSION) && !profileStore.hasLocalProfile()) {
            showTermsScreen();
            return;
        } else if (!settingsStore.termsAccepted(TERMS_VERSION) && profileStore.hasLocalProfile()) {
            settingsStore.setTermsAcceptedVersion(TERMS_VERSION);
            showInitialScreen();
        } else if (profileStore.hasLocalProfile()) {
            showInitialScreen();
        } else {
            showProfileScreen();
        }
        tryStartBluetooth();
        handleDeepLinkIntent(getIntent());
        openChatFromIntent(getIntent());
        handleSharedImageIntent(getIntent());
    }

    private void showTermsScreen() {
        currentScreen = "terms";
        messageList = null;

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = vertical();
        root.setBackgroundColor(color(background()));
        applyRootInsets(root, dp(20), dp(16), dp(20), dp(18));
        scrollView.addView(root, matchWrap());

        TextView title = text("Termos de uso", 27, primary(), Typeface.BOLD);
        root.addView(title);
        TextView intro = text("Leia antes de usar o nBTChat.", 14, secondary(), Typeface.NORMAL);
        root.addView(intro, topMargin(dp(6)));

        TextView body = text(termsOfUseText(), 14, primary(), Typeface.NORMAL);
        body.setLineSpacing(dp(3), 1f);
        body.setPadding(dp(14), dp(14), dp(14), dp(14));
        body.setBackground(rounded(surface(), dp(14), border()));
        root.addView(body, topMargin(dp(16)));

        Button accept = pillButton("Aceitar e continuar", "#16A34A", "#FFFFFF");
        accept.setOnClickListener(v -> {
            settingsStore.setTermsAcceptedVersion(TERMS_VERSION);
            if (profileStore.hasLocalProfile()) {
                showInitialScreen();
            } else {
                showProfileScreen();
            }
            tryStartBluetooth();
        });
        root.addView(accept, topMargin(dp(16)));

        Button decline = pillButton("Recusar e desinstalar", surfaceAlt(), primary());
        decline.setOnClickListener(v -> {
            Toast.makeText(this, "Abrindo a tela de desinstalacao do Android.", Toast.LENGTH_LONG).show();
            Intent uninstall = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName()));
            startActivity(uninstall);
            finish();
        });
        root.addView(decline, topMargin(dp(8)));

        setContentView(scrollView);
        requestInsets(root);
    }

    private String termsOfUseText() {
        return "O nBTChat é um aplicativo livre de comunicação local por Bluetooth. Ele foi criado para conversas privadas entre pessoas próximas e para recursos oficiais usados dentro do próprio app.\n\n"
                + "O app respeita as leis brasileiras. Você se compromete a não usar o nBTChat para fraude, abuso, assédio, jogos de azar, sorteios, distribuição de prêmios, promoções comerciais ou qualquer atividade que exija autorização legal sem cumprir essa exigência.\n\n"
                + "As conversas não são salvas em servidor do nBTChat. Elas ficam no aparelho. O envio, o histórico local, as mídias, o perfil, os contatos, backups e configurações usam proteções técnicas adequadas ao funcionamento do app.\n\n"
                + "A loja pode armazenar dados mínimos para liberar compras e recuperação, como identificador do aparelho, produto, validade, nome, CPF protegido, últimos dígitos do CPF, código de recuperação e estado dos itens oficiais. Pagamentos são processados por meio da instituição de pagamento configurada no momento da compra.\n\n"
                + "Os itens oficiais da loja são ferramentas de organização e interação. O organizador é responsável pela finalidade do uso, pelos textos configurados, pelos contatos exibidos e pela conformidade com as regras aplicáveis.\n\n"
                + "Ao continuar, você entende que o Bluetooth depende do aparelho, permissão do sistema e distância física, e que backups, restaurações e sincronização pela internet podem depender de serviços externos.";
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (profileStore != null && profileStore.hasLocalProfile() && btChatManager != null && btChatManager.isBluetoothEnabled()) {
            btChatManager.startListening();
        }
    }

    @Override
    protected void onStop() {
        if (btChatManager != null && !isChangingConfigurations()) {
            stopScannerDiscoveryCycle();
            btChatManager.stopDiscovery();
            btChatManager.stop();
        }
        stopPeriodicUpdateChecks();
        stopTable100AutoSync();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sendLocalTyping(false);
        stopVoicePlayback(false);
        stopTable100AutoSync();
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
        handleDeepLinkIntent(intent);
        handleSharedImageIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (profileStore != null && profileStore.hasLocalProfile()) {
            tryStartBluetooth();
        }
        startPeriodicUpdateChecks(false);
        syncOwnedCartelaIfUseful();
    }

    @Override
    public void onBackPressed() {
        if ("chat".equals(currentScreen)) {
            leaveChatToHome();
        } else if ("scanner".equals(currentScreen) || "settings".equals(currentScreen) || "updates".equals(currentScreen) || "share_targets".equals(currentScreen)) {
            showHomeScreen();
        } else if ("store_config".equals(currentScreen) || "table100_play".equals(currentScreen) || "cartela_purchase".equals(currentScreen)) {
            showStoreScreen();
        } else if ("store".equals(currentScreen)) {
            showUpdatesScreen();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            tryStartBluetooth();
            if ("scanner".equals(currentScreen) && btChatManager != null && btChatManager.isBluetoothEnabled()) {
                requestDiscoverableForScanner();
                startScannerDiscoveryCycle();
            }
        } else if (requestCode == REQUEST_PROFILE_CAMERA
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            capturePhoto();
        } else if (requestCode == REQUEST_CHAT_CAMERA_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            captureChatImage();
        } else if (requestCode == REQUEST_VOICE_RECORD
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecording();
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
                updateEditingPhoto(compressProfileImage(uri));
            }
        } else if (requestCode == REQUEST_CAPTURE_PHOTO && resultCode == RESULT_OK && data != null) {
            if (pendingCameraUri != null) {
                String photo = compressProfileImage(pendingCameraUri);
                deleteCameraUri(pendingCameraUri);
                pendingCameraUri = null;
                updateEditingPhoto(photo);
            } else {
                Object photo = data.getExtras() == null ? null : data.getExtras().get("data");
                if (photo instanceof Bitmap) {
                    updateEditingPhoto(compressBitmap((Bitmap) photo, PROFILE_IMAGE_MAX_SIDE, 84));
                }
            }
        } else if (requestCode == REQUEST_CAPTURE_PHOTO && resultCode == RESULT_OK) {
            if (pendingCameraUri != null) {
                String photo = compressProfileImage(pendingCameraUri);
                deleteCameraUri(pendingCameraUri);
                pendingCameraUri = null;
                updateEditingPhoto(photo);
            }
        } else if (requestCode == REQUEST_CAPTURE_PHOTO) {
            if (pendingCameraUri != null) {
                deleteCameraUri(pendingCameraUri);
            }
            pendingCameraUri = null;
        } else if (requestCode == REQUEST_PICK_CHAT_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                sendMediaFromUri(uri);
            }
        } else if (requestCode == REQUEST_CAPTURE_CHAT_IMAGE && resultCode == RESULT_OK) {
            if (pendingCameraUri != null) {
                String image = compressChatImage(pendingCameraUri);
                deleteCameraUri(pendingCameraUri);
                pendingCameraUri = null;
                sendImageMessage(image);
            }
        } else if (requestCode == REQUEST_CAPTURE_CHAT_IMAGE) {
            if (pendingCameraUri != null) {
                deleteCameraUri(pendingCameraUri);
            }
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
        } else if (requestCode == REQUEST_RESTORE_BACKUP && resultCode == RESULT_OK && data != null && data.getData() != null) {
            restoreBackupFromUri(data.getData());
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
        btChatManager.startListening();
    }

    private void notifyProfileUpdated() {
        if (!profileStore.hasLocalProfile()) {
            return;
        }
        btChatManager.sendProfileUpdate();
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

    private boolean handleDeepLinkIntent(Intent intent) {
        if (intent == null || intent.getData() == null) {
            return false;
        }
        Uri uri = intent.getData();
        boolean nbtchatShare = "nbtchat".equalsIgnoreCase(uri.getScheme()) && "share".equalsIgnoreCase(uri.getHost());
        boolean webShare = "https".equalsIgnoreCase(uri.getScheme())
                && "mbzerker.github.io".equalsIgnoreCase(uri.getHost())
                && uri.getPath() != null
                && uri.getPath().startsWith("/nBTChat/l");
        boolean shortShare = "https".equalsIgnoreCase(uri.getScheme())
                && "nbtchat-store.nectof.workers.dev".equalsIgnoreCase(uri.getHost())
                && uri.getPath() != null
                && uri.getPath().startsWith("/s/");
        if (!nbtchatShare && !webShare && !shortShare) {
            return false;
        }
        intent.setData(null);
        if (!profileStore.hasLocalProfile()) {
            pendingDeepLinkUri = uri;
            showProfileScreen();
            return true;
        }
        return handleStoreShareDeepLink(uri);
    }

    private boolean handleStoreShareDeepLink(Uri uri) {
        try {
            String code = uri.getQueryParameter("c");
            if ((code == null || code.trim().isEmpty())
                    && "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getPath() != null
                    && uri.getPath().startsWith("/s/")) {
                code = uri.getLastPathSegment();
            }
            if (code != null && !code.trim().isEmpty()) {
                String base = uri.getQueryParameter("u");
                if ((base == null || base.trim().isEmpty()) && "https".equalsIgnoreCase(uri.getScheme())) {
                    base = uri.getScheme() + "://" + uri.getHost();
                }
                fetchAndHandleStoreSharePayload(code, base);
                return true;
            }
            JSONObject payload = decodeStoreSharePayload(uri.getQueryParameter("p"));
            if (payload == null) {
                Toast.makeText(this, "Link nBTChat invalido.", Toast.LENGTH_LONG).show();
                return true;
            }
            return handleDecodedStoreSharePayload(payload);
        } catch (Exception ex) {
            Toast.makeText(this, "Nao foi possivel abrir este link nBTChat.", Toast.LENGTH_LONG).show();
            return true;
        }
    }

    private void fetchAndHandleStoreSharePayload(String code, String baseUrlOverride) {
        Toast.makeText(this, "Abrindo link nBTChat...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String encoded = storePaymentClient.getSharePayload(baseUrlOverride, code);
                JSONObject payload = decodeStoreSharePayload(encoded);
                runOnUiThread(() -> handleDecodedStoreSharePayload(payload));
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(this, "Nao foi possivel abrir este link nBTChat.", Toast.LENGTH_LONG).show());
            }
        }, "nBTChat-open-short-link").start();
    }

    private boolean handleDecodedStoreSharePayload(JSONObject payload) {
        try {
            if (payload == null) {
                Toast.makeText(this, "Link nBTChat invalido.", Toast.LENGTH_LONG).show();
                return true;
            }
            ContactCardPayload contact = ContactCardPayload.parse(payload.optString("contact", ""));
            if (contact == null || contact.address.trim().isEmpty()) {
                Toast.makeText(this, "Este link nao tem dados de contato validos.", Toast.LENGTH_LONG).show();
                return true;
            }
            String kind = payload.optString("kind", "");
            String body = payload.optString("body", "");
            if (isLocalContactPayload(contact)) {
                if (!kind.trim().isEmpty() && !body.trim().isEmpty()) {
                    showShareTargetScreen("Compartilhar item", "Nenhum contato nBTChat para receber este item.", new HashSet<>(),
                            address -> sharePayloadToContact(address, kind, body));
                    Toast.makeText(this, "Escolha um contato para compartilhar.", Toast.LENGTH_SHORT).show();
                    return true;
                }
                showHomeScreen();
                return true;
            }
            saveSharedContact(contact);
            if (!kind.trim().isEmpty() && !body.trim().isEmpty()) {
                String id = "link-" + Integer.toHexString((contact.address + kind + body).hashCode());
                messageStore.addMessage(contact.address, id, kind, body, "", 0L,
                        false, System.currentTimeMillis(), MessageStore.STATUS_DELIVERED, false);
                if (MessageStore.KIND_TABLE_100.equals(kind)) {
                    syncCartelaOnline(GadgetStore.Table100Payload.parse(body), false);
                }
            }
            UserProfile profile = profileStore.loadContact(contact.address);
            currentRemoteAddress = contact.address;
            currentRemoteProfile = profile.isComplete() ? profile : new UserProfile(safeName(contact.name, "Contato nBTChat"), "", UserProfile.GENDER_OTHER, "");
            currentFingerprint = profileStore.loadFingerprint(contact.address);
            showChatScreen(currentRemoteProfile, currentFingerprint);
            Toast.makeText(this, "Contato e item adicionados ao chat.", Toast.LENGTH_LONG).show();
            return true;
        } catch (Exception ex) {
            Toast.makeText(this, "Nao foi possivel abrir este link nBTChat.", Toast.LENGTH_LONG).show();
            return true;
        }
    }

    private boolean isLocalContactPayload(ContactCardPayload contact) {
        if (contact == null) {
            return false;
        }
        String localDeviceId = identityStore == null ? "" : identityStore.getDeviceId();
        if (!localDeviceId.trim().isEmpty() && localDeviceId.equals(contact.deviceId)) {
            return true;
        }
        String localAddress = btChatManager == null ? "" : btChatManager.localBluetoothAddress();
        return !localAddress.trim().isEmpty() && localAddress.equals(contact.address);
    }

    private JSONObject decodeStoreSharePayload(String encoded) throws JSONException {
        if (encoded == null || encoded.trim().isEmpty()) {
            return null;
        }
        byte[] raw = Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP);
        return new JSONObject(new String(raw, StandardCharsets.UTF_8));
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
            if (!bluetoothEnablePromptShown) {
                bluetoothEnablePromptShown = true;
                try {
                    startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT);
                } catch (SecurityException ex) {
                    showState("Libere a permissão Bluetooth nas configurações do app.");
                }
            }
            return;
        }
        bluetoothEnablePromptShown = false;
        startOnlineService();
        if ("scanner".equals(currentScreen) && discoveredDevices.isEmpty()) {
            startScannerDiscoveryCycle();
        }
    }

    private void showInitialScreen() {
        if (conversationCount() == 0) {
            showNearbyScannerScreen(true);
        } else {
            showHomeScreen();
        }
    }

    private void startScannerDiscoveryCycle() {
        stopScannerDiscoveryCycle();
        if (btChatManager == null || !"scanner".equals(currentScreen)) {
            return;
        }
        scannerDiscoveryRunnable = new Runnable() {
            @Override
            public void run() {
                Runnable cycle = this;
                if (!"scanner".equals(currentScreen) || btChatManager == null || !btChatManager.isBluetoothEnabled()) {
                    scannerDiscoveryRunnable = null;
                    return;
                }
                btChatManager.startNearbyDiscovery();
                uiHandler.postDelayed(() -> {
                    if (btChatManager != null) {
                        btChatManager.stopDiscovery();
                    }
                    if ("scanner".equals(currentScreen) && scannerDiscoveryRunnable == cycle) {
                        uiHandler.postDelayed(cycle, SCANNER_DISCOVERY_REST_MS);
                    }
                }, SCANNER_DISCOVERY_BURST_MS);
            }
        };
        scannerDiscoveryRunnable.run();
    }

    private void stopScannerDiscoveryCycle() {
        if (scannerDiscoveryRunnable != null) {
            uiHandler.removeCallbacks(scannerDiscoveryRunnable);
            scannerDiscoveryRunnable = null;
        }
    }

    private void showHomeScreen() {
        if (btChatManager != null) {
            stopScannerDiscoveryCycle();
            btChatManager.stopDiscovery();
        }
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

    }

    private void leaveChatToHome() {
        sendLocalTyping(false);
        hideKeyboard(messageInput);
        stopVoicePlayback(false);
        receiptViews.clear();
        voiceControls.clear();
        renderedMessageIds.clear();
        messageList = null;
        messageScroll = null;
        messageInput = null;
        showHomeScreen();
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
        stopScannerDiscoveryCycle();
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

        Map<String, UserProfile> contacts = profileStore.loadContacts();
        Map<String, MessageStore.ConversationInfo> conversations = messageStore.loadConversationInfo();
        Map<String, BtChatManager.DeviceCandidate> pairedCandidates = btChatManager.pairedCandidatesByAddress();
        LinkedHashMap<String, BtChatManager.DeviceCandidate> candidates = new LinkedHashMap<>();
        Set<String> addresses = new HashSet<>();
        for (String address : contacts.keySet()) {
            addresses.add(address);
            BtChatManager.DeviceCandidate candidate = pairedCandidates.get(address);
            if (candidate != null) {
                candidates.put(address, candidate);
            }
        }
        addresses.addAll(conversations.keySet());
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
            long leftAt = conversationInfo(conversations, left).lastAt;
            long rightAt = conversationInfo(conversations, right).lastAt;
            return Long.compare(rightAt, leftAt);
        });

        int rendered = 0;
        for (String address : orderedAddresses) {
            BtChatManager.DeviceCandidate candidate = candidates.get(address);
            UserProfile known = contacts.containsKey(address) ? contacts.get(address) : UserProfile.empty();
            MessageStore.ConversationInfo conversation = conversationInfo(conversations, address);
            if (!conversationMatches(address, candidate, known, conversation)) {
                continue;
            }
            contactList.addView(contactRow(address, candidate, known, conversation), topMargin(dp(8)));
            rendered++;
        }
        if (rendered == 0) {
            TextView empty = text("Nenhuma conversa encontrada.", 15, secondary(), Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            contactList.addView(empty, topMargin(dp(36)));
        }
    }

    private MessageStore.ConversationInfo conversationInfo(Map<String, MessageStore.ConversationInfo> conversations, String address) {
        MessageStore.ConversationInfo info = conversations.get(address);
        return info == null ? new MessageStore.ConversationInfo(address, "", 0L, 0) : info;
    }

    private boolean conversationMatches(String address, BtChatManager.DeviceCandidate candidate, UserProfile known, MessageStore.ConversationInfo conversation) {
        String query = searchable(conversationFilter);
        if (query.isEmpty()) {
            return true;
        }
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
            if (requestMissingPermissions()) {
                return;
            }
            requestDiscoverableForScanner();
            discoveredDevices.clear();
            renderNearbyDeviceList();
            startScannerDiscoveryCycle();
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
        root.postDelayed(() -> {
            if (!requestMissingPermissions()) {
                requestDiscoverableForScanner();
            }
        }, 250);

        if (autoStart) {
            discoveredDevices.clear();
            renderNearbyDeviceList();
            root.postDelayed(() -> {
                if (!requestMissingPermissions()) {
                    startScannerDiscoveryCycle();
                }
            }, 200);
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

    private View contactRow(String address, BtChatManager.DeviceCandidate candidate, UserProfile known, MessageStore.ConversationInfo conversation) {
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
        boolean typing = isRemoteTyping(address);
        String subtitle = contactSubtitle(address, title, known, conversation);
        TextView titleView = text(safeName(title, "Contato nBTChat"), 17, primary(), Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(titleView);
        TextView subtitleView = text(subtitle, 13, typing ? "#16A34A" : secondary(), Typeface.NORMAL);
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
            return "Digitando";
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
            if (pendingDeepLinkUri != null && handleStoreShareDeepLink(pendingDeepLinkUri)) {
                pendingDeepLinkUri = null;
            } else {
                showInitialScreen();
            }
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
        if (btChatManager != null) {
            stopScannerDiscoveryCycle();
            btChatManager.stopDiscovery();
        }
        currentScreen = "chat";
        currentRemoteProfile = profile == null ? UserProfile.empty() : profile;
        currentFingerprint = fingerprint == null ? "" : fingerprint;
        replyPreviewBar = null;
        chatAvatarSpinning = false;

        LinearLayout root = vertical();
        root.setBackgroundColor(color(chatBackground()));
        applyRootInsets(root, dp(12), dp(8), dp(12), dp(8));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconButton(R.drawable.ic_back_24, "Voltar", dp(42), v -> leaveChatToHome()));

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
        if (currentRemoteAddress != null && !currentRemoteAddress.isEmpty() && !btChatManager.canSendTo(currentRemoteAddress)) {
            connectForAddress(currentRemoteAddress);
            updateChatHeaderStatus();
        }

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
        addMessageBubble(id, body, true, MessageStore.KIND_TEXT, "", 0L, MessageStore.STATUS_PENDING, replyToId, replyPreview, sentAt, true);
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
        addMessageBubble(id, cleanBody, true, cleanKind, mediaBase64, 0L, MessageStore.STATUS_PENDING, replyToId, replyPreview, sentAt, true);
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
        showPendingSharedMediaChooser();
    }

    private void showPendingSharedMediaChooser() {
        if (pendingSharedMediaBase64.isEmpty()) {
            return;
        }
        showShareTargetScreen("Compartilhar imagem", "Nenhum contato nBTChat para receber a imagem.", new HashSet<>(), this::sendPendingSharedMediaTo);
    }

    private void showShareTargetScreen(String title, String emptyMessage, Set<String> excludedAddresses, ShareTargetAction action) {
        currentScreen = "share_targets";
        messageList = null;

        Set<String> excluded = excludedAddresses == null ? new HashSet<>() : excludedAddresses;
        LinearLayout root = vertical();
        root.setBackgroundColor(color(background()));
        applyRootInsets(root, dp(16), dp(10), dp(16), dp(12));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconButton(R.drawable.ic_back_24, "Voltar", dp(42), v -> showHomeScreen()));
        TextView titleView = text(title == null || title.trim().isEmpty() ? "Compartilhar com" : title, 27, primary(), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(12), 0, 0, 0);
        top.addView(titleView, titleParams);
        root.addView(top);

        TextView subtitle = text("Escolha um contato salvo para receber o compartilhamento.", 14, secondary(), Typeface.NORMAL);
        root.addView(subtitle, topMargin(dp(8)));

        ScrollView scrollView = new ScrollView(this);
        LinearLayout list = vertical();
        list.setPadding(0, dp(12), 0, dp(20));
        scrollView.addView(list);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        Map<String, UserProfile> contacts = profileStore.loadContacts();
        boolean any = false;
        for (Map.Entry<String, UserProfile> entry : contacts.entrySet()) {
            String address = entry.getKey();
            if (address == null || address.trim().isEmpty() || excluded.contains(address) || isLocalContactRecord(address)) {
                continue;
            }
            any = true;
            UserProfile profile = entry.getValue();
            list.addView(shareTargetRow(address, profile, action), topMargin(dp(8)));
        }

        if (!any) {
            TextView empty = text(emptyMessage == null ? "Nenhum contato disponivel." : emptyMessage, 15, secondary(), Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(34), dp(18), dp(34));
            empty.setBackground(rounded(surface(), dp(16), border()));
            list.addView(empty, topMargin(dp(12)));
        }

        setContentView(root);
        requestInsets(root);
    }

    private View shareTargetRow(String address, UserProfile profile, ShareTargetAction action) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(rounded(surface(), dp(16), border()));

        row.addView(avatarStatusFrame(profile, contactPresenceStatus(address), dp(54), dp(27), dp(3), false, null));

        LinearLayout texts = vertical();
        String name = profile != null && profile.isComplete() ? profile.getDisplayName() : "Contato nBTChat";
        texts.addView(text(safeName(name, "Contato"), 17, primary(), Typeface.BOLD));
        TextView hint = text("Enviar para esta conversa", 13, secondary(), Typeface.NORMAL);
        texts.addView(hint, topMargin(dp(2)));
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        textParams.setMargins(dp(12), 0, dp(10), 0);
        row.addView(texts, textParams);

        ImageView sendIcon = new ImageView(this);
        sendIcon.setImageResource(R.drawable.ic_send_24);
        sendIcon.setColorFilter(color("#16A34A"));
        row.addView(sendIcon, new LinearLayout.LayoutParams(dp(30), dp(30)));

        row.setOnClickListener(v -> {
            if (action != null) {
                action.send(address);
            }
        });
        return row;
    }

    private boolean isLocalContactRecord(String address) {
        String localAddress = btChatManager == null ? "" : btChatManager.localBluetoothAddress();
        if (!localAddress.trim().isEmpty() && localAddress.equals(address)) {
            return true;
        }
        if (address != null && address.startsWith("nbt-") && identityStore != null && address.equals("nbt-" + identityStore.getDeviceId())) {
            return true;
        }
        try {
            ProfileStore.ContactIdentity identity = profileStore.loadIdentity(address);
            String localDeviceId = identityStore == null ? "" : identityStore.getDeviceId();
            return identity != null && !localDeviceId.trim().isEmpty() && localDeviceId.equals(identity.deviceId);
        } catch (Exception ignored) {
            return false;
        }
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
        openSharedChat(address);
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
        addMessageBubble(id, "", true, MessageStore.KIND_VOICE, audioBase64, durationMs, MessageStore.STATUS_PENDING, replyToId, replyPreview, sentAt, true);
        sendOrQueueOutgoing(currentRemoteAddress, id, MessageStore.KIND_VOICE, "", audioBase64, durationMs, sentAt, replyToId, replyPreview);
        clearPendingReply();
    }

    private void sendTable100ToAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        if (!gadgetStore.hasTable100()) {
            Toast.makeText(this, "Compre a Cartela de eventos na loja para enviar.", Toast.LENGTH_LONG).show();
            showStoreScreen();
            return;
        }
        GadgetStore.Table100Payload payload = table100PayloadWithKnownLocks(gadgetStore.table100Payload());
        if (payload.copyText.trim().isEmpty()) {
            Toast.makeText(this, "Configure a Cartela de eventos antes de enviar.", Toast.LENGTH_LONG).show();
            showTable100ConfigScreen();
            return;
        }
        registerCartelaOnline(false);
        String body = payload.toMessageBody();
        long sentAt = System.currentTimeMillis();
        String id = messageStore.createId();
        messageStore.addMessage(address, id, MessageStore.KIND_TABLE_100, body, "", 0L, true, sentAt, MessageStore.STATUS_PENDING, false);
        sendOrQueueOutgoing(address, id, MessageStore.KIND_TABLE_100, body, "", 0L, sentAt);
        Toast.makeText(this, "Cartela de eventos enviada.", Toast.LENGTH_SHORT).show();
        openSharedChat(address);
    }

    private GadgetStore.Table100Payload table100PayloadWithKnownLocks(GadgetStore.Table100Payload payload) {
        if (payload == null) {
            return new GadgetStore.Table100Payload("", "", "", "");
        }
        boolean trustLocalLocks = table100IsOwner(payload) || lastCartelaSyncAt.containsKey(payload.tableId);
        List<Integer> lockedNumbers = trustLocalLocks ? new ArrayList<>() : new ArrayList<>(payload.lockedNumbers);
        for (Integer number : gadgetStore.lockedNumbers(payload.tableId)) {
            if (number != null && !lockedNumbers.contains(number)) {
                lockedNumbers.add(number);
            }
        }
        String ownerDeviceId = payload.ownerDeviceId.isEmpty() ? StoreDeviceId.get(this) : payload.ownerDeviceId;
        return new GadgetStore.Table100Payload(payload.tableId, payload.customTitle, payload.ownerMessage, payload.copyText, payload.ownerContact,
                ownerDeviceId, lockedNumbers, payload.allowReservations, payload.reservationHours);
    }

    private String table100BodyWithKnownLocks(String body) {
        return table100PayloadWithKnownLocks(GadgetStore.Table100Payload.parse(body)).toMessageBody();
    }

    private void showStoreScreen() {
        stopScannerDiscoveryCycle();
        syncOwnedCartelaIfUseful();
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
        FrameLayout card = new FrameLayout(this);
        card.setMinimumHeight(dp(230));
        card.setBackground(rounded(surface(), dp(12), border()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setClipToOutline(true);
        }
        ImageView backgroundImage = new ImageView(this);
        backgroundImage.setImageResource(R.drawable.cartela_eventos_bg);
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backgroundImage.setAlpha(darkMode ? 0.34f : 0.42f);
        card.addView(backgroundImage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(230)
        ));
        View overlay = new View(this);
        overlay.setBackgroundColor(color(darkMode ? "#AA101820" : "#DDF7F8F5"));
        card.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(230)
        ));

        LinearLayout item = vertical();
        item.setPadding(dp(14), dp(14), dp(14), dp(14));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_table_24);
        icon.setColorFilter(color("#16A34A"));
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        icon.setBackground(rounded(darkMode ? "#18372C" : "#DDF7E8", dp(18), "#16A34A"));
        header.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout texts = vertical();
        TextView name = text(GadgetStore.TABLE_100_TITLE, 19, primary(), Typeface.BOLD);
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
                    + "\nCopiar: " + gadgetStore.table100CopyText()
                    + "\nContato: " + (gadgetStore.table100OwnerContact().isEmpty() ? "sem dados" : gadgetStore.table100OwnerContact());
            TextView current = text(configured, 14, primary(), Typeface.NORMAL);
            current.setPadding(dp(12), dp(10), dp(12), dp(10));
            current.setBackground(rounded(surfaceAlt(), dp(12), border()));
            item.addView(current, topMargin(dp(8)));
            Button options = pillButton("Opcoes", "#16A34A", "#FFFFFF");
            options.setOnClickListener(v -> showTable100OptionsDialog());
            item.addView(options, topMargin(dp(12)));
            Button buyMore = pillButton("Comprar mais cartelas", surfaceAlt(), primary());
            buyMore.setOnClickListener(v -> startCartelaPurchase());
            item.addView(buyMore, topMargin(dp(8)));
            item.setOnClickListener(v -> showTable100OptionsDialog());
            card.setOnClickListener(v -> showTable100OptionsDialog());
        } else if (gadgetStore.hasPendingTable100Payment()) {
            boolean recoveryPending = isPendingCartelaRecovery();
            TextView pending = text(recoveryPending
                    ? "Recuperacao em andamento. Se nao foi concluida, cancele para comprar normalmente."
                    : "Pagamento em andamento. Ao concluir, volte ao app e toque em verificar.", 13, secondary(), Typeface.BOLD);
            item.addView(pending, topMargin(dp(14)));
            LinearLayout actions = horizontal();
            actions.setGravity(Gravity.CENTER_VERTICAL);
            Button resume = pillButton("Abrir pagina", surfaceAlt(), primary());
            resume.setOnClickListener(v -> openPendingCartelaPayment());
            actions.addView(resume, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            Button check = pillButton("Verificar", "#16A34A", "#FFFFFF");
            check.setOnClickListener(v -> syncCartelaEntitlement(true));
            LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            checkParams.setMargins(dp(8), 0, 0, 0);
            actions.addView(check, checkParams);
            item.addView(actions, topMargin(dp(12)));
            Button cancel = pillButton(recoveryPending ? "Cancelar recuperacao" : "Cancelar pagamento", surfaceAlt(), primary());
            cancel.setOnClickListener(v -> clearPendingCartelaPayment(true));
            item.addView(cancel, topMargin(dp(8)));
        } else {
            LinearLayout priceRow = horizontal();
            priceRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView price = text("R$ 2,49", 19, "#16A34A", Typeface.BOLD);
            priceRow.addView(price);
            TextView days = text("1 dia", 14, "#38BDF8", Typeface.BOLD);
            LinearLayout.LayoutParams daysParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            daysParams.setMargins(dp(8), 0, 0, 0);
            priceRow.addView(days, daysParams);
            item.addView(priceRow, topMargin(dp(14)));
            TextView footer = text(GadgetStore.TABLE_100_FOOTER, 12, secondary(), Typeface.NORMAL);
            footer.setLineSpacing(dp(2), 1f);
            item.addView(footer, topMargin(dp(8)));
            Button buy = pillButton("Comprar", "#16A34A", "#FFFFFF");
            buy.setOnClickListener(v -> startCartelaPurchase());
            item.addView(buy, topMargin(dp(12)));
            Button recover = pillButton("Recuperar compra", surfaceAlt(), primary());
            recover.setOnClickListener(v -> recoverCartelaPurchase());
            item.addView(recover, topMargin(dp(8)));
            item.setOnClickListener(v -> buy.performClick());
            card.setOnClickListener(v -> buy.performClick());
        }
        card.addView(item, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));
        return card;
    }

    private void startCartelaPurchase() {
        cartelaPurchaseLines.clear();
        showCartelaPurchaseScreen();
    }

    private void showCartelaPurchaseScreen() {
        currentScreen = "cartela_purchase";
        messageList = null;

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = vertical();
        root.setBackgroundColor(color(background()));
        applyRootInsets(root, dp(16), dp(10), dp(16), dp(18));
        scrollView.addView(root, matchWrap());

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconButton(R.drawable.ic_back_24, "Voltar", dp(42), v -> showStoreScreen()));
        TextView title = text("Comprar cartelas", 26, primary(), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(12), 0, 0, 0);
        top.addView(title, titleParams);
        addTopActions(top);
        root.addView(top);

        TextView loading = text("Carregando valores da loja...", 14, secondary(), Typeface.BOLD);
        root.addView(loading, topMargin(dp(18)));
        setContentView(scrollView);
        requestInsets(root);

        new Thread(() -> {
            StorePaymentClient.ProductConfig product;
            try {
                product = storePaymentClient.getCartelaProduct();
            } catch (Exception ex) {
                product = StorePaymentClient.ProductConfig.fromJson(null);
            }
            StorePaymentClient.ProductConfig finalProduct = product;
            runOnUiThread(() -> renderCartelaPurchaseScreen(finalProduct));
        }, "nBTChat-cartela-product").start();
    }

    private void renderCartelaPurchaseScreen(StorePaymentClient.ProductConfig product) {
        currentScreen = "cartela_purchase";
        if (cartelaPurchaseLines.isEmpty()) {
            cartelaPurchaseLines.add(new CartelaPurchaseLine(product.durationDays));
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = vertical();
        root.setBackgroundColor(color(background()));
        applyRootInsets(root, dp(16), dp(10), dp(16), dp(18));
        scrollView.addView(root, matchWrap());

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconButton(R.drawable.ic_back_24, "Voltar", dp(42), v -> showStoreScreen()));
        TextView title = text("Comprar cartelas", 26, primary(), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(12), 0, 0, 0);
        top.addView(title, titleParams);
        addTopActions(top);
        root.addView(top);

        TextView info = text(String.format(Locale.getDefault(), "Preço inicial: %s = %d dia%s", money(product.price), product.durationDays, product.durationDays == 1 ? "" : "s"),
                15, primary(), Typeface.BOLD);
        root.addView(info, topMargin(dp(16)));
        TextView formula = text(String.format(Locale.getDefault(), "Dias extras usam taxa diária %s e potencializador %.2f.", money(product.dailyFee), product.power),
                13, secondary(), Typeface.NORMAL);
        root.addView(formula, topMargin(dp(4)));
        formula.setVisibility(View.GONE);

        LinearLayout rows = vertical();
        root.addView(rows, topMargin(dp(14)));
        TextView total = text("", 22, "#16A34A", Typeface.BOLD);

        Runnable[] rerender = new Runnable[1];
        rerender[0] = () -> renderCartelaPurchaseScreen(product);
        for (int i = 0; i < cartelaPurchaseLines.size(); i++) {
            rows.addView(cartelaPurchaseRow(product, i, rerender[0]), topMargin(i == 0 ? 0 : dp(10)));
        }

        ImageButton add = iconButton(R.drawable.ic_add_24, "Adicionar cartela", dp(58), v -> {
            cartelaPurchaseLines.add(new CartelaPurchaseLine(product.durationDays));
            renderCartelaPurchaseScreen(product);
        });
        add.setBackground(rounded("#16A34A", dp(29), "#16A34A"));
        add.setColorFilter(color("#FFFFFF"));
        LinearLayout addRow = horizontal();
        addRow.setGravity(Gravity.CENTER);
        addRow.addView(add);
        root.addView(addRow, topMargin(dp(14)));

        double sum = 0;
        for (CartelaPurchaseLine line : cartelaPurchaseLines) {
            sum += cartelaLinePrice(product, line.days);
        }
        total.setText("Total: " + money(sum));
        root.addView(total, topMargin(dp(14)));

        TextView disclaimer = text("A compra libera o uso pelo período escolhido. O uso do item e a finalidade da organização são responsabilidade do comprador.", 12, secondary(), Typeface.NORMAL);
        disclaimer.setLineSpacing(dp(2), 1f);
        root.addView(disclaimer, topMargin(dp(10)));

        Button continueButton = pillButton("Continuar pagamento", "#16A34A", "#FFFFFF");
        continueButton.setOnClickListener(v -> openCartelaCheckoutFromApp(product));
        root.addView(continueButton, topMargin(dp(16)));

        setContentView(scrollView);
        requestInsets(root);
    }

    private View cartelaPurchaseRow(StorePaymentClient.ProductConfig product, int index, Runnable rerender) {
        CartelaPurchaseLine line = cartelaPurchaseLines.get(index);
        LinearLayout row = vertical();
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(rounded(surface(), dp(14), border()));
        LinearLayout head = horizontal();
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("Cartela " + (index + 1), 17, primary(), Typeface.BOLD), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        head.addView(text(money(cartelaLinePrice(product, line.days)), 17, "#16A34A", Typeface.BOLD));
        row.addView(head);

        LinearLayout controls = horizontal();
        controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView days = text(line.days + " dia" + (line.days == 1 ? "" : "s"), 15, "#38BDF8", Typeface.BOLD);
        controls.addView(days, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        controls.addView(iconButton(R.drawable.ic_minus_24, "Menos dias", dp(42), v -> {
            line.days = Math.max(1, line.days - 1);
            rerender.run();
        }));
        controls.addView(iconButton(R.drawable.ic_add_24, "Mais dias", dp(42), v -> {
            line.days = Math.min(365, line.days + 1);
            rerender.run();
        }));
        if (cartelaPurchaseLines.size() > 1) {
            controls.addView(iconButton(R.drawable.ic_delete_24, "Remover cartela", dp(42), v -> {
                cartelaPurchaseLines.remove(index);
                rerender.run();
            }));
        }
        row.addView(controls, topMargin(dp(10)));
        return row;
    }

    private double cartelaLinePrice(StorePaymentClient.ProductConfig product, int days) {
        int extraDays = Math.max(0, days - product.durationDays);
        return product.price + product.dailyFee * Math.pow(extraDays, product.power);
    }

    private String cartelaOrderJson() {
        JSONArray array = new JSONArray();
        for (CartelaPurchaseLine line : cartelaPurchaseLines) {
            JSONObject item = new JSONObject();
            try {
                item.put("days", line.days);
                array.put(item);
            } catch (JSONException ignored) {
            }
        }
        return array.toString();
    }

    private void openCartelaCheckoutFromApp(StorePaymentClient.ProductConfig product) {
        String deviceId = StoreDeviceId.get(this);
        Uri checkoutUri = storePaymentClient.cartelaCheckoutUri(deviceId, cartelaOrderJson());
        gadgetStore.savePendingTable100Payment(checkoutUri.toString(), deviceId);
        new AlertDialog.Builder(this)
                .setTitle("Antes de continuar")
                .setMessage("A Cartela de eventos e uma ferramenta de organizacao. O uso, a finalidade do evento e o cumprimento das regras aplicaveis sao responsabilidade do organizador.")
                .setPositiveButton("Continuar", (dialog, which) -> {
                    openExternalLink(checkoutUri);
                    Toast.makeText(this, "Salve o codigo de recuperacao, conclua o pagamento e volte ao nBTChat.", Toast.LENGTH_LONG).show();
                    showStoreScreen();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private String money(double value) {
        return String.format(Locale.getDefault(), "R$ %.2f", value).replace(".", ",");
    }

    private void recoverCartelaPurchase() {
        String deviceId = StoreDeviceId.get(this);
        Uri recoveryUri = storePaymentClient.cartelaRecoveryUri(deviceId);
        gadgetStore.savePendingTable100Payment(recoveryUri.toString(), deviceId);
        openExternalLink(recoveryUri);
        Toast.makeText(this, "Informe CPF e codigo de recuperacao. Depois volte ao nBTChat.", Toast.LENGTH_LONG).show();
        showStoreScreen();
    }

    private void openPendingCartelaPayment() {
        String url = gadgetStore.pendingTable100CheckoutUrl();
        if (url.isEmpty()) {
            startCartelaPurchase();
            return;
        }
        openExternalLink(Uri.parse(url));
    }

    private boolean isPendingCartelaRecovery() {
        String url = gadgetStore == null ? "" : gadgetStore.pendingTable100CheckoutUrl();
        return url.contains("/recover");
    }

    private void clearPendingCartelaPayment(boolean showFeedback) {
        if (gadgetStore != null) {
            gadgetStore.clearPendingTable100Payment();
        }
        if (showFeedback) {
            Toast.makeText(this, "Operacao cancelada. Voce pode comprar novamente.", Toast.LENGTH_SHORT).show();
        }
        showStoreScreen();
    }

    private void syncCartelaEntitlement(boolean showFeedback) {
        if (cartelaSyncInProgress || storePaymentClient == null || gadgetStore == null) {
            return;
        }
        cartelaSyncInProgress = true;
        String deviceId = gadgetStore.pendingTable100DeviceId();
        if (deviceId.isEmpty()) {
            deviceId = StoreDeviceId.get(this);
        }
        long pendingStartedAt = gadgetStore.pendingTable100StartedAt();
        String finalDeviceId = deviceId;
        new Thread(() -> {
            try {
                StorePaymentClient.Entitlement entitlement = storePaymentClient.getCartelaEntitlement(finalDeviceId);
                runOnUiThread(() -> {
                    cartelaSyncInProgress = false;
                    boolean belongsToThisAttempt = pendingStartedAt <= 0L || entitlement.updatedAt >= pendingStartedAt;
                    if (entitlement.active && entitlement.expiresAt > System.currentTimeMillis() && belongsToThisAttempt) {
                        gadgetStore.activateTable100Until(entitlement.expiresAt);
                        gadgetStore.clearPendingTable100Payment();
                        registerCartelaOnline(false);
                        Toast.makeText(this, "Cartela de eventos liberada.", Toast.LENGTH_LONG).show();
                        showTable100ConfigScreen();
                    } else if (showFeedback) {
                        Toast.makeText(this, "Pagamento ainda nao confirmado.", Toast.LENGTH_LONG).show();
                        if (isPendingCartelaRecovery()) {
                            gadgetStore.clearPendingTable100Payment();
                        }
                    }
                    if ("store".equals(currentScreen)) {
                        showStoreScreen();
                    }
                });
            } catch (Exception ex) {
                String message = ex.getMessage() == null || ex.getMessage().trim().isEmpty()
                        ? "Nao foi possivel verificar o pagamento."
                        : ex.getMessage();
                runOnUiThread(() -> {
                    cartelaSyncInProgress = false;
                    if (showFeedback) {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        if (isPendingCartelaRecovery()) {
                            gadgetStore.clearPendingTable100Payment();
                        }
                    }
                    if ("store".equals(currentScreen)) {
                        showStoreScreen();
                    }
                });
            }
        }, "nBTChat-store-sync").start();
    }

    private void registerCartelaOnline(boolean showFeedback) {
        if (storePaymentClient == null || gadgetStore == null || !gadgetStore.hasTable100()) {
            return;
        }
        GadgetStore.Table100Payload payload = table100PayloadWithKnownLocks(gadgetStore.table100Payload());
        UserProfile local = profileStore.loadLocalProfile();
        String ownerDeviceId = StoreDeviceId.get(this);
        new Thread(() -> {
            try {
                StorePaymentClient.CartelaState state = storePaymentClient.registerCartela(
                        payload.tableId,
                        ownerDeviceId,
                        local.isComplete() ? local.getDisplayName() : "Dono",
                        payload.customTitle,
                        payload.ownerMessage,
                        payload.copyText,
                        payload.ownerContact,
                        payload.allowReservations,
                        payload.reservationHours
                );
                boolean changed = gadgetStore.mergeOnlineCartela(state);
                runOnUiThread(() -> {
                    if (showFeedback) {
                        Toast.makeText(this, "Cartela sincronizada pela internet.", Toast.LENGTH_SHORT).show();
                    }
                    if (changed) {
                        refreshTable100IfOpen(payload.tableId);
                    }
                });
            } catch (Exception ex) {
                if (showFeedback) {
                    runOnUiThread(() -> Toast.makeText(this, "Nao foi possivel sincronizar a cartela online.", Toast.LENGTH_LONG).show());
                }
            }
        }, "nBTChat-cartela-register").start();
    }

    private void syncOwnedCartelaIfUseful() {
        if (gadgetStore == null || storePaymentClient == null || !gadgetStore.hasTable100()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastOwnedCartelaSyncAt < OWNED_CARTELA_SYNC_INTERVAL_MS) {
            return;
        }
        lastOwnedCartelaSyncAt = now;
        GadgetStore.Table100Payload payload = gadgetStore.table100Payload();
        if (payload == null || payload.tableId.isEmpty()) {
            return;
        }
        registerCartelaOnline(false);
        syncCartelaOnline(payload, false);
    }

    private void syncCartelaOnline(GadgetStore.Table100Payload payload, boolean showFeedback) {
        if (payload == null || payload.tableId.isEmpty() || storePaymentClient == null || cartelaOnlineSyncInProgress) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastCartelaSyncAt.containsKey(payload.tableId) ? lastCartelaSyncAt.get(payload.tableId) : 0L;
        if (!showFeedback && now - last < 10_000L) {
            return;
        }
        lastCartelaSyncAt.put(payload.tableId, now);
        cartelaOnlineSyncInProgress = true;
        new Thread(() -> {
            try {
                StorePaymentClient.CartelaState state = storePaymentClient.getCartelaState(payload.tableId);
                boolean changed = gadgetStore.mergeOnlineCartela(state);
                runOnUiThread(() -> {
                    cartelaOnlineSyncInProgress = false;
                    if (showFeedback) {
                        Toast.makeText(this, "Cartela atualizada.", Toast.LENGTH_SHORT).show();
                    }
                    if (changed) {
                        refreshTable100IfOpen(payload.tableId);
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    cartelaOnlineSyncInProgress = false;
                    if (showFeedback) {
                        Toast.makeText(this, "Nao foi possivel atualizar pela internet.", Toast.LENGTH_LONG).show();
                    }
                });
            }
        }, "nBTChat-cartela-state").start();
    }

    private void refreshTable100IfOpen(String tableId) {
        if ("table100_play".equals(currentScreen) && tableId != null && currentTable100Text.contains(tableId)) {
            refreshTable100PlayScreen();
        }
    }

    private void showTable100OptionsDialog() {
        List<String> actions = new ArrayList<>();
        actions.add("Abrir");
        actions.add("Configurar");
        actions.add("Compartilhar");
        actions.add("Compartilhar link");
        new AlertDialog.Builder(this)
                .setTitle(GadgetStore.TABLE_100_TITLE)
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if ("Abrir".equals(action)) {
                        showTable100PlayScreen(gadgetStore.table100Payload().toMessageBody());
                    } else if ("Configurar".equals(action)) {
                        showTable100ConfigScreen();
                    } else if ("Compartilhar".equals(action)) {
                        showTable100ShareChooser();
                    } else if ("Compartilhar link".equals(action)) {
                        shareTable100ExternalLink();
                    }
                })
                .show();
    }

    private void shareTable100ExternalLink() {
        if (!gadgetStore.hasTable100()) {
            Toast.makeText(this, "Compre o item antes de compartilhar.", Toast.LENGTH_LONG).show();
            return;
        }
        GadgetStore.Table100Payload payload = table100PayloadWithKnownLocks(gadgetStore.table100Payload());
        ContactCardPayload contact = localContactCardPayload();
        if (contact == null) {
            Toast.makeText(this, "Configure seu perfil antes de compartilhar.", Toast.LENGTH_LONG).show();
            return;
        }
        String sharePayload = buildStoreSharePayload(MessageStore.KIND_TABLE_100, payload.toMessageBody(), contact);
        if (sharePayload.isEmpty()) {
            Toast.makeText(this, "Nao foi possivel criar o link.", Toast.LENGTH_LONG).show();
            return;
        }
        shareExternalStorePayload(GadgetStore.TABLE_100_TITLE, sharePayload, "Compartilhar item");
    }

    private String buildStoreShareUrl(String kind, String body, ContactCardPayload contact) {
        String encoded = buildStoreSharePayload(kind, body, contact);
        return encoded.isEmpty() ? "" : DOWNLOAD_PAGE_URL + "l/?p=" + Uri.encode(encoded);
    }

    private String buildStoreSharePayload(String kind, String body, ContactCardPayload contact) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("v", 1);
            payload.put("kind", kind == null ? "" : kind);
            payload.put("body", body == null ? "" : body);
            payload.put("contact", contact.toJson());
            return Base64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP);
        } catch (Exception ignored) {
            return "";
        }
    }

    private void showTable100ShareChooser() {
        if (!gadgetStore.hasTable100()) {
            Toast.makeText(this, "Compre a Cartela de eventos antes de compartilhar.", Toast.LENGTH_LONG).show();
            return;
        }
        if (gadgetStore.table100CopyText().trim().isEmpty()) {
            Toast.makeText(this, "Configure a Cartela de eventos antes de compartilhar.", Toast.LENGTH_LONG).show();
            showTable100ConfigScreen();
            return;
        }
        showShareTargetScreen("Compartilhar " + GadgetStore.TABLE_100_TITLE,
                "Nenhum contato nBTChat para receber a Cartela de eventos.",
                new HashSet<>(),
                this::sendTable100ToAddress);
    }

    private void showTable100ConfigScreen() {
        currentScreen = "store_config";
        messageList = null;

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout root = vertical();
        root.setBackgroundColor(color(background()));
        applyRootInsets(root, dp(18), dp(12), dp(18), dp(132));
        scrollView.addView(root, matchWrap());

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(iconButton(R.drawable.ic_back_24, "Voltar", dp(42), v -> showStoreScreen()));
        TextView title = text(GadgetStore.TABLE_100_TITLE, 25, primary(), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        titleParams.setMargins(dp(12), 0, 0, 0);
        top.addView(title, titleParams);
        addTopActions(top);
        root.addView(top);

        TextView subtitle = text("Configure o que aparece para quem usa a tabela e o texto que sera copiado ao confirmar um numero.", 14, secondary(), Typeface.NORMAL);
        subtitle.setLineSpacing(dp(2), 1f);
        root.addView(subtitle, topMargin(dp(12)));

        root.addView(label("Titulo da cartela"));
        EditText titleInput = input("Ex.: Evento beneficente");
        titleInput.setSingleLine(true);
        titleInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
        titleInput.setText(gadgetStore.table100CustomTitle());
        root.addView(titleInput, topMargin(dp(6)));

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

        root.addView(label("Dados pessoais para contato"));
        EditText contactInput = input("Pix, telefone, e-mail ou outra forma de contato");
        contactInput.setSingleLine(false);
        contactInput.setMinLines(2);
        contactInput.setMaxLines(5);
        contactInput.setText(gadgetStore.table100OwnerContact());
        root.addView(contactInput, topMargin(dp(6)));

        LinearLayout reservationRow = horizontal();
        reservationRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout reservationTexts = vertical();
        reservationTexts.addView(text("Permitir reservas", 15, primary(), Typeface.BOLD));
        TextView reservationHelp = text("Participantes podem reservar um numero por prazo definido.", 12, secondary(), Typeface.NORMAL);
        reservationHelp.setLineSpacing(dp(1), 1f);
        reservationTexts.addView(reservationHelp, topMargin(dp(2)));
        reservationRow.addView(reservationTexts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Switch reservationSwitch = new Switch(this);
        reservationSwitch.setChecked(gadgetStore.table100ReservationsEnabled());
        reservationRow.addView(reservationSwitch);
        root.addView(reservationRow, topMargin(dp(14)));

        root.addView(label("Prazo da reserva em horas"));
        EditText reservationHoursInput = input("Ex.: 24");
        reservationHoursInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        reservationHoursInput.setSingleLine(true);
        reservationHoursInput.setText(String.valueOf(gadgetStore.table100ReservationHours()));
        root.addView(reservationHoursInput, topMargin(dp(6)));

        Button save = pillButton("Salvar", "#16A34A", "#FFFFFF");
        save.setOnClickListener(v -> {
            int hours;
            try {
                hours = Integer.parseInt(reservationHoursInput.getText().toString().trim());
            } catch (Exception ex) {
                hours = 24;
            }
            gadgetStore.saveTable100Texts(titleInput.getText().toString(), messageInput.getText().toString(), copyInput.getText().toString(), contactInput.getText().toString());
            gadgetStore.saveTable100ReservationSettings(reservationSwitch.isChecked(), hours);
            registerCartelaOnline(false);
            hideKeyboard(contactInput);
            Toast.makeText(this, "Cartela de eventos salva.", Toast.LENGTH_SHORT).show();
            showStoreScreen();
        });
        root.addView(save, topMargin(dp(16)));

        setContentView(scrollView);
        requestInsets(root);
    }

    private void showTable100PlayScreen(String configuredText) {
        table100ReturnScreen = currentScreen;
        currentTable100Text = configuredText == null ? "" : configuredText.trim();
        GadgetStore.Table100Payload initialPayload = GadgetStore.Table100Payload.parse(currentTable100Text);
        if (table100IsOwner(initialPayload)) {
            currentTable100Text = table100PayloadWithKnownLocks(gadgetStore.table100Payload()).toMessageBody();
        } else if (!initialPayload.tableId.isEmpty()) {
            currentTable100Text = table100BodyWithKnownLocks(currentTable100Text);
        }
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
        TextView title = text(table100DisplayTitle(payload), 26, primary(), Typeface.BOLD);
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

        TextView footer = text(GadgetStore.TABLE_100_FOOTER, 12, secondary(), Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        footer.setLineSpacing(dp(2), 1f);
        root.addView(footer, topMargin(dp(10)));

        if (!owner && !payload.ownerMessage.trim().isEmpty()) {
            TextView ownerMessage = text(payload.ownerMessage.trim(), 15, primary(), Typeface.BOLD);
            ownerMessage.setLineSpacing(dp(2), 1f);
            ownerMessage.setPadding(dp(14), dp(12), dp(14), dp(12));
            ownerMessage.setBackground(rounded(surface(), dp(14), border()));
            root.addView(ownerMessage, topMargin(dp(14)));
        }

        if (owner) {
            root.addView(table100OwnerChoices(payload), topMargin(dp(18)));
        } else if (table100HasLocalChoice(payload)) {
            if (!payload.ownerContact.trim().isEmpty()) {
                Button contact = pillButton("Informacoes de contato do dono", surfaceAlt(), primary());
                contact.setOnClickListener(v -> showTable100OwnerContactDialog(payload));
                root.addView(contact, topMargin(dp(14)));
            }
            root.addView(table100MyChoices(payload), topMargin(dp(12)));
        }

        setContentView(scrollView);
        requestInsets(root);
        syncCartelaOnline(payload, false);
        scheduleTable100AutoSync(payload.tableId);
    }

    private void refreshTable100PlayScreen() {
        String text = freshTable100BodyForCurrentScreen();
        String returnScreen = table100ReturnScreen;
        showTable100PlayScreen(text);
        table100ReturnScreen = returnScreen;
    }

    private String freshTable100BodyForCurrentScreen() {
        GadgetStore.Table100Payload payload = GadgetStore.Table100Payload.parse(currentTable100Text);
        if (table100IsOwner(payload)) {
            return table100PayloadWithKnownLocks(gadgetStore.table100Payload()).toMessageBody();
        }
        if (!payload.tableId.isEmpty()) {
            return table100BodyWithKnownLocks(currentTable100Text);
        }
        return currentTable100Text;
    }

    private String table100DisplayTitle(GadgetStore.Table100Payload payload) {
        if (payload != null && !payload.customTitle.trim().isEmpty()) {
            return payload.customTitle.trim();
        }
        return GadgetStore.TABLE_100_TITLE;
    }

    private void scheduleTable100AutoSync(String tableId) {
        if (table100AutoSyncRunnable != null) {
            uiHandler.removeCallbacks(table100AutoSyncRunnable);
        }
        if (tableId == null || tableId.trim().isEmpty()) {
            table100AutoSyncRunnable = null;
            return;
        }
        String cleanTableId = tableId.trim();
        table100AutoSyncRunnable = () -> {
            if (!"table100_play".equals(currentScreen) || currentTable100Text == null || !currentTable100Text.contains(cleanTableId)) {
                table100AutoSyncRunnable = null;
                return;
            }
            syncCartelaOnline(GadgetStore.Table100Payload.parse(currentTable100Text), false);
            uiHandler.postDelayed(table100AutoSyncRunnable, 60_000L);
        };
        uiHandler.postDelayed(table100AutoSyncRunnable, 60_000L);
    }

    private void stopTable100AutoSync() {
        if (table100AutoSyncRunnable != null) {
            uiHandler.removeCallbacks(table100AutoSyncRunnable);
            table100AutoSyncRunnable = null;
        }
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
        scheduleWakeForPendingOutgoing(outgoing);
        Toast.makeText(this, "Vou enviar assim que o Bluetooth conectar.", Toast.LENGTH_SHORT).show();
    }

    private void scheduleWakeForPendingOutgoing(PendingOutgoing outgoing) {
        if (outgoing == null || outgoing.address == null || outgoing.address.isEmpty() || outgoing.id == null || outgoing.id.isEmpty()) {
            return;
        }
        uiHandler.postDelayed(() -> wakeForPendingOutgoing(outgoing.address, outgoing.id, false), PENDING_WAKE_DELAY_MS);
        uiHandler.postDelayed(() -> wakeForPendingOutgoing(outgoing.address, outgoing.id, true), PENDING_REPAIR_DELAY_MS);
    }

    private void wakeForPendingOutgoing(String address, String id, boolean repairIfPaired) {
        if (address == null || address.isEmpty() || id == null || id.isEmpty()) {
            return;
        }
        MessageStore.ChatMessage message = messageStore.findMessage(address, id);
        if (message == null || !message.mine || !MessageStore.STATUS_PENDING.equals(message.status)) {
            return;
        }
        if (btChatManager.canSendTo(address)) {
            flushPendingOutgoing(address);
            return;
        }
        if (repairIfPaired && btChatManager.getPairedCandidate(address) != null) {
            if (tryRepairPairing(address)) {
                Toast.makeText(this, "Tentando refazer o pareamento para enviar a mensagem.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (tryWakeAddress(address)) {
            Toast.makeText(this, "Tentando acordar o contato pelo Bluetooth.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean tryWakeAddress(String address) {
        long now = System.currentTimeMillis();
        long last = lastWakeAttemptAt.containsKey(address) ? lastWakeAttemptAt.get(address) : 0L;
        if (now - last < WAKE_THROTTLE_MS) {
            return false;
        }
        lastWakeAttemptAt.put(address, now);
        return btChatManager.wakeForMessage(address);
    }

    private boolean tryRepairPairing(String address) {
        long now = System.currentTimeMillis();
        long last = lastPairRepairAt.containsKey(address) ? lastPairRepairAt.get(address) : 0L;
        if (now - last < PAIR_REPAIR_THROTTLE_MS) {
            return false;
        }
        lastPairRepairAt.put(address, now);
        return btChatManager.repairPairingForMessage(address);
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
            long now = System.currentTimeMillis();
            long last = lastConnectAttemptAt.containsKey(address) ? lastConnectAttemptAt.get(address) : 0L;
            if (now - last < CONNECT_BACKOFF_MS) {
                return;
            }
            lastConnectAttemptAt.put(address, now);
            btChatManager.connect(target);
        } else if (target == null) {
            tryWakeAddress(address);
        }
    }

    private void pickChatImage() {
        startImagePicker(REQUEST_PICK_CHAT_IMAGE);
    }

    private void captureChatImage() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CHAT_CAMERA_PERMISSION);
            return;
        }
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
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_VOICE_RECORD);
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
            if (!controls.mine && !playingVoiceId.isEmpty()) {
                messageStore.markVoiceHeard(currentRemoteAddress, playingVoiceId);
                applyVoiceSeekBarTint(controls.seekBar, true);
            }
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
        playingVoiceControls.time.setText(formatDuration(Math.max(0, duration - position)));
    }

    private void resetVoiceControls(VoiceControls controls) {
        controls.button.setImageResource(R.drawable.ic_play_24);
        controls.seekBar.setProgress(0);
        controls.time.setText(formatDuration(controls.durationMs));
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

    private String formatMessageTime(long when) {
        long value = when > 0L ? when : System.currentTimeMillis();
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(value));
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
                addMessageBubble(message.id, message.body, message.mine, message.kind, message.mediaBase64, message.durationMs, message.status, message.replyToId, message.replyPreview, message.sentAt, false);
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
        addMessageBubble("", body, mine, MessageStore.KIND_TEXT, "", 0L, mine ? MessageStore.STATUS_SENT : MessageStore.STATUS_DELIVERED, "", "", System.currentTimeMillis(), true);
    }

    private void addMessageBubble(String body, boolean mine, String kind, String mediaBase64, long durationMs, String status) {
        addMessageBubble("", body, mine, kind, mediaBase64, durationMs, status, "", "", System.currentTimeMillis(), true);
    }

    private void addMessageBubble(String id, String body, boolean mine, String kind, String mediaBase64, long durationMs, String status, String replyToId, String replyPreview, boolean scrollBottom) {
        addMessageBubble(id, body, mine, kind, mediaBase64, durationMs, status, replyToId, replyPreview, System.currentTimeMillis(), scrollBottom);
    }

    private void addMessageBubble(String id, String body, boolean mine, String kind, String mediaBase64, long durationMs, String status, String replyToId, String replyPreview, long sentAt, boolean scrollBottom) {
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
            TextView title = text(GadgetStore.TABLE_100_TITLE, 16, mine ? "#FFFFFF" : primary(), Typeface.BOLD);
            title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_table_24, 0, 0, 0);
            title.setCompoundDrawablePadding(dp(8));
            bubble.addView(title);
            TextView hint = text("Toque para abrir", 12, mine ? "#D7FBE8" : secondary(), Typeface.NORMAL);
            bubble.addView(hint, topMargin(dp(2)));
            View.OnClickListener openTable = v -> showTable100PlayScreen(body);
            bubble.setOnClickListener(openTable);
            title.setOnClickListener(openTable);
            hint.setOnClickListener(openTable);
        } else if (MessageStore.KIND_CONTACT_INVITE.equals(kind)) {
            addContactCardToBubble(bubble, body, mine);
        } else if ((MessageStore.KIND_IMAGE.equals(kind) || MessageStore.KIND_GIF.equals(kind)) && mediaBase64 != null && !mediaBase64.isEmpty()) {
            if (MessageStore.KIND_GIF.equals(kind)) {
                addGifBadge(bubble, mine);
            }
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
            boolean heard = mine || messageStore.isVoiceHeard(currentRemoteAddress, id);
            applyVoiceSeekBarTint(seekBar, heard);
            TextView time = text(formatDuration(durationMs), 12, mine ? "#D7FBE8" : secondary(), Typeface.BOLD);
            VoiceControls controls = new VoiceControls(play, seekBar, time, durationMs, mine);
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
            if (mine) {
                voiceRow.addView(voiceAvatar(profileStore.loadLocalProfile(), true));
                voiceRow.addView(play, leftMargin(dp(8), dp(42), dp(42)));
                voiceRow.addView(seekBar, new LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT));
            } else {
                voiceRow.addView(play);
                voiceRow.addView(seekBar, new LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT));
                voiceRow.addView(voiceAvatar(currentRemoteProfile, false), leftMargin(dp(8), dp(42), dp(42)));
            }
            bubble.addView(voiceRow);
            time.setGravity(Gravity.LEFT);
            bubble.addView(time, topMargin(dp(2)));
        } else {
            addTextContentToBubble(bubble, id, body, mine);
        }
        LinearLayout footer = horizontal();
        footer.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        TextView sentTime = text(formatMessageTime(sentAt), 11, mine ? "#D7FBE8" : secondary(), Typeface.NORMAL);
        sentTime.setGravity(Gravity.RIGHT);
        footer.addView(sentTime);
        if (mine) {
            TextView receipt = text(statusIcon(status), 11, statusColor(status), Typeface.BOLD);
            receipt.setGravity(Gravity.RIGHT);
            if (id != null && !id.isEmpty()) {
                receiptViews.put(id, receipt);
            }
            footer.addView(receipt, leftMargin(dp(6), LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        bubble.addView(footer, topMargin(dp(3)));
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

    private void addContactCardToBubble(LinearLayout bubble, String body, boolean mine) {
        ContactCardPayload payload = ContactCardPayload.parse(body);
        TextView title = text(payload == null ? "Contato nBTChat" : safeName(payload.name, "Contato nBTChat"),
                16, mine ? "#FFFFFF" : primary(), Typeface.BOLD);
        title.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_invite_24, 0, 0, 0);
        title.setCompoundDrawablePadding(dp(8));
        bubble.addView(title);

        String detail = payload == null
                ? "Contato indisponivel"
                : (payload.bluetoothName.isEmpty() ? "Dados Bluetooth compartilhados" : payload.bluetoothName);
        TextView subtitle = text(detail, 12, mine ? "#D7FBE8" : secondary(), Typeface.NORMAL);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        bubble.addView(subtitle, topMargin(dp(2)));

        if (payload == null || mine) {
            return;
        }
        LinearLayout actions = horizontal();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button save = pillButton("Salvar", mine ? "#FFFFFF" : "#16A34A", mine ? "#0F766E" : "#FFFFFF");
        save.setTextSize(12);
        save.setOnClickListener(v -> saveSharedContact(payload));
        actions.addView(save, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button connect = pillButton("Conectar", mine ? "#D7FBE8" : surfaceAlt(), mine ? "#0F766E" : primary());
        connect.setTextSize(12);
        connect.setOnClickListener(v -> connectSharedContact(payload));
        LinearLayout.LayoutParams connectParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        connectParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(connect, connectParams);
        bubble.addView(actions, topMargin(dp(8)));
    }

    private void saveSharedContact(ContactCardPayload payload) {
        if (payload == null || payload.address.trim().isEmpty()) {
            Toast.makeText(this, "Este contato nao tem endereco Bluetooth salvo.", Toast.LENGTH_LONG).show();
            return;
        }
        UserProfile profile = payload.profile.isComplete()
                ? payload.profile
                : new UserProfile(safeName(payload.name, "Contato nBTChat"), "", UserProfile.GENDER_OTHER, "");
        profileStore.saveContact(payload.address, profile);
        if (!payload.deviceId.isEmpty()) {
            profileStore.saveIdentity(payload.address, payload.deviceId, payload.publicKey, payload.bluetoothName);
        }
        profileStore.setContactShareAllowed(payload.address, payload.allowContactSharing);
        Toast.makeText(this, "Contato salvo.", Toast.LENGTH_SHORT).show();
        if ("home".equals(currentScreen)) {
            renderContactList();
        }
    }

    private void connectSharedContact(ContactCardPayload payload) {
        if (payload == null) {
            return;
        }
        saveSharedContact(payload);
        BtChatManager.DeviceCandidate candidate = btChatManager.getDirectCandidate(payload.address, payload.bluetoothName);
        if (candidate == null) {
            Toast.makeText(this, "Quando este aparelho estiver por perto, procure ou pareie pelo Bluetooth.", Toast.LENGTH_LONG).show();
            return;
        }
        pendingOpenChatAddress = candidate.address;
        openNextQrConnectionUntil = System.currentTimeMillis() + 45_000L;
        btChatManager.connectDirect(candidate);
    }

    private GridLayout table100Grid(GadgetStore.Table100Payload payload, boolean mine, boolean fullScreen, boolean owner) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(fullScreen ? 10 : 5);
        grid.setPadding(0, dp(4), 0, 0);
        int available = Math.max(dp(280), getResources().getDisplayMetrics().widthPixels - dp(56));
        int cellSize = fullScreen ? Math.max(dp(29), Math.min(dp(42), available / 10 - dp(4))) : dp(40);
        Map<Integer, Integer> numberStatuses = table100NumberStatuses(payload);
        for (int i = 1; i <= 100; i++) {
            final int number = i;
            Button cell = new Button(this);
            cell.setTextSize(fullScreen ? 12 : 11);
            cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cell.setAllCaps(false);
            cell.setMinWidth(0);
            cell.setMinimumWidth(0);
            cell.setMinHeight(0);
            cell.setMinimumHeight(0);
            cell.setPadding(0, 0, 0, 0);
            int status = table100NumberStatus(numberStatuses, number);
            setCellTextForStatus(cell, status, number, fullScreen, mine);
            String fill = table100CellColor(number, status, fullScreen, mine);
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

    private String table100CellColor(int number, int status, boolean fullScreen, boolean mine) {
        if (status != 0) {
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

    private Map<Integer, Integer> table100NumberStatuses(GadgetStore.Table100Payload payload) {
        Map<Integer, Integer> statuses = new HashMap<>();
        if (payload == null || payload.tableId.isEmpty()) {
            return statuses;
        }
        for (Integer number : payload.lockedNumbers) {
            if (number != null && number >= 1 && number <= 100) {
                statuses.put(number, 1);
            }
        }
        for (GadgetStore.Table100Choice choice : gadgetStore.loadChoices(payload.tableId)) {
            if (choice.number >= 1 && choice.number <= 100) {
                int previous = statuses.containsKey(choice.number) ? statuses.get(choice.number) : 0;
                statuses.put(choice.number, choice.confirmed ? 2 : Math.max(previous, 1));
            }
        }
        return statuses;
    }

    private int table100NumberStatus(Map<Integer, Integer> statuses, int number) {
        return statuses == null || !statuses.containsKey(number) ? 0 : statuses.get(number);
    }

    private int table100NumberStatus(GadgetStore.Table100Payload payload, int number, boolean owner) {
        return table100NumberStatus(table100NumberStatuses(payload), number);
    }

    private void showTable100NumberDialog(int number, GadgetStore.Table100Payload payload, boolean owner) {
        if (payload == null) {
            return;
        }
        if (owner) {
            Toast.makeText(this, "Use a lista de participantes para confirmar ou remover escolhas.", Toast.LENGTH_LONG).show();
            return;
        }
        int status = table100NumberStatus(payload, number, false);
        if (status != 0) {
            Toast.makeText(this,
                    status == 1 ? "Este numero esta em analise pelo organizador." : "Este numero ja esta confirmado.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String disclaimer = "Voce escolheu o numero " + number + ". Ao continuar, voce reconhece que a organizacao e a finalidade do evento sao responsabilidade do organizador.";
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Confirmar escolha")
                .setMessage(disclaimer);
        if (payload.allowReservations) {
            builder.setPositiveButton("Confirmar agora", (dialog, which) -> {
                showTable100ResultDialog(number, payload, false);
            });
            builder.setNeutralButton("Reservar", (dialog, which) -> {
                markTable100Choice(payload, number, true);
                refreshTable100PlayScreen();
                Toast.makeText(this, "Reserva registrada e aguardando analise do organizador.", Toast.LENGTH_LONG).show();
            });
            builder.setNegativeButton("Cancelar", null);
        } else {
            builder.setPositiveButton("Sim", (dialog, which) -> {
                showTable100ResultDialog(number, payload, false);
            });
            builder.setNegativeButton("Nao", null);
        }
        builder.show();
    }

    private void showTable100ResultDialog(int number, GadgetStore.Table100Payload payload, boolean reserved) {
        String message = payload.ownerMessage.trim().isEmpty()
                ? "Escolha registrada."
                : payload.ownerMessage.trim();
        String copyText = payload.copyText.trim().isEmpty()
                ? "Nenhum texto configurado para esta tabela."
                : payload.copyText.trim();

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
                .setPositiveButton("OK", (dialog, which) -> {
                    copyToClipboard(copyText, false);
                    markTable100Choice(payload, number, reserved);
                    refreshTable100PlayScreen();
                    Toast.makeText(this, "Texto copiado e escolha enviada para análise.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void markTable100Choice(GadgetStore.Table100Payload payload, int number, boolean reserved) {
        if (payload == null || payload.tableId.isEmpty()) {
            return;
        }
        UserProfile local = profileStore.loadLocalProfile();
        String chooserName = local.isComplete() ? local.getDisplayName() : "Contato";
        String chooserDeviceId = StoreDeviceId.get(this);
        long expiresAt = reserved ? System.currentTimeMillis() + Math.max(1, payload.reservationHours) * 60L * 60L * 1000L : 0L;
        gadgetStore.saveChoice(payload.tableId, chooserDeviceId, number, chooserName, false, reserved, expiresAt);
        chooseTable100NumberOnline(payload, number, chooserDeviceId, chooserName, reserved);
        sendTable100Choice(payload, number, reserved, expiresAt);
    }

    private View table100OwnerChoices(GadgetStore.Table100Payload payload) {
        LinearLayout container = vertical();
        container.setPadding(dp(14), dp(14), dp(14), dp(14));
        container.setBackground(rounded(surface(), dp(14), border()));
        container.addView(text("Escolhas dos contatos", 18, primary(), Typeface.BOLD));
        Button addManual = pillButton("Adicionar pessoa e travar numero", "#16A34A", "#FFFFFF");
        addManual.setOnClickListener(v -> showManualTableChoiceDialog(payload));
        container.addView(addManual, topMargin(dp(12)));

        List<GadgetStore.Table100Choice> choices = gadgetStore.loadChoices(payload.tableId);
        if (choices.isEmpty()) {
            TextView empty = text("Nenhum contato assinalou esta tabela ainda.", 14, secondary(), Typeface.NORMAL);
            container.addView(empty, topMargin(dp(10)));
            return container;
        }

        Collections.sort(choices, (left, right) -> {
            int nameCompare = table100ChoiceBaseName(left).compareToIgnoreCase(table100ChoiceBaseName(right));
            if (nameCompare != 0) {
                return nameCompare;
            }
            return Integer.compare(left.number, right.number);
        });
        Map<String, Set<String>> participantsByName = new HashMap<>();
        for (GadgetStore.Table100Choice choice : choices) {
            String base = table100ChoiceBaseName(choice);
            Set<String> keys = participantsByName.containsKey(base) ? participantsByName.get(base) : new HashSet<>();
            keys.add(table100ChoiceParticipantKey(choice));
            participantsByName.put(base, keys);
        }
        Map<String, Integer> nameCounts = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : participantsByName.entrySet()) {
            nameCounts.put(entry.getKey(), entry.getValue().size());
        }
        Map<String, Integer> nameIndexes = new HashMap<>();
        Map<String, String> participantLabels = new HashMap<>();

        boolean anyPending = false;
        for (GadgetStore.Table100Choice choice : choices) {
            if (!choice.confirmed && !choice.removed) {
                if (!anyPending) {
                    container.addView(text("Pendentes", 13, secondary(), Typeface.BOLD), topMargin(dp(12)));
                    anyPending = true;
                }
                container.addView(table100ChoiceRow(payload, choice, table100ChoiceDisplayName(choice, nameCounts, nameIndexes, participantLabels)), topMargin(dp(8)));
            }
        }

        boolean anyConfirmed = false;
        for (GadgetStore.Table100Choice choice : choices) {
            if (choice.confirmed && !choice.removed) {
                if (!anyConfirmed) {
                    container.addView(text("Confirmados", 13, secondary(), Typeface.BOLD), topMargin(dp(14)));
                    anyConfirmed = true;
                }
                container.addView(table100ChoiceRow(payload, choice, table100ChoiceDisplayName(choice, nameCounts, nameIndexes, participantLabels)), topMargin(dp(8)));
            }
        }

        boolean anyRemoved = false;
        for (GadgetStore.Table100Choice choice : choices) {
            if (choice.removed) {
                if (!anyRemoved) {
                    container.addView(text("Removidos", 13, secondary(), Typeface.BOLD), topMargin(dp(14)));
                    anyRemoved = true;
                }
                container.addView(table100ChoiceRow(payload, choice, table100ChoiceDisplayName(choice, nameCounts, nameIndexes, participantLabels)), topMargin(dp(8)));
            }
        }
        return container;
    }

    private String table100ChoiceBaseName(GadgetStore.Table100Choice choice) {
        if (choice == null) {
            return "Contato";
        }
        UserProfile profile = profileStore.loadContact(choice.address);
        return safeName(profile.isComplete() ? profile.getDisplayName() : (choice.name.isEmpty() ? "Contato" : choice.name), "Contato");
    }

    private String table100ChoiceDisplayName(GadgetStore.Table100Choice choice, Map<String, Integer> counts, Map<String, Integer> indexes, Map<String, String> participantLabels) {
        String base = table100ChoiceBaseName(choice);
        if (counts == null || !counts.containsKey(base) || counts.get(base) <= 1) {
            return base;
        }
        String participant = table100ChoiceParticipantKey(choice);
        String labelKey = base + "\n" + participant;
        if (participantLabels != null && participantLabels.containsKey(labelKey)) {
            return participantLabels.get(labelKey);
        }
        int index = indexes.containsKey(base) ? indexes.get(base) + 1 : 1;
        indexes.put(base, index);
        String label = base + "(" + index + ")";
        if (participantLabels != null) {
            participantLabels.put(labelKey, label);
        }
        return label;
    }

    private String table100ChoiceParticipantKey(GadgetStore.Table100Choice choice) {
        if (choice == null) {
            return "";
        }
        String address = choice.address == null ? "" : choice.address.trim();
        if (!address.isEmpty()) {
            return address;
        }
        return "choice:" + choice.number + ":" + table100ChoiceBaseName(choice);
    }

    private boolean table100HasLocalChoice(GadgetStore.Table100Payload payload) {
        if (payload == null || payload.tableId.isEmpty()) {
            return false;
        }
        String deviceId = StoreDeviceId.get(this);
        for (GadgetStore.Table100Choice choice : gadgetStore.loadChoices(payload.tableId)) {
            if (deviceId.equals(choice.address)) {
                return true;
            }
        }
        return false;
    }

    private View table100MyChoices(GadgetStore.Table100Payload payload) {
        LinearLayout container = vertical();
        container.setPadding(dp(14), dp(12), dp(14), dp(12));
        container.setBackground(rounded(surface(), dp(14), border()));
        container.addView(text("Meus numeros", 17, primary(), Typeface.BOLD));
        List<GadgetStore.Table100Choice> choices = gadgetStore.choicesForAddress(payload.tableId, StoreDeviceId.get(this));
        if (choices.isEmpty()) {
            container.addView(text("Nenhum numero escolhido neste aparelho.", 13, secondary(), Typeface.NORMAL), topMargin(dp(6)));
            return container;
        }
        StringBuilder builder = new StringBuilder();
        for (GadgetStore.Table100Choice choice : choices) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(choice.number).append(" - ").append(table100ChoiceStateLabel(choice));
        }
        TextView list = text(builder.toString(), 14, primary(), Typeface.BOLD);
        list.setLineSpacing(dp(2), 1f);
        container.addView(list, topMargin(dp(8)));
        return container;
    }

    private String table100ChoiceStateLabel(GadgetStore.Table100Choice choice) {
        if (choice.confirmed) {
            return "confirmado";
        }
        if (choice.removed) {
            return "removido, em analise";
        }
        if (choice.reserved) {
            return "reservado, em analise";
        }
        return "em analise";
    }

    private void showTable100OwnerContactDialog(GadgetStore.Table100Payload payload) {
        String contact = payload == null ? "" : payload.ownerContact.trim();
        if (contact.isEmpty()) {
            Toast.makeText(this, "O dono da tabela nao deixou dados de contato.", Toast.LENGTH_LONG).show();
            return;
        }
        TextView box = text(contact, 15, primary(), Typeface.NORMAL);
        box.setTextIsSelectable(true);
        box.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.setBackground(rounded(surfaceAlt(), dp(12), border()));
        new AlertDialog.Builder(this)
                .setTitle("Contato do dono")
                .setView(box)
                .setPositiveButton("Copiar", (dialog, which) -> copyMessageText(contact))
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void showManualTableChoiceDialog(GadgetStore.Table100Payload payload) {
        LinearLayout content = vertical();
        content.setPadding(dp(18), dp(8), dp(18), dp(4));

        EditText nameInput = input("Nome da pessoa");
        content.addView(label("Pessoa"));
        content.addView(nameInput, topMargin(dp(6)));

        EditText numberInput = input("Numero de 1 a 100");
        numberInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        content.addView(label("Numero"));
        content.addView(numberInput, topMargin(dp(6)));

        new AlertDialog.Builder(this)
                .setTitle("Travar numero")
                .setView(content)
                .setPositiveButton("Adicionar", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    int number;
                    try {
                        number = Integer.parseInt(numberInput.getText().toString().trim());
                    } catch (Exception ex) {
                        Toast.makeText(this, "Informe um numero entre 1 e 100.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (number < 1 || number > 100) {
                        Toast.makeText(this, "Informe um numero entre 1 e 100.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (table100NumberStatus(payload, number, true) != 0) {
                        Toast.makeText(this, "Este numero ja esta travado.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String address = "manual:" + payload.tableId + ":" + number + ":" + Long.toHexString(System.currentTimeMillis());
                    gadgetStore.saveChoice(payload.tableId, address, number, name.isEmpty() ? "Pessoa externa" : name, false);
                    chooseTable100NumberOnline(payload, number, address, name.isEmpty() ? "Pessoa externa" : name, false);
                    refreshTable100PlayScreen();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private View table100ChoiceRow(GadgetStore.Table100Payload payload, GadgetStore.Table100Choice choice, String displayName) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(10), dp(10));
        row.setBackground(rounded(surfaceAlt(), dp(12), border()));

        String name = safeName(displayName, "Contato");
        TextView label = text(name + " - numero " + choice.number, 15, choice.removed ? secondary() : primary(), Typeface.BOLD);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Switch toggle = new Switch(this);
        toggle.setChecked(choice.confirmed);
        toggle.setEnabled(!choice.removed);
        toggle.setOnClickListener(v -> {
            boolean target = toggle.isChecked();
            String action = target ? "confirmar" : "remover a confirmacao de";
            new AlertDialog.Builder(this)
                    .setTitle(target ? "Confirmar escolha?" : "Remover confirmacao?")
                    .setMessage("Deseja " + action + " " + safeName(name, "Contato") + " no numero " + choice.number + "?")
                    .setPositiveButton("Sim", (dialog, which) -> {
                        gadgetStore.setChoiceConfirmed(payload.tableId, choice.address, choice.number, target);
                        confirmTable100NumberOnline(payload, choice.address, choice.number, target);
                        sendTable100Confirmation(payload, choice.address, choice.number, target);
                        refreshTable100PlayScreen();
                    })
                    .setNegativeButton("Nao", (dialog, which) -> toggle.setChecked(!target))
                    .show();
        });
        row.addView(toggle);
        row.setOnLongClickListener(v -> {
            showTable100ChoiceActions(payload, choice, safeName(name, "Contato"));
            return true;
        });
        return row;
    }

    private void showTable100ChoiceActions(GadgetStore.Table100Payload payload, GadgetStore.Table100Choice choice, String name) {
        List<String> actions = new ArrayList<>();
        actions.add("Editar nome");
        if (choice.removed) {
            actions.add("Restaurar");
            actions.add("Excluir permanentemente");
        } else {
            actions.add("Remover");
        }
        new AlertDialog.Builder(this)
                .setTitle(name + " - numero " + choice.number)
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if ("Editar nome".equals(action)) {
                        showRenameTable100ChoiceDialog(payload, choice, name);
                    } else if ("Restaurar".equals(action)) {
                        restoreTable100Choice(payload, choice, name);
                    } else if (choice.removed) {
                        confirmPermanentDeleteTable100Choice(payload, choice, name);
                    } else {
                        confirmDeleteTable100Choice(payload, choice, name);
                    }
                })
                .show();
    }

    private void restoreTable100Choice(GadgetStore.Table100Payload payload, GadgetStore.Table100Choice choice, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Restaurar participante?")
                .setMessage(name + " voltara para Pendentes e o numero " + choice.number + " continuara aguardando confirmacao.")
                .setPositiveButton("Restaurar", (dialog, which) -> {
                    gadgetStore.markChoiceRemoved(payload.tableId, choice.address, choice.number, false);
                    restoreTable100ChoiceOnline(payload, choice.address, choice.number);
                    refreshTable100PlayScreen();
                    Toast.makeText(this, name + " restaurado.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showRenameTable100ChoiceDialog(GadgetStore.Table100Payload payload, GadgetStore.Table100Choice choice, String name) {
        EditText input = input("Nome");
        input.setSingleLine(true);
        input.setText(table100ChoiceBaseName(choice));
        input.setSelectAllOnFocus(true);
        int padding = dp(18);
        input.setPadding(padding, input.getPaddingTop(), padding, input.getPaddingBottom());
        new AlertDialog.Builder(this)
                .setTitle("Editar nome")
                .setView(input)
                .setPositiveButton("Salvar", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, "Informe um nome.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    gadgetStore.renameChoice(payload.tableId, choice.address, choice.number, newName);
                    renameTable100ChoiceOnline(payload, choice.address, choice.number, newName);
                    refreshTable100PlayScreen();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmDeleteTable100Choice(GadgetStore.Table100Payload payload, GadgetStore.Table100Choice choice, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Remover participante?")
                .setMessage("O participante ira para a categoria Removidos e o numero " + choice.number + " continuara bloqueado com ampulheta. Para liberar o numero, exclua o registro permanentemente em Removidos.")
                .setPositiveButton("Remover", (dialog, which) -> {
                    gadgetStore.markChoiceRemoved(payload.tableId, choice.address, choice.number, true);
                    deleteTable100ChoiceOnline(payload, choice.address, choice.number, false);
                    refreshTable100PlayScreen();
                    Toast.makeText(this, name + " removido.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmPermanentDeleteTable100Choice(GadgetStore.Table100Payload payload, GadgetStore.Table100Choice choice, String name) {
        new AlertDialog.Builder(this)
                .setTitle("Excluir permanentemente?")
                .setMessage("Esta acao remove " + name + " da categoria Removidos e libera o numero " + choice.number + " para nova escolha.")
                .setPositiveButton("Excluir", (dialog, which) -> {
                    gadgetStore.removeChoice(payload.tableId, choice.address, choice.number);
                    deleteTable100ChoiceOnline(payload, choice.address, choice.number, true);
                    refreshTable100PlayScreen();
                    Toast.makeText(this, "Registro excluido.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private boolean table100IsOwner(GadgetStore.Table100Payload payload) {
        return payload != null && !payload.tableId.isEmpty() && payload.tableId.equals(gadgetStore.table100InstanceId());
    }

    private void sendTable100Choice(GadgetStore.Table100Payload payload, int number, boolean reserved, long reservationExpiresAt) {
        try {
            JSONObject json = new JSONObject();
            json.put("tableId", payload.tableId);
            json.put("number", number);
            json.put("name", profileStore.loadLocalProfile().getDisplayName());
            json.put("chooserDeviceId", StoreDeviceId.get(this));
            json.put("reserved", reserved);
            json.put("reservationExpiresAt", reservationExpiresAt);
            sendOrQueueOutgoing(currentRemoteAddress, messageStore.createId(), MessageStore.KIND_TABLE_100_CHOICE,
                    json.toString(), "", 0L, System.currentTimeMillis());
        } catch (Exception ignored) {
        }
    }

    private void chooseTable100NumberOnline(GadgetStore.Table100Payload payload, int number, String chooserDeviceId, String chooserName, boolean reserved) {
        if (storePaymentClient == null || payload == null || payload.tableId.isEmpty()) {
            return;
        }
        new Thread(() -> {
            try {
                StorePaymentClient.CartelaState state = storePaymentClient.chooseCartelaNumber(payload.tableId, chooserDeviceId, chooserName, number, reserved);
                boolean changed = gadgetStore.mergeOnlineCartela(state);
                if (changed) {
                    runOnUiThread(() -> refreshTable100IfOpen(payload.tableId));
                }
            } catch (Exception ex) {
                String message = ex.getMessage() == null ? "" : ex.getMessage();
                if ("number_taken".equals(message)) {
                    gadgetStore.removeChoice(payload.tableId, chooserDeviceId, number);
                    try {
                        StorePaymentClient.CartelaState state = storePaymentClient.getCartelaState(payload.tableId);
                        gadgetStore.mergeOnlineCartela(state);
                    } catch (Exception ignored) {
                    }
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Este numero ja foi escolhido por outra pessoa.", Toast.LENGTH_LONG).show();
                        refreshTable100IfOpen(payload.tableId);
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Escolha salva localmente. Vou sincronizar quando houver internet.", Toast.LENGTH_LONG).show());
                }
            }
        }, "nBTChat-cartela-choice").start();
    }

    private void sendTable100Confirmation(GadgetStore.Table100Payload payload, String address, int number, boolean confirmed) {
        if (address == null || address.startsWith("manual:") || address.startsWith("nbt-")) {
            return;
        }
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

    private void confirmTable100NumberOnline(GadgetStore.Table100Payload payload, String chooserDeviceId, int number, boolean confirmed) {
        if (storePaymentClient == null || payload == null || payload.tableId.isEmpty()) {
            return;
        }
        String ownerDeviceId = StoreDeviceId.get(this);
        new Thread(() -> {
            try {
                StorePaymentClient.CartelaState state = storePaymentClient.confirmCartelaNumber(payload.tableId, ownerDeviceId, chooserDeviceId, number, confirmed);
                boolean changed = gadgetStore.mergeOnlineCartela(state);
                if (changed) {
                    runOnUiThread(() -> refreshTable100IfOpen(payload.tableId));
                }
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(this, "Confirmacao salva localmente. Vou sincronizar quando possivel.", Toast.LENGTH_LONG).show());
            }
        }, "nBTChat-cartela-confirm").start();
    }

    private void renameTable100ChoiceOnline(GadgetStore.Table100Payload payload, String chooserDeviceId, int number, String name) {
        if (storePaymentClient == null || payload == null || payload.tableId.isEmpty()) {
            return;
        }
        String ownerDeviceId = StoreDeviceId.get(this);
        new Thread(() -> {
            try {
                StorePaymentClient.CartelaState state = storePaymentClient.renameCartelaChoice(payload.tableId, ownerDeviceId, chooserDeviceId, number, name);
                boolean changed = gadgetStore.mergeOnlineCartela(state);
                if (changed) {
                    runOnUiThread(() -> refreshTable100IfOpen(payload.tableId));
                }
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(this, "Nome salvo localmente. Vou sincronizar quando possivel.", Toast.LENGTH_LONG).show());
            }
        }, "nBTChat-cartela-rename").start();
    }

    private void restoreTable100ChoiceOnline(GadgetStore.Table100Payload payload, String chooserDeviceId, int number) {
        if (storePaymentClient == null || payload == null || payload.tableId.isEmpty()) {
            return;
        }
        String ownerDeviceId = StoreDeviceId.get(this);
        new Thread(() -> {
            try {
                StorePaymentClient.CartelaState state = storePaymentClient.restoreCartelaChoice(payload.tableId, ownerDeviceId, chooserDeviceId, number);
                boolean changed = gadgetStore.mergeOnlineCartela(state);
                if (changed) {
                    runOnUiThread(() -> refreshTable100IfOpen(payload.tableId));
                }
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(this, "Restauracao salva localmente. Vou sincronizar quando possivel.", Toast.LENGTH_LONG).show());
            }
        }, "nBTChat-cartela-restore").start();
    }

    private void deleteTable100ChoiceOnline(GadgetStore.Table100Payload payload, String chooserDeviceId, int number, boolean permanent) {
        if (storePaymentClient == null || payload == null || payload.tableId.isEmpty()) {
            return;
        }
        String ownerDeviceId = StoreDeviceId.get(this);
        new Thread(() -> {
            try {
                StorePaymentClient.CartelaState state = storePaymentClient.deleteCartelaChoice(payload.tableId, ownerDeviceId, chooserDeviceId, number, permanent);
                boolean changed = gadgetStore.mergeOnlineCartela(state);
                if (changed) {
                    runOnUiThread(() -> refreshTable100IfOpen(payload.tableId));
                }
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(this, "Remocao salva localmente. Vou sincronizar quando possivel.", Toast.LENGTH_LONG).show());
            }
        }, "nBTChat-cartela-delete").start();
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
                String chooserDeviceId = json.optString("chooserDeviceId", address);
                gadgetStore.saveChoice(tableId, chooserDeviceId, number, name, false,
                        json.optBoolean("reserved", false), json.optLong("reservationExpiresAt", 0L));
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
        LoadingRingView ring = statusRingView(chatAvatarFrame);
        if (ring != null) {
            ring.setRingColor(color(presenceColor(presence)));
        }
        if (chatConnectionIcon != null) {
            chatConnectionIcon.setImageResource(presenceDrawable(presence));
            chatConnectionIcon.setContentDescription(presenceLabel(presence));
            chatConnectionIcon.setBackground(rounded("#FFFFFF", dp(11), "#FFFFFF"));
        }
        updateChatAvatarSpin();
    }

    private void updateChatAvatarSpin() {
        if (chatAvatarFrame == null) {
            chatAvatarSpinning = false;
            return;
        }
        LoadingRingView ring = statusRingView(chatAvatarFrame);
        if (ring == null) {
            chatAvatarSpinning = false;
            return;
        }
        boolean shouldSpin = "chat".equals(currentScreen)
                && currentRemoteAddress != null
                && !currentRemoteAddress.isEmpty()
                && btChatManager != null
                && !btChatManager.canSendTo(currentRemoteAddress);
        ring.setLoading(shouldSpin);
        if (shouldSpin && !chatAvatarSpinning) {
            chatAvatarSpinning = true;
            spinChatAvatarFrame();
        } else if (!shouldSpin && chatAvatarSpinning) {
            chatAvatarSpinning = false;
            ring.animate().cancel();
            ring.setRotation(0f);
            ring.setLoading(false);
        }
    }

    private void spinChatAvatarFrame() {
        LoadingRingView ring = statusRingView(chatAvatarFrame);
        if (!chatAvatarSpinning || ring == null) {
            return;
        }
        ring.animate()
                .rotationBy(360f)
                .setDuration(1100L)
                .setInterpolator(new LinearInterpolator())
                .withEndAction(this::spinChatAvatarFrame)
                .start();
    }

    private LoadingRingView statusRingView(FrameLayout frame) {
        View view = frame == null ? null : frame.findViewWithTag("statusRing");
        return view instanceof LoadingRingView ? (LoadingRingView) view : null;
    }

    private void updateChatHeaderProfile() {
        if (chatTitleText != null) {
            String title = currentRemoteProfile.isComplete() ? currentRemoteProfile.getDisplayName() : "Conversa Bluetooth";
            chatTitleText.setText(safeName(title, "Conversa Bluetooth"));
        }
        if (chatSubtitleText != null) {
            String subtitle;
            String subtitleColor = secondary();
            if (isRemoteTyping(currentRemoteAddress)) {
                subtitle = "Digitando";
                subtitleColor = "#16A34A";
            } else if (profileStore.isBlocked(currentRemoteAddress)) {
                subtitle = "Bloqueado";
            } else {
                subtitle = currentRemoteProfile.getStatus().isEmpty() ? "Bluetooth seguro" : currentRemoteProfile.getStatus();
            }
            chatSubtitleText.setText(subtitle);
            chatSubtitleText.setTextColor(color(subtitleColor));
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
        lastConnectAttemptAt.remove(address);
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
                addMessageBubble(id, body, false, kind, mediaBase64, durationMs, MessageStore.STATUS_DELIVERED, replyToId, replyPreview, sentAt, true);
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
                addMessageBubble(message.id, message.body, message.mine, message.kind, message.mediaBase64, message.durationMs, message.status, message.replyToId, message.replyPreview, message.sentAt, true);
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

    private void shareBackupZip() {
        new Thread(() -> {
            try {
                File backup = new BackupStore().createZipBackup(this);
                Uri uri = BackupFileProvider.uriFor(this, backup);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("application/zip");
                intent.putExtra(Intent.EXTRA_SUBJECT, "Backup nBTChat");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.setClipData(ClipData.newUri(getContentResolver(), "Backup nBTChat", uri));
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                uiHandler.post(() -> startActivity(Intent.createChooser(intent, "Enviar backup nBTChat")));
            } catch (Exception ex) {
                uiHandler.post(() -> Toast.makeText(this, "Nao foi possivel criar o backup.", Toast.LENGTH_LONG).show());
            }
        }, "nBTChat-backup-export").start();
    }

    private void confirmRestoreBackup() {
        new AlertDialog.Builder(this)
                .setTitle("Restaurar backup?")
                .setMessage("A restauracao substitui os dados locais do nBTChat neste aparelho. Use apenas um ZIP que voce criou e guardou em local seguro.")
                .setPositiveButton("Restaurar", (dialog, which) -> pickBackupZip())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void pickBackupZip() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/octet-stream"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_RESTORE_BACKUP);
        } catch (Exception ex) {
            Toast.makeText(this, "Nao foi possivel abrir o seletor de arquivo.", Toast.LENGTH_LONG).show();
        }
    }

    private void restoreBackupFromUri(Uri uri) {
        new Thread(() -> {
            try {
                BackupStore.RestoreResult result = new BackupStore().restoreZipBackup(this, uri);
                uiHandler.post(() -> {
                    Toast.makeText(this, "Backup restaurado: " + result.values + " itens.", Toast.LENGTH_LONG).show();
                    recreate();
                });
            } catch (Exception ex) {
                uiHandler.post(() -> Toast.makeText(this, "Nao foi possivel restaurar este backup ZIP.", Toast.LENGTH_LONG).show());
            }
        }, "nBTChat-backup-restore").start();
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

        LinearLayout privacyRow = horizontal();
        privacyRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout privacyText = vertical();
        privacyText.addView(text("Permitir compartilharem meu contato", 16, primary(), Typeface.BOLD));
        TextView privacyHint = text("Se desligado, seus contatos nao devem repassar seus dados Bluetooth pelo nBTChat.", 13, secondary(), Typeface.NORMAL);
        privacyHint.setLineSpacing(dp(2), 1f);
        privacyText.addView(privacyHint, topMargin(dp(3)));
        privacyRow.addView(privacyText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Switch privacyToggle = new Switch(this);
        privacyToggle.setChecked(settingsStore.contactSharingEnabled());
        privacyToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settingsStore.setContactSharingEnabled(isChecked);
            btChatManager.sendProfileUpdate();
            notifyProfileUpdated();
        });
        privacyRow.addView(privacyToggle);
        card.addView(privacyRow, topMargin(dp(20)));

        TextView backupTitle = text("Backup", 16, primary(), Typeface.BOLD);
        card.addView(backupTitle, topMargin(dp(22)));
        TextView backupHint = text("O arquivo ZIP guarda mensagens, perfil, contatos e chaves locais. Mantenha em um lugar privado e seguro.", 13, secondary(), Typeface.NORMAL);
        backupHint.setLineSpacing(dp(2), 1f);
        card.addView(backupHint, topMargin(dp(4)));
        LinearLayout backupActions = horizontal();
        Button exportBackup = pillButton("Enviar backup ZIP", "#16A34A", "#FFFFFF");
        exportBackup.setOnClickListener(v -> shareBackupZip());
        backupActions.addView(exportBackup, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button restoreBackup = pillButton("Restaurar", surfaceAlt(), primary());
        restoreBackup.setOnClickListener(v -> confirmRestoreBackup());
        LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        restoreParams.setMargins(dp(8), 0, 0, 0);
        backupActions.addView(restoreBackup, restoreParams);
        card.addView(backupActions, topMargin(dp(10)));

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
        boolean shareAllowed = profileStore.isContactShareAllowed(address);
        menu.getMenu().add(0, 1, 0, "Apagar conversa");
        menu.getMenu().add(0, 2, 1, muted ? "Ativar notificacoes" : "Silenciar contato");
        menu.getMenu().add(0, 3, 2, blocked ? "Desbloquear contato" : "Bloquear contato");
        menu.getMenu().add(0, 5, 3, shareAllowed ? "Compartilhar contato Bluetooth" : "Contato nao permite compartilhar");
        menu.getMenu().add(0, 4, 4, "Remover contato e pareamento");
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
            if (item.getItemId() == 5) {
                if (!profileStore.isContactShareAllowed(address)) {
                    Toast.makeText(this, "Este contato nao permitiu que os dados Bluetooth dele sejam compartilhados.", Toast.LENGTH_LONG).show();
                    return true;
                }
                showContactShareChooser(address);
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

    private void showContactShareChooser(String sourceAddress) {
        ContactCardPayload payload = contactCardPayload(sourceAddress);
        if (payload == null) {
            Toast.makeText(this, "Nao tenho dados suficientes desse contato para compartilhar.", Toast.LENGTH_LONG).show();
            return;
        }
        Set<String> excluded = new HashSet<>();
        excluded.add(sourceAddress);
        showShareTargetScreen("Compartilhar " + safeName(payload.name, "Contato"),
                "Nenhum outro contato para receber este contato.",
                excluded,
                address -> sendContactCardToAddress(address, payload));
    }

    private ContactCardPayload contactCardPayload(String sourceAddress) {
        if (sourceAddress == null || sourceAddress.trim().isEmpty()) {
            return null;
        }
        if (!profileStore.isContactShareAllowed(sourceAddress)) {
            return null;
        }
        UserProfile profile = profileStore.loadContact(sourceAddress);
        ProfileStore.ContactIdentity identity = profileStore.loadIdentity(sourceAddress);
        BtChatManager.DeviceCandidate candidate = btChatManager.getPairedCandidate(sourceAddress);
        String name = profile.isComplete() ? profile.getDisplayName() : (candidate == null ? "Contato nBTChat" : candidate.name);
        String bluetoothName = candidate == null
                ? (identity.bluetoothName.isEmpty() ? name : identity.bluetoothName)
                : candidate.name;
        if ((sourceAddress.trim().isEmpty() || !QrInvite.validBluetoothAddress(sourceAddress))
                && (identity.deviceId == null || identity.deviceId.isEmpty())) {
            return null;
        }
        return new ContactCardPayload(sourceAddress, bluetoothName, identity.deviceId, identity.identityPublicKey, profile, name, true);
    }

    private ContactCardPayload localContactCardPayload() {
        UserProfile profile = profileStore.loadLocalProfile();
        if (!profile.isComplete()) {
            return null;
        }
        String address = btChatManager == null ? "" : btChatManager.localBluetoothAddress();
        String bluetoothName = btChatManager == null ? "" : btChatManager.localBluetoothName();
        String deviceId = identityStore.getDeviceId();
        if (address.trim().isEmpty() && !deviceId.trim().isEmpty()) {
            address = "nbt-" + deviceId;
        }
        String name = safeName(profile.getDisplayName(), "Contato nBTChat");
        if (bluetoothName.trim().isEmpty()) {
            bluetoothName = name;
        }
        return new ContactCardPayload(address, bluetoothName, deviceId, identityStore.getPublicKeyBase64(), profile, name, settingsStore.contactSharingEnabled());
    }

    private void sendContactCardToAddress(String address, ContactCardPayload payload) {
        if (address == null || address.trim().isEmpty() || payload == null) {
            return;
        }
        long sentAt = System.currentTimeMillis();
        String id = messageStore.createId();
        String body = payload.toJson();
        messageStore.addMessage(address, id, MessageStore.KIND_CONTACT_INVITE, body, "", 0L, true, sentAt, MessageStore.STATUS_PENDING, false);
        sendOrQueueOutgoing(address, id, MessageStore.KIND_CONTACT_INVITE, body, "", 0L, sentAt);
        Toast.makeText(this, "Contato compartilhado no nBTChat.", Toast.LENGTH_SHORT).show();
        openSharedChat(address);
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
        if (MessageStore.KIND_TABLE_100.equals(message.kind)) {
            actions.add("Abrir");
            actions.add("Compartilhar");
            actions.add("Compartilhar link");
        } else {
            actions.add("Copiar");
            actions.add("Compartilhar");
        }
        actions.add("Remover");
        actions.add("Responder");
        new AlertDialog.Builder(this)
                .setTitle("Mensagem selecionada")
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    String action = actions.get(which);
                    if ("Abrir".equals(action)) {
                        showTable100PlayScreen(message.body);
                    } else if ("Copiar".equals(action)) {
                        if (message.body != null && !message.body.trim().isEmpty()) {
                            copyMessageText(message.body);
                        } else {
                            Toast.makeText(this, "Esta mensagem nao tem texto para copiar.", Toast.LENGTH_SHORT).show();
                        }
                    } else if ("Compartilhar".equals(action)) {
                        showInternalShareChooser(message);
                    } else if ("Compartilhar link".equals(action)) {
                        shareMessageExternalLink(message);
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
            return message.mine ? "Voce enviou uma cartela de eventos" : GadgetStore.TABLE_100_TITLE;
        }
        if (MessageStore.KIND_CONTACT_INVITE.equals(message.kind)) {
            return message.mine ? "Voce enviou um contato" : "Contato nBTChat";
        }
        String body = message.body == null ? "" : message.body.trim();
        if (body.length() > 80) {
            return body.substring(0, 77) + "...";
        }
        return body;
    }

    private void showInternalShareChooser(MessageStore.ChatMessage source) {
        Set<String> excluded = new HashSet<>();
        excluded.add(currentRemoteAddress);
        showShareTargetScreen("Compartilhar com",
                "Nenhum outro contato nBTChat para compartilhar.",
                excluded,
                address -> shareMessageToContact(address, source));
    }

    private void shareMessageToContact(String address, MessageStore.ChatMessage source) {
        long sentAt = System.currentTimeMillis();
        String id = messageStore.createId();
        String body = source.body == null ? "" : source.body;
        String media = source.mediaBase64 == null ? "" : source.mediaBase64;
        if (MessageStore.KIND_TABLE_100.equals(source.kind)) {
            body = table100BodyWithKnownLocks(body);
        }
        messageStore.addMessage(address, id, source.kind, body, media, source.durationMs, true, sentAt, MessageStore.STATUS_PENDING, false);
        sendOrQueueOutgoing(address, id, source.kind, body, media, source.durationMs, sentAt);
        Toast.makeText(this, "Mensagem compartilhada no nBTChat.", Toast.LENGTH_SHORT).show();
        openSharedChat(address);
    }

    private void sharePayloadToContact(String address, String kind, String body) {
        if (address == null || address.trim().isEmpty() || kind == null || kind.trim().isEmpty()) {
            return;
        }
        String finalBody = body == null ? "" : body;
        if (MessageStore.KIND_TABLE_100.equals(kind)) {
            finalBody = table100BodyWithKnownLocks(finalBody);
        }
        long sentAt = System.currentTimeMillis();
        String id = messageStore.createId();
        messageStore.addMessage(address, id, kind, finalBody, "", 0L, true, sentAt, MessageStore.STATUS_PENDING, false);
        sendOrQueueOutgoing(address, id, kind, finalBody, "", 0L, sentAt);
        Toast.makeText(this, "Item compartilhado no nBTChat.", Toast.LENGTH_SHORT).show();
        openSharedChat(address);
    }

    private void openSharedChat(String address) {
        if (address == null || address.trim().isEmpty()) {
            return;
        }
        currentRemoteAddress = address;
        UserProfile profile = profileStore.loadContact(address);
        currentRemoteProfile = profile.isComplete() ? profile : new UserProfile("Contato nBTChat", "", UserProfile.GENDER_OTHER, "");
        currentFingerprint = profileStore.loadFingerprint(address);
        showChatScreen(currentRemoteProfile, currentFingerprint);
    }

    private void shareMessageExternalLink(MessageStore.ChatMessage source) {
        if (source == null || !MessageStore.KIND_TABLE_100.equals(source.kind)) {
            Toast.makeText(this, "Esta mensagem nao pode virar link externo.", Toast.LENGTH_LONG).show();
            return;
        }
        ContactCardPayload contact = localContactCardPayload();
        if (contact == null) {
            Toast.makeText(this, "Configure seu perfil antes de compartilhar link.", Toast.LENGTH_LONG).show();
            return;
        }
        String body = table100BodyWithKnownLocks(source.body == null ? "" : source.body);
        String sharePayload = buildStoreSharePayload(source.kind, body, contact);
        if (sharePayload.isEmpty()) {
            Toast.makeText(this, "Nao foi possivel criar o link.", Toast.LENGTH_LONG).show();
            return;
        }
        shareExternalStorePayload(GadgetStore.TABLE_100_TITLE, sharePayload, "Compartilhar link");
    }

    private void shareExternalStorePayload(String subject, String encodedPayload, String chooserTitle) {
        new Thread(() -> {
            String finalUrl = "";
            try {
                if (storePaymentClient != null) {
                    finalUrl = storePaymentClient.createShareLink(encodedPayload);
                }
            } catch (Exception ignored) {
            }
            String shareUrl = finalUrl;
            runOnUiThread(() -> {
                if (shareUrl == null || shareUrl.trim().isEmpty()) {
                    Toast.makeText(this, "Nao foi possivel gerar o link curto. Verifique a internet e tente novamente.", Toast.LENGTH_LONG).show();
                    return;
                }
                copyToClipboard(shareUrl, false);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_SUBJECT, subject == null ? "nBTChat" : subject);
                intent.putExtra(Intent.EXTRA_TEXT, "Abra este item no nBTChat: " + shareUrl);
                Uri imageUri = cartelaShareImageUri();
                if (imageUri != null && GadgetStore.TABLE_100_TITLE.equals(subject)) {
                    intent.setType("image/png");
                    intent.putExtra(Intent.EXTRA_STREAM, imageUri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else {
                    intent.setType("text/plain");
                }
                startActivity(Intent.createChooser(intent, chooserTitle == null ? "Compartilhar link" : chooserTitle));
                Toast.makeText(this, "Link curto copiado.", Toast.LENGTH_SHORT).show();
            });
        }, "nBTChat-short-link").start();
    }

    private Uri cartelaShareImageUri() {
        try {
            File dir = new File(getCacheDir(), "backups");
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File file = new File(dir, "cartela_eventos.png");
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.cartela_eventos_bg);
            if (bitmap == null) {
                return null;
            }
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            }
            return BackupFileProvider.uriFor(this, file);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void showUpdateDialog() {
        StringBuilder message = new StringBuilder();
        if (updateVersionName.isEmpty()) {
            message.append("Uma nova versao do nBTChat esta pronta.");
        } else {
            message.append("Versao ").append(updateVersionName).append(" disponivel.");
        }
        if (!updateChangelog.trim().isEmpty()) {
            message.append("\n\nNovidades:\n").append(updateChangelog.trim());
        }
        message.append("\n\nOrigem: ").append(updateOrigin == null || updateOrigin.trim().isEmpty()
                ? "fonte oficial do nBTChat"
                : updateOrigin.trim());
        new AlertDialog.Builder(this)
                .setTitle("Atualizacao disponivel")
                .setMessage(message.toString())
                .setPositiveButton("Baixar e instalar", (dialog, which) -> downloadAndInstallUpdateApk())
                .setNeutralButton("Abrir pagina", (dialog, which) -> openExternalLink(Uri.parse(updatePageUrl)))
                .setNegativeButton("Agora nao", null)
                .show();
    }

    private void downloadAndInstallUpdateApk() {
        Toast.makeText(this, "Baixando atualizacao...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(updateApkUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(20_000);
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new Exception("Servidor respondeu " + code);
                }
                File dir = new File(getCacheDir(), "backups");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new Exception("Nao foi possivel preparar o download.");
                }
                File apk = new File(dir, "nBTChat-update.apk");
                byte[] buffer = new byte[8192];
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(apk)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }
                runOnUiThread(() -> installDownloadedUpdate(apk));
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(this, "Nao foi possivel baixar a atualizacao.", Toast.LENGTH_LONG).show());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }, "nBTChat-apk-download").start();
    }

    private void installDownloadedUpdate(File apk) {
        if (apk == null || !apk.exists()) {
            Toast.makeText(this, "APK de atualizacao nao encontrado.", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "Autorize o nBTChat a instalar atualizacoes e toque em baixar novamente.", Toast.LENGTH_LONG).show();
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            return;
        }
        Uri uri = BackupFileProvider.uriFor(this, apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(intent);
        } catch (Exception ex) {
            Toast.makeText(this, "Nao encontrei o instalador do Android.", Toast.LENGTH_LONG).show();
        }
    }

    private void startPeriodicUpdateChecks(boolean checkNow) {
        stopPeriodicUpdateChecks();
        updateCheckRunnable = new Runnable() {
            @Override
            public void run() {
                checkForUpdates(false);
                uiHandler.postDelayed(this, UPDATE_CHECK_INTERVAL_MS);
            }
        };
        if (checkNow) {
            updateCheckRunnable.run();
        } else {
            uiHandler.postDelayed(updateCheckRunnable, UPDATE_CHECK_INTERVAL_MS);
        }
    }

    private void stopPeriodicUpdateChecks() {
        if (updateCheckRunnable != null) {
            uiHandler.removeCallbacks(updateCheckRunnable);
            updateCheckRunnable = null;
        }
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
                    String changelog = json.optString("changelog", "");
                    String origin = json.optString("origin", "GitHub Pages oficial do nBTChat");
                    boolean critical = json.optBoolean("critical", false);
                    int currentCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                    runOnUiThread(() -> {
                        updateAvailable = latestCode > currentCode;
                        updateVersionName = latestName;
                        updatePageUrl = pageUrl;
                        updateApkUrl = apkUrl;
                        updateChangelog = changelog;
                        updateOrigin = origin;
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
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_PROFILE_CAMERA);
            return;
        }
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

    private void deleteCameraUri(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            getContentResolver().delete(uri, null, null);
        } catch (Exception ignored) {
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

    private String compressProfileImage(Uri uri) {
        return compressImage(uri, PROFILE_IMAGE_MAX_SIDE, 84);
    }

    private String compressChatImage(Uri uri) {
        return compressImage(uri, CHAT_IMAGE_MAX_SIDE, 90);
    }

    private String compressImage(Uri uri, int maxSide, int quality) {
        try {
            Bitmap original = decodeBitmapWithOrientation(uri);
            if (original == null) {
                return "";
            }
            return compressBitmap(original, maxSide, quality);
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
        String imageBase64 = compressChatImage(uri);
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

    private String compressBitmap(Bitmap original, int maxSide, int quality) {
        int max = Math.max(original.getWidth(), original.getHeight());
        int target = Math.max(128, maxSide);
        float scale = max > target ? (float) target / max : 1f;
        Bitmap scaled = Bitmap.createScaledBitmap(
                original,
                Math.max(1, Math.round(original.getWidth() * scale)),
                Math.max(1, Math.round(original.getHeight() * scale)),
                true
        );
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, Math.max(60, Math.min(95, quality)), outputStream);
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
        }
    }

    private Drawable mediaDrawable(String kind, String base64) {
        if (base64 == null || base64.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(base64, Base64.NO_WRAP);
            Bitmap bitmap = decodeMediaBitmap(bytes, MessageStore.KIND_GIF.equals(kind) ? 420 : 1200);
            return bitmap == null ? null : new BitmapDrawable(getResources(), bitmap);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Bitmap decodeMediaBitmap(byte[] bytes, int targetMax) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetMax);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    }

    private void addGifBadge(LinearLayout bubble, boolean mine) {
        TextView badge = text("GIF", 11, mine ? "#FFFFFF" : "#0F766E", Typeface.BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        badge.setBackground(rounded(mine ? "#0C5F58" : "#DFF4EC", dp(10), mine ? "#7DD3FC" : "#16A34A"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(6));
        bubble.addView(badge, params);
    }

    private FrameLayout voiceAvatar(UserProfile profile, boolean mine) {
        FrameLayout frame = new FrameLayout(this);
        ImageView avatar = new ImageView(this);
        applyAvatar(avatar, profile);
        frame.addView(avatar, new FrameLayout.LayoutParams(dp(42), dp(42)));

        ImageView mic = new ImageView(this);
        mic.setImageResource(R.drawable.ic_mic_24);
        mic.setColorFilter(color("#FFFFFF"));
        mic.setPadding(dp(2), dp(2), dp(2), dp(2));
        mic.setBackground(rounded("#16A34A", dp(8), "#16A34A"));
        FrameLayout.LayoutParams micParams = new FrameLayout.LayoutParams(
                dp(17),
                dp(17),
                (mine ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM
        );
        frame.addView(mic, micParams);
        frame.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return frame;
    }

    private void applyVoiceSeekBarTint(SeekBar seekBar, boolean heard) {
        int thumb = color(heard ? "#7DD3FC" : "#16A34A");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.setThumbTintList(ColorStateList.valueOf(thumb));
        }
    }

    private void setCellTextForStatus(Button cell, int status, int number, boolean fullScreen, boolean mine) {
        if (status == 2) {
            cell.setText("X");
            cell.setTextColor(color("#DC2626"));
            cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            return;
        }
        if (status == 1) {
            cell.setText("\u23F3");
            cell.setTextColor(color("#FACC15"));
            cell.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            return;
        }
        cell.setText(String.valueOf(number));
        cell.setTextColor(color(fullScreen ? "#FFFFFF" : (mine ? "#FFFFFF" : primary())));
    }

    private FrameLayout avatarStatusFrame(UserProfile profile, String presence, int size, int radius, int borderWidth, boolean badge, View.OnClickListener clickListener) {
        FrameLayout frame = new FrameLayout(this);
        LoadingRingView ring = new LoadingRingView(this, borderWidth);
        ring.setTag("statusRing");
        ring.setRingColor(color(presenceColor(presence)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ring.setClipToOutline(true);
        }
        frame.addView(ring, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        ImageView avatar = new ImageView(this);
        applyAvatar(avatar, profile);
        avatar.setBackground(ovalStroke(surfaceAlt(), surfaceAlt(), dp(1)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            avatar.setClipToOutline(true);
        }
        if (clickListener != null) {
            avatar.setOnClickListener(clickListener);
            frame.setOnClickListener(clickListener);
        }
        frame.addView(avatar, new FrameLayout.LayoutParams(
                size - (borderWidth * 2),
                size - (borderWidth * 2),
                Gravity.CENTER
        ));
        if (badge) {
            ImageView status = new ImageView(this);
            status.setTag("presence");
            status.setImageResource(presenceDrawable(presence));
            status.setBackground(ovalStroke("#FFFFFF", "#FFFFFF", dp(1)));
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
        imageView.setBackground(ovalStroke(surfaceAlt(), surfaceAlt(), dp(1)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            imageView.setClipToOutline(true);
        }
        imageView.post(() -> {
            int size = Math.min(imageView.getWidth(), imageView.getHeight());
            if (size > 0) {
                imageView.setBackground(ovalStroke(surfaceAlt(), surfaceAlt(), dp(1)));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    imageView.setClipToOutline(true);
                }
            }
        });
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

    private final class LoadingRingView extends View {
        private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final int strokeWidth;
        private int ringColor = color("#9CA3AF");
        private boolean loading;

        LoadingRingView(Context context, int strokeWidth) {
            super(context);
            this.strokeWidth = Math.max(1, strokeWidth);
            basePaint.setStyle(Paint.Style.STROKE);
            basePaint.setStrokeWidth(this.strokeWidth);
            basePaint.setStrokeCap(Paint.Cap.ROUND);
            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeWidth(this.strokeWidth);
            arcPaint.setStrokeCap(Paint.Cap.ROUND);
        }

        void setRingColor(int value) {
            ringColor = value;
            invalidate();
        }

        void setLoading(boolean value) {
            if (loading != value) {
                loading = value;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = strokeWidth / 2f + 1f;
            bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
            arcPaint.setColor(ringColor);
            if (!loading) {
                canvas.drawOval(bounds, arcPaint);
                return;
            }
            basePaint.setColor(adjustAlpha(ringColor, 0.24f));
            canvas.drawOval(bounds, basePaint);
            canvas.drawArc(bounds, -90f, 96f, false, arcPaint);
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

    private static final class CartelaPurchaseLine {
        int days;

        CartelaPurchaseLine(int days) {
            this.days = Math.max(1, Math.min(365, days));
        }
    }

    private static final class ContactCardPayload {
        final String address;
        final String bluetoothName;
        final String deviceId;
        final String publicKey;
        final UserProfile profile;
        final String name;
        final boolean allowContactSharing;

        ContactCardPayload(String address, String bluetoothName, String deviceId, String publicKey, UserProfile profile, String name, boolean allowContactSharing) {
            this.address = address == null ? "" : address.trim();
            this.bluetoothName = bluetoothName == null ? "" : bluetoothName.trim();
            this.deviceId = deviceId == null ? "" : deviceId.trim();
            this.publicKey = publicKey == null ? "" : publicKey.trim();
            this.profile = profile == null ? UserProfile.empty() : profile;
            this.name = name == null ? "" : name.trim();
            this.allowContactSharing = allowContactSharing;
        }

        String toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "nbtchat-contact");
                json.put("address", address);
                json.put("bluetoothName", bluetoothName);
                json.put("deviceId", deviceId);
                json.put("publicKey", publicKey);
                json.put("name", name);
                json.put("profile", profile.toJson());
                json.put("allowContactSharing", allowContactSharing);
                return json.toString();
            } catch (Exception ignored) {
                return "";
            }
        }

        static ContactCardPayload parse(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return null;
            }
            try {
                JSONObject json = new JSONObject(raw);
                if (!"nbtchat-contact".equals(json.optString("type", ""))) {
                    return null;
                }
                UserProfile profile = UserProfile.fromJson(json.optJSONObject("profile"));
                String name = json.optString("name", "");
                if (name.trim().isEmpty() && profile.isComplete()) {
                    name = profile.getDisplayName();
                }
                return new ContactCardPayload(
                        json.optString("address", ""),
                        json.optString("bluetoothName", ""),
                        json.optString("deviceId", ""),
                        json.optString("publicKey", ""),
                        profile,
                        name,
                        json.optBoolean("allowContactSharing", false)
                );
            } catch (Exception ignored) {
                return null;
            }
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

    private interface ShareTargetAction {
        void send(String address);
    }

    private static final class VoiceControls {
        final ImageButton button;
        final SeekBar seekBar;
        final TextView time;
        final long durationMs;
        final boolean mine;

        VoiceControls(ImageButton button, SeekBar seekBar, TextView time, long durationMs, boolean mine) {
            this.button = button;
            this.seekBar = seekBar;
            this.time = time;
            this.durationMs = durationMs;
            this.mine = mine;
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

    private GradientDrawable ovalStroke(String fill, String stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color(fill));
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

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * Math.max(0f, Math.min(1f, factor)));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
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
