package com.mbzerker.nbtchat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

public final class BlePresenceManager {
    public static final UUID BLE_SERVICE_UUID = UUID.fromString("66a14f52-9c02-4c04-903d-0cdd8755a5f8");
    private static final ParcelUuid SERVICE_UUID = new ParcelUuid(BLE_SERVICE_UUID);
    private static final int BLE_VERSION = 1;
    private static final int BLE_ID_BYTES = 12;
    private static final byte FLAG_AVAILABLE = 1;
    private static final long ECONOMY_SCAN_MS = 5_000L;
    private static final long ECONOMY_REST_MS = 30_000L;

    public interface Listener {
        void onBlePeerSeen(String address, String bleIdHex, int rssi, long timestamp);
    }

    private final Context appContext;
    private final IdentityStore identityStore;
    private final ProfileStore profileStore;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BluetoothAdapter adapter;
    private final ParcelUuid serviceUuid = SERVICE_UUID;
    private final Map<String, String> expectedBleIds = new HashMap<>();

    private BluetoothLeAdvertiser advertiser;
    private BluetoothLeScanner scanner;
    private boolean advertising;
    private boolean scanning;
    private boolean aggressive;
    private Runnable scanCycle;

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            handleScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (results == null) {
                return;
            }
            for (ScanResult result : results) {
                handleScanResult(result);
            }
        }
    };

    public BlePresenceManager(Context context, IdentityStore identityStore, ProfileStore profileStore, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.identityStore = identityStore;
        this.profileStore = profileStore;
        this.listener = listener;
        BluetoothManager manager = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        this.adapter = manager == null ? BluetoothAdapter.getDefaultAdapter() : manager.getAdapter();
    }

    public boolean isAvailable() {
        return adapter != null && adapter.isEnabled();
    }

    public void refreshKnownPeers() {
        expectedBleIds.clear();
        for (Map.Entry<String, ProfileStore.ContactIdentity> entry : profileStore.loadIdentities().entrySet()) {
            String address = entry.getKey();
            String deviceId = entry.getValue().deviceId;
            if (address == null || address.isEmpty() || deviceId == null || deviceId.isEmpty()) {
                continue;
            }
            for (int offset = -1; offset <= 1; offset++) {
                expectedBleIds.put(bleIdHex(deviceId, offset), address);
            }
        }
    }

    public void startEconomy() {
        start(false);
    }

    public void startAggressive() {
        start(true);
    }

    public void stop() {
        handler.removeCallbacksAndMessages(null);
        stopScan();
        stopAdvertising();
    }

    private void start(boolean aggressiveMode) {
        if (!isAvailable() || !hasBlePermission()) {
            return;
        }
        if (aggressive != aggressiveMode) {
            stopScan();
            stopAdvertising();
        }
        aggressive = aggressiveMode;
        refreshKnownPeers();
        startAdvertising(aggressiveMode);
        startScanCycle();
    }

    @SuppressLint("MissingPermission")
    private void startAdvertising(boolean aggressiveMode) {
        if (advertising || adapter == null || !adapter.isMultipleAdvertisementSupported()) {
            return;
        }
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) {
            return;
        }
        byte[] localBleId = bleIdBytes(identityStore.getDeviceId(), 0);
        if (localBleId.length != BLE_ID_BYTES) {
            return;
        }
        byte[] payload = new byte[14];
        payload[0] = (byte) BLE_VERSION;
        System.arraycopy(localBleId, 0, payload, 1, BLE_ID_BYTES);
        payload[13] = FLAG_AVAILABLE;

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(aggressiveMode ? AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY : AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
                .setConnectable(false)
                .build();
        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceUuid(serviceUuid)
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .build();
        AdvertiseData scanResponse = new AdvertiseData.Builder()
                .addServiceData(serviceUuid, payload)
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .build();
        try {
            advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback);
            advertising = true;
        } catch (Exception ignored) {
        }
    }

    @SuppressLint("MissingPermission")
    private void stopAdvertising() {
        if (!advertising || advertiser == null || !hasBlePermission()) {
            advertising = false;
            return;
        }
        try {
            advertiser.stopAdvertising(advertiseCallback);
        } catch (Exception ignored) {
        }
        advertising = false;
    }

    private void startScanCycle() {
        if (scanCycle != null) {
            handler.removeCallbacks(scanCycle);
        }
        scanCycle = new Runnable() {
            @Override
            public void run() {
                if (!hasBlePermission() || adapter == null || !adapter.isEnabled()) {
                    return;
                }
                startScan(aggressive);
                if (aggressive) {
                    return;
                }
                handler.postDelayed(() -> {
                    stopScan();
                    handler.postDelayed(this, ECONOMY_REST_MS);
                }, ECONOMY_SCAN_MS);
            }
        };
        scanCycle.run();
    }

    @SuppressLint("MissingPermission")
    private void startScan(boolean aggressiveMode) {
        if (scanning || adapter == null) {
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            return;
        }
        ScanFilter filter = new ScanFilter.Builder().setServiceUuid(serviceUuid).build();
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(filter);
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(aggressiveMode ? ScanSettings.SCAN_MODE_LOW_LATENCY : ScanSettings.SCAN_MODE_LOW_POWER)
                .build();
        try {
            scanner.startScan(filters, settings, scanCallback);
            scanning = true;
        } catch (Exception ignored) {
        }
    }

    @SuppressLint("MissingPermission")
    private void stopScan() {
        if (!scanning || scanner == null || !hasBlePermission()) {
            scanning = false;
            return;
        }
        try {
            scanner.stopScan(scanCallback);
        } catch (Exception ignored) {
        }
        scanning = false;
    }

    private void handleScanResult(ScanResult result) {
        if (result == null) {
            return;
        }
        ScanRecord record = result.getScanRecord();
        if (record == null) {
            return;
        }
        byte[] payload = record.getServiceData(serviceUuid);
        if (payload == null || payload.length < 1 + BLE_ID_BYTES || payload[0] != BLE_VERSION) {
            return;
        }
        byte[] bleId = new byte[BLE_ID_BYTES];
        System.arraycopy(payload, 1, bleId, 0, BLE_ID_BYTES);
        String bleIdHex = hex(bleId);
        String address = expectedBleIds.get(bleIdHex);
        if (address != null && listener != null) {
            listener.onBlePeerSeen(address, bleIdHex, result.getRssi(), System.currentTimeMillis());
        }
    }

    private boolean hasBlePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }
        return appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
                && appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    public static String bleIdHex(String deviceId, int dayOffset) {
        return hex(bleIdBytes(deviceId, dayOffset));
    }

    private static byte[] bleIdBytes(String deviceId, int dayOffset) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return new byte[0];
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(("nBTChat BLE v1" + deviceId + dayString(dayOffset)).getBytes(StandardCharsets.UTF_8));
            byte[] shortId = new byte[BLE_ID_BYTES];
            System.arraycopy(hash, 0, shortId, 0, BLE_ID_BYTES);
            return shortId;
        } catch (Exception ignored) {
            return new byte[0];
        }
    }

    private static String dayString(int offset) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_YEAR, offset);
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(calendar.getTime());
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte value : bytes) {
            builder.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return builder.toString();
    }
}
