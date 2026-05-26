package com.mbzerker.nbtchat;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.Parcelable;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BtChatManager {
    private static final String SERVICE_NAME = "nBTChat";
    public static final UUID SERVICE_UUID = UUID.fromString("66a14f52-9c02-4c04-903d-0cdd8755a5f7");
    private static final int MAX_FRAME_BYTES = 1024 * 1024;
    private static final long SERVICE_CHECK_TIMEOUT_MS = 9000;

    public interface Listener {
        void onBluetoothState(String state);

        void onDeviceFound(DeviceCandidate candidate);

        void onDiscoveryFinished();

        void onIncomingConnection(String remoteName, String remoteAddress);

        void onRemoteProfile(String remoteAddress, UserProfile profile);

        void onConnected(String remoteAddress, UserProfile profile, String fingerprint);

        void onRemoteIdentity(String remoteAddress, String deviceId, String identityPublicKey);

        void onIdentityWarning(String remoteAddress, String status, String deviceId, String identityPublicKey, String fingerprint);

        void onMessageReceived(String remoteAddress, String id, String kind, String body, String mediaBase64, long durationMs, long sentAt, String replyToId, String replyPreview);

        void onReceiptReceived(String remoteAddress, String id, String status);

        void onMessageDeleted(String remoteAddress, String id);

        void onTypingReceived(String remoteAddress, boolean typing);

        void onPresenceReceived(String remoteAddress, String status);

        void onDisconnected(String remoteAddress);

        void onError(String message);
    }

    public static final class DeviceCandidate {
        public final BluetoothDevice device;
        public final String name;
        public final String address;
        public final boolean paired;
        public final boolean appAvailable;

        DeviceCandidate(BluetoothDevice device, String name, String address, boolean paired, boolean appAvailable) {
            this.device = device;
            this.name = name;
            this.address = address;
            this.paired = paired;
            this.appAvailable = appAvailable;
        }
    }

    private final Context appContext;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    private final ProfileStore profileStore;
    private final IdentityStore identityStore;
    private final RelayStore relayStore;
    private final AppSettingsStore settingsStore;
    private static final int PROTOCOL_V2 = 2;

    private AcceptThread secureAcceptThread;
    private AcceptThread insecureAcceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private boolean receiverRegistered;
    private boolean nearbyDiscoveryMode;
    private final Set<String> pendingServiceChecks = new HashSet<>();
    private final Map<String, BluetoothDevice> serviceCheckDevices = new HashMap<>();

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    if (nearbyDiscoveryMode) {
                        postDevice(device, isBonded(device), false);
                    } else {
                        checkDeviceService(device);
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                mainHandler.post(listener::onDiscoveryFinished);
            } else if (BluetoothDevice.ACTION_UUID.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    handleServiceUuids(device, intent);
                }
            }
        }
    };

    public BtChatManager(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.profileStore = new ProfileStore(appContext);
        this.identityStore = new IdentityStore(appContext);
        this.relayStore = new RelayStore(appContext);
        this.settingsStore = new AppSettingsStore(appContext);
    }

    public boolean isBluetoothAvailable() {
        return adapter != null;
    }

    @SuppressLint("MissingPermission")
    public boolean isBluetoothEnabled() {
        return adapter != null && adapter.isEnabled();
    }

    @SuppressLint("MissingPermission")
    public void startListening() {
        if (adapter == null) {
            postError("Este aparelho nao tem Bluetooth disponivel.");
            return;
        }
        if (secureAcceptThread == null) {
            secureAcceptThread = new AcceptThread(false);
            secureAcceptThread.start();
        }
        if (insecureAcceptThread == null) {
            insecureAcceptThread = new AcceptThread(true);
            insecureAcceptThread.start();
        }
        postState("Pronto para receber conexoes Bluetooth.");
    }

    @SuppressLint("MissingPermission")
    public void startDiscovery() {
        if (adapter == null) {
            postError("Bluetooth indisponivel.");
            return;
        }
        registerReceiverIfNeeded();
        nearbyDiscoveryMode = false;
        postState("Procurando nBTChat em aparelhos pareados...");
        try {
            pendingServiceChecks.clear();
            serviceCheckDevices.clear();
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices != null && !bondedDevices.isEmpty()) {
                for (BluetoothDevice device : bondedDevices) {
                    checkDeviceService(device);
                }
            }
            if (adapter.isDiscovering()) {
                adapter.cancelDiscovery();
            }
            if (pendingServiceChecks.isEmpty()) {
                postState("Nenhum aparelho pareado para verificar.");
                mainHandler.post(listener::onDiscoveryFinished);
            } else {
                mainHandler.postDelayed(this::finishTimedOutServiceChecks, SERVICE_CHECK_TIMEOUT_MS);
            }
        } catch (SecurityException ex) {
            postError("Permissao Bluetooth negada. Libere o Bluetooth nas permissoes do app.");
        }
    }

    @SuppressLint("MissingPermission")
    public void startNearbyDiscovery() {
        if (adapter == null) {
            postError("Bluetooth indisponivel.");
            return;
        }
        registerReceiverIfNeeded();
        nearbyDiscoveryMode = true;
        pendingServiceChecks.clear();
        serviceCheckDevices.clear();
        postState("Procurando aparelhos proximos...");
        try {
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices != null) {
                for (BluetoothDevice device : bondedDevices) {
                    postDevice(device, true, false);
                }
            }
            if (adapter.isDiscovering()) {
                adapter.cancelDiscovery();
            }
            if (!adapter.startDiscovery()) {
                postError("Nao foi possivel iniciar a busca de aparelhos proximos.");
            }
        } catch (SecurityException ex) {
            postError("Permissao Bluetooth negada. Libere o Bluetooth nas permissoes do app.");
        }
    }

    @SuppressLint("MissingPermission")
    public String localBluetoothName() {
        if (adapter == null) {
            return "";
        }
        try {
            return adapter.getName() == null ? "" : adapter.getName();
        } catch (SecurityException ignored) {
            return "";
        }
    }

    @SuppressLint("MissingPermission")
    public String localBluetoothAddress() {
        if (adapter == null) {
            return "";
        }
        try {
            String address = adapter.getAddress();
            return QrInvite.validBluetoothAddress(address) ? address : "";
        } catch (SecurityException ignored) {
            return "";
        }
    }

    @SuppressLint("MissingPermission")
    public DeviceCandidate getPairedCandidate(String address) {
        if (adapter == null || address == null || address.trim().isEmpty()) {
            return null;
        }
        try {
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices == null) {
                return null;
            }
            for (BluetoothDevice device : bondedDevices) {
                if (address.equals(safeAddress(device))) {
                    return new DeviceCandidate(device, safeName(device), address, true, true);
                }
            }
        } catch (SecurityException ignored) {
        }
        return null;
    }

    @SuppressLint("MissingPermission")
    public Map<String, DeviceCandidate> pairedCandidatesByAddress() {
        Map<String, DeviceCandidate> candidates = new LinkedHashMap<>();
        if (adapter == null) {
            return candidates;
        }
        try {
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices == null) {
                return candidates;
            }
            for (BluetoothDevice device : bondedDevices) {
                String address = safeAddress(device);
                if (!address.isEmpty()) {
                    candidates.put(address, new DeviceCandidate(device, safeName(device), address, true, true));
                }
            }
        } catch (SecurityException ignored) {
        }
        return candidates;
    }

    @SuppressLint("MissingPermission")
    public void stopDiscovery() {
        if (adapter == null) {
            return;
        }
        try {
            if (adapter.isDiscovering()) {
                adapter.cancelDiscovery();
            }
        } catch (SecurityException ignored) {
        }
    }

    @SuppressLint("MissingPermission")
    public DeviceCandidate getDirectCandidate(String address, String bluetoothName) {
        if (adapter == null) {
            return null;
        }
        String cleanAddress = address == null ? "" : address.trim();
        if (QrInvite.validBluetoothAddress(cleanAddress)) {
            try {
                BluetoothDevice device = adapter.getRemoteDevice(cleanAddress);
                return new DeviceCandidate(device, safeName(device), safeAddress(device), isBonded(device), true);
            } catch (Exception ignored) {
            }
        }
        String cleanName = bluetoothName == null ? "" : bluetoothName.trim();
        if (cleanName.isEmpty()) {
            return null;
        }
        try {
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices == null) {
                return null;
            }
            for (BluetoothDevice device : bondedDevices) {
                String deviceName = safeName(device);
                if (cleanName.equalsIgnoreCase(deviceName)) {
                    return new DeviceCandidate(device, deviceName, safeAddress(device), true, true);
                }
            }
        } catch (SecurityException ignored) {
        }
        return null;
    }

    @SuppressLint("MissingPermission")
    public void connectDirect(DeviceCandidate candidate) {
        if (candidate == null || candidate.device == null) {
            postError("Nao encontrei este aparelho no Bluetooth. Pareie no Android ou deixe o aparelho visivel.");
            return;
        }
        try {
            if (candidate.device.getBondState() != BluetoothDevice.BOND_BONDED) {
                candidate.device.createBond();
                postState("Confirme o pareamento Bluetooth. Vou tentar conectar em seguida.");
                mainHandler.postDelayed(() -> connect(candidate), 5500);
                return;
            }
        } catch (SecurityException ignored) {
        }
        connect(candidate);
    }

    @SuppressLint("MissingPermission")
    public boolean wakeForMessage(String address) {
        if (adapter == null || address == null || address.trim().isEmpty()) {
            return false;
        }
        DeviceCandidate paired = getPairedCandidate(address);
        if (paired != null) {
            if (!isConnectedTo(address)) {
                connect(paired);
            }
            return true;
        }
        ProfileStore.ContactIdentity identity = profileStore.loadIdentity(address);
        if ((identity.deviceId == null || identity.deviceId.isEmpty())
                && (identity.identityPublicKey == null || identity.identityPublicKey.isEmpty())) {
            return false;
        }
        DeviceCandidate direct = getDirectCandidate(address, identity.bluetoothName);
        if (direct == null || direct.device == null) {
            postError("Nao consegui acordar este contato. Deixe o outro aparelho visivel ou pareie pelo Android.");
            return false;
        }
        connectDirect(direct);
        return true;
    }

    @SuppressLint("MissingPermission")
    public boolean repairPairingForMessage(String address) {
        if (adapter == null || address == null || address.trim().isEmpty()) {
            return false;
        }
        DeviceCandidate paired = getPairedCandidate(address);
        if (paired == null || paired.device == null) {
            return wakeForMessage(address);
        }
        try {
            postState("Refazendo pareamento Bluetooth para tentar corrigir erro de PIN...");
            java.lang.reflect.Method method = paired.device.getClass().getMethod("removeBond");
            method.invoke(paired.device);
            mainHandler.postDelayed(() -> {
                DeviceCandidate direct = getDirectCandidate(address, profileStore.loadIdentity(address).bluetoothName);
                if (direct != null) {
                    connectDirect(direct);
                }
            }, 2500L);
            return true;
        } catch (Exception ex) {
            postError("Nao consegui refazer o pareamento automaticamente. Remova e pareie novamente no Android.");
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    public void connect(DeviceCandidate candidate) {
        if (candidate == null || candidate.device == null) {
            return;
        }
        String address = candidate.address == null || candidate.address.isEmpty() ? safeAddress(candidate.device) : candidate.address;
        if (isConnectedTo(address)) {
            return;
        }
        if (connectThread != null) {
            connectThread.cancel();
        }
        try {
            if (adapter != null && adapter.isDiscovering()) {
                adapter.cancelDiscovery();
            }
        } catch (SecurityException ignored) {
        }
        postState("Conectando com " + candidate.name + "...");
        connectThread = new ConnectThread(candidate.device);
        connectThread.start();
    }

    public void sendMessage(String body) {
        sendChatMessage("", Long.toString(System.currentTimeMillis()), MessageStore.KIND_TEXT, body, "", 0L, System.currentTimeMillis(), "", "");
    }

    public void sendChatMessage(String destinationAddress, String id, String kind, String body, String mediaBase64, long durationMs, long sentAt) {
        sendChatMessage(destinationAddress, id, kind, body, mediaBase64, durationMs, sentAt, "", "");
    }

    public void sendChatMessage(String destinationAddress, String id, String kind, String body, String mediaBase64, long durationMs, long sentAt, String replyToId, String replyPreview) {
        ConnectedThread thread = connectedThread;
        if (thread == null) {
            postError("Nenhuma conversa conectada.");
            return;
        }
        thread.sendChatMessage(destinationAddress, id, kind, body, mediaBase64, durationMs, sentAt, replyToId, replyPreview);
    }

    public void sendDeleteMessage(String destinationAddress, String messageId) {
        ConnectedThread thread = connectedThread;
        if (thread != null) {
            thread.sendDeleteMessage(destinationAddress, messageId);
        }
    }

    public void sendReceipt(String destinationAddress, String messageId, String status) {
        ConnectedThread thread = connectedThread;
        if (thread != null) {
            thread.sendReceipt(destinationAddress, messageId, status);
        }
    }

    public void sendProfileUpdate() {
        ConnectedThread thread = connectedThread;
        if (thread != null) {
            thread.sendProfileUpdate();
        }
    }

    public void sendTyping(String destinationAddress, boolean typing) {
        ConnectedThread thread = connectedThread;
        if (thread != null) {
            thread.sendTyping(destinationAddress, typing);
        }
    }

    public void sendPresence(String status) {
        ConnectedThread thread = connectedThread;
        if (thread != null) {
            thread.sendPresence(status);
        }
    }

    public boolean canSendTo(String address) {
        ConnectedThread thread = connectedThread;
        if (thread == null) {
            return false;
        }
        if (address != null && address.equals(thread.getRemoteAddress())) {
            return true;
        }
        ProfileStore.ContactIdentity identity = profileStore.loadIdentity(address);
        return identity.deviceId != null && !identity.deviceId.isEmpty()
                && identity.identityPublicKey != null && !identity.identityPublicKey.isEmpty();
    }

    public boolean isConnectedTo(String address) {
        ConnectedThread thread = connectedThread;
        return thread != null && address != null && address.equals(thread.getRemoteAddress());
    }

    public void disconnectCurrent() {
        ConnectedThread thread = connectedThread;
        if (thread != null) {
            thread.cancel();
            connectedThread = null;
        }
        postState("Conexao encerrada.");
    }

    @SuppressLint("MissingPermission")
    public boolean unpair(String address) {
        DeviceCandidate candidate = getPairedCandidate(address);
        if (candidate == null || candidate.device == null) {
            return false;
        }
        try {
            java.lang.reflect.Method method = candidate.device.getClass().getMethod("removeBond");
            Object result = method.invoke(candidate.device);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    public void stop() {
        if (adapter != null) {
            try {
                if (adapter.isDiscovering()) {
                    adapter.cancelDiscovery();
                }
            } catch (SecurityException ignored) {
            }
        }
        if (secureAcceptThread != null) {
            secureAcceptThread.cancel();
            secureAcceptThread = null;
        }
        if (insecureAcceptThread != null) {
            insecureAcceptThread.cancel();
            insecureAcceptThread = null;
        }
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        if (receiverRegistered) {
            try {
                appContext.unregisterReceiver(discoveryReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            receiverRegistered = false;
        }
    }

    private void registerReceiverIfNeeded() {
        if (receiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothDevice.ACTION_UUID);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(discoveryReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            appContext.registerReceiver(discoveryReceiver, filter);
        }
        receiverRegistered = true;
    }

    private void handleSocket(BluetoothSocket socket, boolean incoming) {
        if (incoming && connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        ConnectedThread current = connectedThread;
        if (current != null) {
            current.cancel();
        }
        connectedThread = new ConnectedThread(socket, incoming);
        connectedThread.start();
    }

    @SuppressLint("MissingPermission")
    private void checkDeviceService(BluetoothDevice device) {
        String address = safeAddress(device);
        if (address.isEmpty() || pendingServiceChecks.contains(address)) {
            return;
        }
        try {
            pendingServiceChecks.add(address);
            serviceCheckDevices.put(address, device);
            if (!device.fetchUuidsWithSdp()) {
                pendingServiceChecks.remove(address);
                serviceCheckDevices.remove(address);
                finishServiceCheckIfIdle();
            }
        } catch (SecurityException ex) {
            pendingServiceChecks.remove(address);
            serviceCheckDevices.remove(address);
            postError("Permissao Bluetooth negada ao verificar apps pareados.");
        }
    }

    private void handleServiceUuids(BluetoothDevice device, Intent intent) {
        if (nearbyDiscoveryMode) {
            return;
        }
        String address = safeAddress(device);
        Parcelable[] uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
        boolean hasApp = false;
        if (uuids != null) {
            for (Parcelable value : uuids) {
                if (value instanceof ParcelUuid && SERVICE_UUID.equals(((ParcelUuid) value).getUuid())) {
                    hasApp = true;
                    break;
                }
            }
        }
        pendingServiceChecks.remove(address);
        serviceCheckDevices.remove(address);
        if (hasApp) {
            postDevice(device, true, true);
        }
        finishServiceCheckIfIdle();
    }

    private void finishServiceCheckIfIdle() {
        if (pendingServiceChecks.isEmpty()) {
            postState("Busca de nBTChat finalizada.");
            mainHandler.post(listener::onDiscoveryFinished);
        }
    }

    private void finishTimedOutServiceChecks() {
        if (!pendingServiceChecks.isEmpty()) {
            pendingServiceChecks.clear();
            serviceCheckDevices.clear();
            postState("Busca de nBTChat finalizada.");
            listener.onDiscoveryFinished();
        }
    }

    @SuppressLint("MissingPermission")
    private boolean isBonded(BluetoothDevice device) {
        try {
            return device.getBondState() == BluetoothDevice.BOND_BONDED;
        } catch (SecurityException ex) {
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    private void postDevice(BluetoothDevice device, boolean paired, boolean appAvailable) {
        String address = safeAddress(device);
        String name = safeName(device);
        mainHandler.post(() -> listener.onDeviceFound(new DeviceCandidate(device, name, address, paired, appAvailable)));
    }

    @SuppressLint("MissingPermission")
    private String safeName(BluetoothDevice device) {
        try {
            String name = device.getName();
            if (name == null || name.trim().isEmpty()) {
                return "Aparelho Bluetooth";
            }
            return name;
        } catch (SecurityException ex) {
            return "Aparelho Bluetooth";
        }
    }

    @SuppressLint("MissingPermission")
    private String safeAddress(BluetoothDevice device) {
        try {
            return device.getAddress();
        } catch (SecurityException ex) {
            return "";
        }
    }

    private void postState(String state) {
        mainHandler.post(() -> listener.onBluetoothState(state));
    }

    private void postError(String message) {
        mainHandler.post(() -> listener.onError(message));
    }

    private final class AcceptThread extends Thread {
        private final boolean insecure;
        private BluetoothServerSocket serverSocket;
        private boolean running = true;

        AcceptThread(boolean insecure) {
            this.insecure = insecure;
        }

        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            try {
                serverSocket = insecure
                        ? adapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                        : adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID);
                while (running) {
                    BluetoothSocket socket = serverSocket.accept();
                    if (socket != null) {
                        String remoteName = safeName(socket.getRemoteDevice());
                        String remoteAddress = safeAddress(socket.getRemoteDevice());
                        mainHandler.post(() -> listener.onIncomingConnection(remoteName, remoteAddress));
                        handleSocket(socket, true);
                    }
                }
            } catch (IOException ex) {
                if (running) {
                    String mode = insecure ? "compatibilidade" : "segura";
                    postError("Falha ao escutar conexoes Bluetooth (" + mode + "): " + ex.getMessage());
                }
            } catch (SecurityException ex) {
                postError("Permissao Bluetooth negada para receber conexoes.");
            }
        }

        void cancel() {
            running = false;
            closeQuietly(serverSocket);
        }
    }

    private final class ConnectThread extends Thread {
        private final BluetoothDevice device;
        private BluetoothSocket socket;
        private boolean running = true;

        ConnectThread(BluetoothDevice device) {
            this.device = device;
        }

        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            try {
                BluetoothSocket connectedSocket = connectWithFallbacks();
                if (running) {
                    handleSocket(connectedSocket, false);
                } else {
                    closeQuietly(connectedSocket);
                }
            } catch (IOException ex) {
                closeQuietly(socket);
                if (running) {
                    postError("Nao foi possivel conectar. Confirme se o outro aparelho esta visivel, pareado e com o nBTChat aberto.");
                }
            } catch (SecurityException ex) {
                if (running) {
                    postError("Permissao Bluetooth negada para conectar.");
                }
            }
        }

        @SuppressLint("MissingPermission")
        private BluetoothSocket connectWithFallbacks() throws IOException {
            IOException lastError = null;
            for (int mode = 0; mode < 3 && running; mode++) {
                BluetoothSocket attempt = null;
                try {
                    attempt = createSocketForMode(mode);
                    socket = attempt;
                    attempt.connect();
                    return attempt;
                } catch (IOException ex) {
                    lastError = ex;
                    closeQuietly(attempt);
                    socket = null;
                }
            }
            if (lastError != null) {
                throw lastError;
            }
            throw new IOException("Conexao cancelada.");
        }

        @SuppressLint("MissingPermission")
        private BluetoothSocket createSocketForMode(int mode) throws IOException {
            if (mode == 0) {
                return device.createRfcommSocketToServiceRecord(SERVICE_UUID);
            }
            if (mode == 1) {
                return device.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID);
            }
            try {
                java.lang.reflect.Method method = device.getClass().getMethod("createRfcommSocket", int.class);
                Object result = method.invoke(device, 1);
                if (result instanceof BluetoothSocket) {
                    return (BluetoothSocket) result;
                }
            } catch (Exception ex) {
                IOException ioException = new IOException("Fallback Bluetooth indisponivel.");
                ioException.initCause(ex);
                throw ioException;
            }
            throw new IOException("Fallback Bluetooth invalido.");
        }

        void cancel() {
            running = false;
            closeQuietly(socket);
        }
    }

    private final class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final boolean incoming;
        private DataInputStream input;
        private DataOutputStream output;
        private CryptoSession cryptoSession;
        private boolean running = true;
        private String remoteAddress = "";
        private String remoteDeviceId = "";
        private String remoteIdentityPublicKey = "";

        ConnectedThread(BluetoothSocket socket, boolean incoming) {
            this.socket = socket;
            this.incoming = incoming;
        }

        @Override
        public void run() {
            try {
                remoteAddress = safeAddress(socket.getRemoteDevice());
                input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

                UserProfile localProfile = profileStore.loadLocalProfile();
                CryptoSession.Handshake handshake = CryptoSession.createHandshake();
                String localNonce = CryptoSession.randomNonce();
                JSONObject hello = new JSONObject();
                hello.put("type", "hello");
                hello.put("protocol", PROTOCOL_V2);
                hello.put("publicKey", handshake.getPublicKeyBase64());
                hello.put("deviceId", identityStore.getDeviceId());
                hello.put("identityPublicKey", identityStore.getPublicKeyBase64());
                hello.put("nonce", localNonce);
                hello.put("signature", identityStore.sign(CryptoSession.handshakeBytes(
                        PROTOCOL_V2,
                        handshake.getPublicKeyBase64(),
                        identityStore.getDeviceId(),
                        identityStore.getPublicKeyBase64(),
                        localNonce
                )));
                hello.put("profile", localProfile.toJson());
                hello.put("allowContactSharing", settingsStore.contactSharingEnabled());
                writeFrame(hello.toString());

                JSONObject remoteHello = new JSONObject(readFrame());
                if (!"hello".equals(remoteHello.optString("type"))) {
                    throw new IOException("Resposta inicial invalida.");
                }

                UserProfile remoteProfile = UserProfile.fromJson(remoteHello.optJSONObject("profile"));
                int remoteProtocol = remoteHello.optInt("protocol", 1);
                String remotePublicKey = remoteHello.getString("publicKey");
                remoteDeviceId = remoteHello.optString("deviceId", "");
                remoteIdentityPublicKey = remoteHello.optString("identityPublicKey", "");
                if (remoteProtocol >= PROTOCOL_V2) {
                    boolean signatureOk = identityStore.verify(
                            remoteIdentityPublicKey,
                            CryptoSession.handshakeBytes(
                                    remoteProtocol,
                                    remotePublicKey,
                                    remoteDeviceId,
                                    remoteIdentityPublicKey,
                                    remoteHello.optString("nonce", "")
                            ),
                            remoteHello.optString("signature", "")
                    );
                    if (!signatureOk) {
                        mainHandler.post(() -> listener.onIdentityWarning(remoteAddress, ProfileStore.IdentityStatus.INVALID.name(), remoteDeviceId, remoteIdentityPublicKey, ""));
                        throw new IOException("Nao foi possivel confirmar a identidade deste contato.");
                    }
                    cryptoSession = CryptoSession.deriveV2(handshake, remotePublicKey);
                } else {
                    cryptoSession = CryptoSession.deriveLegacy(handshake, remotePublicKey);
                    mainHandler.post(() -> listener.onIdentityWarning(remoteAddress, "LEGACY", remoteDeviceId, remoteIdentityPublicKey, cryptoSession.getFingerprint()));
                }
                profileStore.saveContact(remoteAddress, remoteProfile);
                ProfileStore.IdentityStatus identityStatus = profileStore.verifyOrStoreIdentity(remoteAddress, remoteDeviceId, remoteIdentityPublicKey, safeName(socket.getRemoteDevice()));
                if (identityStatus == ProfileStore.IdentityStatus.CHANGED_KEY
                        || identityStatus == ProfileStore.IdentityStatus.CHANGED_DEVICE
                        || identityStatus == ProfileStore.IdentityStatus.INVALID) {
                    mainHandler.post(() -> listener.onIdentityWarning(remoteAddress, identityStatus.name(), remoteDeviceId, remoteIdentityPublicKey, cryptoSession.getFingerprint()));
                    throw new IOException("A identidade de seguranca deste contato mudou.");
                }
                profileStore.setContactShareAllowed(remoteAddress, remoteHello.optBoolean("allowContactSharing", false));

                mainHandler.post(() -> listener.onRemoteProfile(remoteAddress, remoteProfile));
                mainHandler.post(() -> listener.onRemoteIdentity(remoteAddress, remoteDeviceId, remoteIdentityPublicKey));
                mainHandler.post(() -> listener.onConnected(remoteAddress, remoteProfile, cryptoSession.getFingerprint()));
                flushRelayQueue();

                while (running) {
                    JSONObject frame = new JSONObject(readFrame());
                    if (!"encrypted".equals(frame.optString("type"))) {
                        continue;
                    }
                    JSONObject plain = cryptoSession.decrypt(frame);
                    String type = plain.optString("type");
                    if ("message".equals(type)) {
                        handlePlainMessage(plain);
                    } else if ("receipt".equals(type)) {
                        handleReceipt(plain);
                    } else if ("delete".equals(type)) {
                        handleDelete(plain);
                    } else if ("sealed".equals(type)) {
                        handleSealed(plain);
                    } else if ("profile".equals(type)) {
                        handleProfileUpdate(plain);
                    } else if ("typing".equals(type)) {
                        handleTyping(plain);
                    } else if ("presence".equals(type)) {
                        handlePresence(plain);
                    }
                }
            } catch (EOFException ex) {
                if (running) {
                    postState("O outro aparelho encerrou a conversa.");
                }
            } catch (Exception ex) {
                if (running) {
                    String name = safeName(socket.getRemoteDevice());
                    postError("Nao foi possivel manter a conexao com " + name + ". Abra o nBTChat nos dois aparelhos e tente novamente.");
                }
            } finally {
                closeQuietly(socket);
                if (connectedThread == this) {
                    connectedThread = null;
                }
                if (remoteAddress != null && !remoteAddress.isEmpty()) {
                    mainHandler.post(() -> listener.onDisconnected(remoteAddress));
                }
            }
        }

        void sendChatMessage(String destinationAddress, String id, String kind, String body, String mediaBase64, long durationMs, long sentAt, String replyToId, String replyPreview) {
            if ((body == null || body.trim().isEmpty()) && (mediaBase64 == null || mediaBase64.trim().isEmpty())) {
                return;
            }
            if (cryptoSession == null) {
                postError("A conversa ainda esta criando a chave segura.");
                return;
            }
            new Thread(() -> {
                try {
                    JSONObject message = messageJson(id, kind, body, mediaBase64, durationMs, sentAt, replyToId, replyPreview);
                    JSONObject outgoing = wrapForDestination(destinationAddress, message);
                    writeFrame(cryptoSession.encrypt(outgoing).toString());
                    mainHandler.post(() -> listener.onReceiptReceived(destinationAddress == null || destinationAddress.isEmpty() ? remoteAddress : destinationAddress, id, MessageStore.STATUS_SENT));
                } catch (Exception ex) {
                    postError("Nao foi possivel enviar a mensagem: " + ex.getMessage());
                }
            }, "nBTChat-send").start();
        }

        void sendDeleteMessage(String destinationAddress, String messageId) {
            if (messageId == null || messageId.isEmpty() || cryptoSession == null) {
                return;
            }
            new Thread(() -> {
                try {
                    JSONObject delete = new JSONObject();
                    delete.put("type", "delete");
                    delete.put("id", messageId);
                    delete.put("sentAt", System.currentTimeMillis());
                    delete.put("sourceDeviceId", identityStore.getDeviceId());
                    delete.put("sourceIdentityPublicKey", identityStore.getPublicKeyBase64());
                    delete.put("sourceProfile", profileStore.loadLocalProfile().toJson());
                    writeFrame(cryptoSession.encrypt(wrapForDestination(destinationAddress, delete)).toString());
                } catch (Exception ex) {
                    postError("Nao foi possivel apagar para todos: " + ex.getMessage());
                }
            }, "nBTChat-delete").start();
        }

        void sendReceipt(String destinationAddress, String messageId, String status) {
            if (messageId == null || messageId.isEmpty() || cryptoSession == null) {
                return;
            }
            new Thread(() -> {
                try {
                    JSONObject receipt = new JSONObject();
                    receipt.put("type", "receipt");
                    receipt.put("id", messageId);
                    receipt.put("status", status);
                    receipt.put("sentAt", System.currentTimeMillis());
                    receipt.put("sourceDeviceId", identityStore.getDeviceId());
                    receipt.put("sourceIdentityPublicKey", identityStore.getPublicKeyBase64());
                    receipt.put("sourceProfile", profileStore.loadLocalProfile().toJson());
                    writeFrame(cryptoSession.encrypt(wrapForDestination(destinationAddress, receipt)).toString());
                } catch (Exception ignored) {
                }
            }, "nBTChat-receipt").start();
        }

        void sendProfileUpdate() {
            if (cryptoSession == null) {
                return;
            }
            new Thread(() -> {
                try {
                    JSONObject profile = new JSONObject();
                    profile.put("type", "profile");
                    profile.put("deviceId", identityStore.getDeviceId());
                    profile.put("identityPublicKey", identityStore.getPublicKeyBase64());
                    profile.put("profile", profileStore.loadLocalProfile().toJson());
                    profile.put("allowContactSharing", settingsStore.contactSharingEnabled());
                    writeFrame(cryptoSession.encrypt(profile).toString());
                } catch (Exception ignored) {
                }
            }, "nBTChat-profile").start();
        }

        void sendTyping(String destinationAddress, boolean typing) {
            if (cryptoSession == null || destinationAddress == null || destinationAddress.isEmpty() || !destinationAddress.equals(remoteAddress)) {
                return;
            }
            new Thread(() -> {
                try {
                    JSONObject event = new JSONObject();
                    event.put("type", "typing");
                    event.put("typing", typing);
                    event.put("sentAt", System.currentTimeMillis());
                    event.put("sourceDeviceId", identityStore.getDeviceId());
                    writeFrame(cryptoSession.encrypt(event).toString());
                } catch (Exception ignored) {
                }
            }, "nBTChat-typing").start();
        }

        void sendPresence(String status) {
            if (cryptoSession == null) {
                return;
            }
            new Thread(() -> {
                try {
                    JSONObject event = new JSONObject();
                    event.put("type", "presence");
                    event.put("status", status == null ? AppSettingsStore.PRESENCE_ONLINE : status);
                    event.put("sentAt", System.currentTimeMillis());
                    event.put("sourceDeviceId", identityStore.getDeviceId());
                    writeFrame(cryptoSession.encrypt(event).toString());
                } catch (Exception ignored) {
                }
            }, "nBTChat-presence").start();
        }

        private JSONObject messageJson(String id, String kind, String body, String mediaBase64, long durationMs, long sentAt, String replyToId, String replyPreview) throws Exception {
            JSONObject plain = new JSONObject();
            plain.put("type", "message");
            plain.put("id", id);
            plain.put("kind", kind == null || kind.isEmpty() ? MessageStore.KIND_TEXT : kind);
            plain.put("body", body == null ? "" : body);
            plain.put("mediaBase64", mediaBase64 == null ? "" : mediaBase64);
            plain.put("durationMs", durationMs);
            plain.put("sentAt", sentAt);
            plain.put("replyToId", replyToId == null ? "" : replyToId);
            plain.put("replyPreview", replyPreview == null ? "" : replyPreview);
            plain.put("sourceDeviceId", identityStore.getDeviceId());
            plain.put("sourceIdentityPublicKey", identityStore.getPublicKeyBase64());
            plain.put("sourceProfile", profileStore.loadLocalProfile().toJson());
            return plain;
        }

        private JSONObject wrapForDestination(String destinationAddress, JSONObject payload) throws Exception {
            if (destinationAddress == null || destinationAddress.isEmpty() || destinationAddress.equals(remoteAddress)) {
                return payload;
            }
            ProfileStore.ContactIdentity identity = profileStore.loadIdentity(destinationAddress);
            if (identity.deviceId.isEmpty() || identity.identityPublicKey.isEmpty()) {
                throw new IOException("Ainda nao tenho a chave do contato para encaminhar com privacidade.");
            }
            JSONObject envelope = new JSONObject();
            envelope.put("type", "sealed");
            envelope.put("id", payload.optString("id", Long.toString(System.currentTimeMillis())));
            envelope.put("sourceDeviceId", identityStore.getDeviceId());
            envelope.put("destinationDeviceId", identity.deviceId);
            envelope.put("ttl", 4);
            envelope.put("sealed", CryptoSession.sealFor(identity.identityPublicKey, payload));
            return envelope;
        }

        private void handlePlainMessage(JSONObject plain) {
            processMessage(plain, remoteAddress, remoteAddress, remoteAddress + ":" + plain.optString("id", ""));
        }

        private void processMessage(JSONObject plain, String conversationAddress, String receiptDestinationAddress, String seenKey) {
            String id = plain.optString("id", "");
            if (id.isEmpty() || conversationAddress == null || conversationAddress.isEmpty()) {
                return;
            }
            if (!relayStore.rememberSeen(seenKey)) {
                return;
            }
            String kind = plain.optString("kind", MessageStore.KIND_TEXT);
            String body = plain.optString("body", "");
            String mediaBase64 = plain.optString("mediaBase64", "");
            long durationMs = plain.optLong("durationMs", 0L);
            long sentAt = plain.optLong("sentAt", System.currentTimeMillis());
            String replyToId = plain.optString("replyToId", "");
            String replyPreview = plain.optString("replyPreview", "");
            if (!profileStore.isMuted(conversationAddress)
                    && !AppSettingsStore.PRESENCE_INVISIBLE.equals(settingsStore.userPresence())) {
                sendReceipt(receiptDestinationAddress, id, MessageStore.STATUS_DELIVERED);
            }
            mainHandler.post(() -> listener.onMessageReceived(conversationAddress, id, kind, body, mediaBase64, durationMs, sentAt, replyToId, replyPreview));
        }

        private void handleReceipt(JSONObject receipt) {
            processReceipt(receipt, remoteAddress);
        }

        private void processReceipt(JSONObject receipt, String conversationAddress) {
            String id = receipt.optString("id", "");
            String status = receipt.optString("status", MessageStore.STATUS_DELIVERED);
            if (conversationAddress == null || conversationAddress.isEmpty()) {
                return;
            }
            mainHandler.post(() -> listener.onReceiptReceived(conversationAddress, id, status));
        }

        private void handleDelete(JSONObject delete) {
            processDelete(delete, remoteAddress);
        }

        private void processDelete(JSONObject delete, String conversationAddress) {
            String id = delete.optString("id", "");
            if (id.isEmpty() || conversationAddress == null || conversationAddress.isEmpty()) {
                return;
            }
            mainHandler.post(() -> listener.onMessageDeleted(conversationAddress, id));
        }

        private void handleProfileUpdate(JSONObject profileJson) {
            UserProfile profile = UserProfile.fromJson(profileJson.optJSONObject("profile"));
            String deviceId = profileJson.optString("deviceId", "");
            String identityPublicKey = profileJson.optString("identityPublicKey", "");
            profileStore.saveContact(remoteAddress, profile);
            ProfileStore.IdentityStatus identityStatus = profileStore.verifyOrStoreIdentity(remoteAddress, deviceId, identityPublicKey, safeName(socket.getRemoteDevice()));
            if (identityStatus == ProfileStore.IdentityStatus.CHANGED_KEY
                    || identityStatus == ProfileStore.IdentityStatus.CHANGED_DEVICE
                    || identityStatus == ProfileStore.IdentityStatus.INVALID) {
                mainHandler.post(() -> listener.onIdentityWarning(remoteAddress, identityStatus.name(), deviceId, identityPublicKey, cryptoSession == null ? "" : cryptoSession.getFingerprint()));
                return;
            }
            profileStore.setContactShareAllowed(remoteAddress, profileJson.optBoolean("allowContactSharing", false));
            mainHandler.post(() -> listener.onRemoteIdentity(remoteAddress, deviceId, identityPublicKey));
            mainHandler.post(() -> listener.onRemoteProfile(remoteAddress, profile));
        }

        private void handleTyping(JSONObject typingJson) {
            boolean typing = typingJson.optBoolean("typing", false);
            mainHandler.post(() -> listener.onTypingReceived(remoteAddress, typing));
        }

        private void handlePresence(JSONObject presenceJson) {
            String status = presenceJson.optString("status", AppSettingsStore.PRESENCE_ONLINE);
            mainHandler.post(() -> listener.onPresenceReceived(remoteAddress, status));
        }

        private void handleSealed(JSONObject envelope) throws Exception {
            String id = envelope.optString("id", "");
            if (!relayStore.rememberSeen("sealed:" + id)) {
                return;
            }
            String destination = envelope.optString("destinationDeviceId", "");
            if (identityStore.getDeviceId().equals(destination)) {
                JSONObject opened = CryptoSession.openSealed(identityStore.getPrivateKey(), envelope.getJSONObject("sealed"));
                String sourceDeviceId = envelope.optString("sourceDeviceId", opened.optString("sourceDeviceId", ""));
                String conversationAddress = ensureKnownSource(sourceDeviceId, opened);
                if ("message".equals(opened.optString("type"))) {
                    processMessage(opened, conversationAddress, conversationAddress, "message:" + sourceDeviceId + ":" + opened.optString("id", ""));
                } else if ("receipt".equals(opened.optString("type"))) {
                    processReceipt(opened, conversationAddress);
                } else if ("delete".equals(opened.optString("type"))) {
                    processDelete(opened, conversationAddress);
                }
                return;
            }
            int ttl = envelope.optInt("ttl", 0);
            if (ttl > 0) {
                envelope.put("ttl", ttl - 1);
                relayStore.store(envelope);
            }
        }

        private String ensureKnownSource(String sourceDeviceId, JSONObject opened) {
            String address = profileStore.addressForDeviceId(sourceDeviceId);
            if (address.isEmpty()) {
                address = sourceDeviceId == null || sourceDeviceId.isEmpty() ? remoteAddress : sourceDeviceId;
            }
            String sourceIdentityPublicKey = opened.optString("sourceIdentityPublicKey", "");
            if (sourceDeviceId != null && !sourceDeviceId.isEmpty() && !sourceIdentityPublicKey.isEmpty()) {
                ProfileStore.IdentityStatus identityStatus = profileStore.verifyOrStoreIdentity(address, sourceDeviceId, sourceIdentityPublicKey, "");
                if (identityStatus == ProfileStore.IdentityStatus.CHANGED_KEY
                        || identityStatus == ProfileStore.IdentityStatus.CHANGED_DEVICE
                        || identityStatus == ProfileStore.IdentityStatus.INVALID) {
                    String callbackAddress = address;
                    mainHandler.post(() -> listener.onIdentityWarning(callbackAddress, identityStatus.name(), sourceDeviceId, sourceIdentityPublicKey, ""));
                    return address;
                }
                String callbackAddress = address;
                mainHandler.post(() -> listener.onRemoteIdentity(callbackAddress, sourceDeviceId, sourceIdentityPublicKey));
            }
            UserProfile sourceProfile = UserProfile.fromJson(opened.optJSONObject("sourceProfile"));
            if (sourceProfile.isComplete()) {
                profileStore.saveContact(address, sourceProfile);
                String callbackAddress = address;
                mainHandler.post(() -> listener.onRemoteProfile(callbackAddress, sourceProfile));
            }
            return address;
        }

        private void flushRelayQueue() {
            if (remoteDeviceId == null || remoteDeviceId.isEmpty() || cryptoSession == null) {
                return;
            }
            for (JSONObject envelope : relayStore.takeFor(remoteDeviceId)) {
                new Thread(() -> {
                    try {
                        writeFrame(cryptoSession.encrypt(envelope).toString());
                    } catch (Exception ignored) {
                        relayStore.store(envelope);
                    }
                }, "nBTChat-relay").start();
            }
        }

        void cancel() {
            running = false;
            closeQuietly(socket);
        }

        String getRemoteAddress() {
            return remoteAddress;
        }

        private synchronized void writeFrame(String data) throws IOException {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            output.writeInt(bytes.length);
            output.write(bytes);
            output.flush();
        }

        private String readFrame() throws IOException {
            int length = input.readInt();
            if (length <= 0 || length > MAX_FRAME_BYTES) {
                throw new IOException("Pacote Bluetooth invalido.");
            }
            byte[] bytes = new byte[length];
            input.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static void closeQuietly(BluetoothServerSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static void closeQuietly(BluetoothSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
