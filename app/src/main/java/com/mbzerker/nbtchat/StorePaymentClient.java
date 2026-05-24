package com.mbzerker.nbtchat;

import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class StorePaymentClient {
    public static final String PRODUCT_CARTELA_EVENTOS = "cartela_de_eventos";

    private final String baseUrl;

    public StorePaymentClient() {
        baseUrl = trimTrailingSlash(BuildConfig.NBTCHAT_STORE_BASE_URL);
    }

    public Uri cartelaCheckoutUri(String deviceId) {
        return Uri.parse(baseUrl + "/checkout?productId=" + PRODUCT_CARTELA_EVENTOS + "&deviceId=" + Uri.encode(deviceId));
    }

    public Entitlement getCartelaEntitlement(String deviceId) throws Exception {
        String path = "/entitlement?deviceId=" + enc(deviceId) + "&productId=" + enc(PRODUCT_CARTELA_EVENTOS);
        JSONObject json = request(path);
        return new Entitlement(
                json.optBoolean("active", false),
                json.optString("productId", PRODUCT_CARTELA_EVENTOS),
                json.optLong("expiresAt", 0L),
                json.optString("title", GadgetStore.TABLE_100_TITLE)
        );
    }

    private JSONObject request(String path) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "nBTChat/" + BuildConfig.VERSION_NAME);
            int code = connection.getResponseCode();
            String response = readAll(code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream());
            if (code < 200 || code >= 300) {
                throw new StorePaymentException(parseError(response));
            }
            return response.trim().isEmpty() ? new JSONObject() : new JSONObject(response);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String parseError(String response) {
        try {
            String error = new JSONObject(response == null ? "{}" : response).optString("error", "");
            if (!error.trim().isEmpty()) {
                return error;
            }
        } catch (Exception ignored) {
        }
        return "Nao foi possivel falar com a loja.";
    }

    private static String readAll(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private static String trimTrailingSlash(String value) {
        String clean = value == null || value.trim().isEmpty()
                ? "https://nbtchat-store.nectof.workers.dev"
                : value.trim();
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    public static final class Entitlement {
        public final boolean active;
        public final String productId;
        public final long expiresAt;
        public final String title;

        Entitlement(boolean active, String productId, long expiresAt, String title) {
            this.active = active;
            this.productId = productId == null ? PRODUCT_CARTELA_EVENTOS : productId;
            this.expiresAt = expiresAt;
            this.title = title == null ? "" : title;
        }
    }

    public static final class StorePaymentException extends Exception {
        StorePaymentException(String message) {
            super(message);
        }
    }
}
