package com.mbzerker.nbtchat;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public final class StorePaymentClient {
    public static final String PRODUCT_CARTELA_EVENTOS = "cartela_de_eventos";

    private final String baseUrl;

    public StorePaymentClient() {
        baseUrl = trimTrailingSlash(BuildConfig.NBTCHAT_STORE_BASE_URL);
    }

    public Uri cartelaCheckoutUri(String deviceId) {
        return Uri.parse(baseUrl + "/checkout?productId=" + PRODUCT_CARTELA_EVENTOS + "&deviceId=" + Uri.encode(deviceId));
    }

    public Uri cartelaRecoveryUri(String deviceId) {
        return Uri.parse(baseUrl + "/recover?productId=" + PRODUCT_CARTELA_EVENTOS + "&deviceId=" + Uri.encode(deviceId));
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

    public CartelaState registerCartela(String tableId, String ownerDeviceId, String ownerName,
                                        String ownerMessage, String copyText, String ownerContact) throws Exception {
        JSONObject body = new JSONObject();
        body.put("tableId", tableId == null ? "" : tableId);
        body.put("ownerDeviceId", ownerDeviceId == null ? "" : ownerDeviceId);
        body.put("ownerName", ownerName == null ? "" : ownerName);
        body.put("ownerMessage", ownerMessage == null ? "" : ownerMessage);
        body.put("copyText", copyText == null ? "" : copyText);
        body.put("ownerContact", ownerContact == null ? "" : ownerContact);
        return CartelaState.fromJson(post("/cartela/register", body).optJSONObject("cartela"));
    }

    public CartelaState getCartelaState(String tableId) throws Exception {
        JSONObject json = request("/cartela/state?tableId=" + enc(tableId));
        return CartelaState.fromJson(json.optJSONObject("cartela"));
    }

    public CartelaState chooseCartelaNumber(String tableId, String chooserDeviceId, String chooserName, int number) throws Exception {
        JSONObject body = new JSONObject();
        body.put("tableId", tableId == null ? "" : tableId);
        body.put("chooserDeviceId", chooserDeviceId == null ? "" : chooserDeviceId);
        body.put("chooserName", chooserName == null ? "" : chooserName);
        body.put("number", number);
        return CartelaState.fromJson(post("/cartela/choose", body).optJSONObject("cartela"));
    }

    public CartelaState confirmCartelaNumber(String tableId, String ownerDeviceId, String chooserDeviceId, int number, boolean confirmed) throws Exception {
        JSONObject body = new JSONObject();
        body.put("tableId", tableId == null ? "" : tableId);
        body.put("ownerDeviceId", ownerDeviceId == null ? "" : ownerDeviceId);
        body.put("chooserDeviceId", chooserDeviceId == null ? "" : chooserDeviceId);
        body.put("number", number);
        body.put("confirmed", confirmed);
        return CartelaState.fromJson(post("/cartela/confirm", body).optJSONObject("cartela"));
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

    private JSONObject post(String path, JSONObject body) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(baseUrl + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("User-Agent", "nBTChat/" + BuildConfig.VERSION_NAME);
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(payload);
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

    public static final class CartelaState {
        public final String tableId;
        public final String ownerDeviceId;
        public final String ownerName;
        public final String ownerMessage;
        public final String copyText;
        public final String ownerContact;
        public final long expiresAt;
        public final ArrayList<CartelaChoice> choices;

        CartelaState(String tableId, String ownerDeviceId, String ownerName, String ownerMessage,
                     String copyText, String ownerContact, long expiresAt, ArrayList<CartelaChoice> choices) {
            this.tableId = tableId == null ? "" : tableId;
            this.ownerDeviceId = ownerDeviceId == null ? "" : ownerDeviceId;
            this.ownerName = ownerName == null ? "" : ownerName;
            this.ownerMessage = ownerMessage == null ? "" : ownerMessage;
            this.copyText = copyText == null ? "" : copyText;
            this.ownerContact = ownerContact == null ? "" : ownerContact;
            this.expiresAt = expiresAt;
            this.choices = choices == null ? new ArrayList<>() : choices;
        }

        static CartelaState fromJson(JSONObject json) {
            if (json == null) {
                return new CartelaState("", "", "", "", "", "", 0L, new ArrayList<>());
            }
            ArrayList<CartelaChoice> choices = new ArrayList<>();
            JSONArray rawChoices = json.optJSONArray("choices");
            if (rawChoices != null) {
                for (int i = 0; i < rawChoices.length(); i++) {
                    JSONObject item = rawChoices.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    choices.add(new CartelaChoice(
                            item.optString("chooserDeviceId", ""),
                            item.optString("chooserName", ""),
                            item.optInt("number", 0),
                            item.optBoolean("confirmed", false)
                    ));
                }
            }
            return new CartelaState(
                    json.optString("tableId", ""),
                    json.optString("ownerDeviceId", ""),
                    json.optString("ownerName", ""),
                    json.optString("ownerMessage", ""),
                    json.optString("copyText", ""),
                    json.optString("ownerContact", ""),
                    json.optLong("expiresAt", 0L),
                    choices
            );
        }
    }

    public static final class CartelaChoice {
        public final String chooserDeviceId;
        public final String chooserName;
        public final int number;
        public final boolean confirmed;

        CartelaChoice(String chooserDeviceId, String chooserName, int number, boolean confirmed) {
            this.chooserDeviceId = chooserDeviceId == null ? "" : chooserDeviceId;
            this.chooserName = chooserName == null ? "" : chooserName;
            this.number = number;
            this.confirmed = confirmed;
        }
    }
}
