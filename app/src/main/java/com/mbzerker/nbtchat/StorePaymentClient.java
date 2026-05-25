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

    public Uri cartelaCheckoutUri(String deviceId, String cartelasJson) {
        return Uri.parse(baseUrl + "/checkout?productId=" + PRODUCT_CARTELA_EVENTOS
                + "&deviceId=" + Uri.encode(deviceId)
                + "&cartelas=" + Uri.encode(cartelasJson == null ? "" : cartelasJson));
    }

    public Uri cartelaRecoveryUri(String deviceId) {
        return Uri.parse(baseUrl + "/recover?productId=" + PRODUCT_CARTELA_EVENTOS + "&deviceId=" + Uri.encode(deviceId));
    }

    public String shortenUrl(String longUrl) throws Exception {
        JSONObject body = new JSONObject();
        body.put("url", longUrl == null ? "" : longUrl);
        JSONObject json = post("/shorten", body);
        String shortUrl = json.optString("shortUrl", "");
        return shortUrl.trim().isEmpty() ? longUrl : shortUrl;
    }

    public String createShareLink(String encodedPayload) throws Exception {
        JSONObject body = new JSONObject();
        body.put("payload", encodedPayload == null ? "" : encodedPayload);
        JSONObject json = post("/share-link", body);
        return json.optString("shortUrl", "");
    }

    public String getSharePayload(String baseUrlOverride, String code) throws Exception {
        String cleanBase = trimTrailingSlash(baseUrlOverride == null || baseUrlOverride.trim().isEmpty() ? baseUrl : baseUrlOverride);
        JSONObject json = requestAbsolute(cleanBase + "/share/" + enc(code));
        return json.optString("payload", "");
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

    public ProductConfig getCartelaProduct() throws Exception {
        JSONObject products = request("/").optJSONObject("products");
        JSONObject item = products == null ? null : products.optJSONObject(PRODUCT_CARTELA_EVENTOS);
        return ProductConfig.fromJson(item);
    }

    public CartelaState registerCartela(String tableId, String ownerDeviceId, String ownerName,
                                        String title, String ownerMessage, String copyText, String ownerContact,
                                        boolean allowReservations, int reservationHours) throws Exception {
        JSONObject body = new JSONObject();
        body.put("tableId", tableId == null ? "" : tableId);
        body.put("ownerDeviceId", ownerDeviceId == null ? "" : ownerDeviceId);
        body.put("ownerName", ownerName == null ? "" : ownerName);
        body.put("title", title == null ? "" : title);
        body.put("ownerMessage", ownerMessage == null ? "" : ownerMessage);
        body.put("copyText", copyText == null ? "" : copyText);
        body.put("ownerContact", ownerContact == null ? "" : ownerContact);
        body.put("allowReservations", allowReservations);
        body.put("reservationHours", Math.max(1, Math.min(168, reservationHours)));
        return CartelaState.fromJson(post("/cartela/register", body).optJSONObject("cartela"));
    }

    public CartelaState getCartelaState(String tableId) throws Exception {
        JSONObject json = request("/cartela/state?tableId=" + enc(tableId));
        return CartelaState.fromJson(json.optJSONObject("cartela"));
    }

    public CartelaState chooseCartelaNumber(String tableId, String chooserDeviceId, String chooserName, int number, boolean reserved) throws Exception {
        JSONObject body = new JSONObject();
        body.put("tableId", tableId == null ? "" : tableId);
        body.put("chooserDeviceId", chooserDeviceId == null ? "" : chooserDeviceId);
        body.put("chooserName", chooserName == null ? "" : chooserName);
        body.put("number", number);
        body.put("reserved", reserved);
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

    public CartelaState deleteCartelaChoice(String tableId, String ownerDeviceId, String chooserDeviceId, int number) throws Exception {
        return deleteCartelaChoice(tableId, ownerDeviceId, chooserDeviceId, number, false);
    }

    public CartelaState deleteCartelaChoice(String tableId, String ownerDeviceId, String chooserDeviceId, int number, boolean permanent) throws Exception {
        JSONObject body = new JSONObject();
        body.put("tableId", tableId == null ? "" : tableId);
        body.put("ownerDeviceId", ownerDeviceId == null ? "" : ownerDeviceId);
        body.put("chooserDeviceId", chooserDeviceId == null ? "" : chooserDeviceId);
        body.put("number", number);
        body.put("permanent", permanent);
        return CartelaState.fromJson(post("/cartela/delete-choice", body).optJSONObject("cartela"));
    }

    public CartelaState renameCartelaChoice(String tableId, String ownerDeviceId, String chooserDeviceId, int number, String chooserName) throws Exception {
        JSONObject body = new JSONObject();
        body.put("tableId", tableId == null ? "" : tableId);
        body.put("ownerDeviceId", ownerDeviceId == null ? "" : ownerDeviceId);
        body.put("chooserDeviceId", chooserDeviceId == null ? "" : chooserDeviceId);
        body.put("number", number);
        body.put("chooserName", chooserName == null ? "" : chooserName);
        return CartelaState.fromJson(post("/cartela/rename-choice", body).optJSONObject("cartela"));
    }

    public CartelaState restoreCartelaChoice(String tableId, String ownerDeviceId, String chooserDeviceId, int number) throws Exception {
        JSONObject body = new JSONObject();
        body.put("tableId", tableId == null ? "" : tableId);
        body.put("ownerDeviceId", ownerDeviceId == null ? "" : ownerDeviceId);
        body.put("chooserDeviceId", chooserDeviceId == null ? "" : chooserDeviceId);
        body.put("number", number);
        return CartelaState.fromJson(post("/cartela/restore-choice", body).optJSONObject("cartela"));
    }

    private JSONObject request(String path) throws Exception {
        return requestAbsolute(baseUrl + path);
    }

    private JSONObject requestAbsolute(String urlValue) throws Exception {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlValue);
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

    public static final class ProductConfig {
        public final String id;
        public final String title;
        public final double price;
        public final double dailyFee;
        public final double power;
        public final int durationDays;
        public final String footer;

        ProductConfig(String id, String title, double price, double dailyFee, double power, int durationDays, String footer) {
            this.id = id == null ? PRODUCT_CARTELA_EVENTOS : id;
            this.title = title == null || title.trim().isEmpty() ? GadgetStore.TABLE_100_TITLE : title;
            this.price = price > 0 ? price : 2.49;
            this.dailyFee = Math.max(0, dailyFee);
            this.power = power >= 1 ? power : 1.25;
            this.durationDays = Math.max(1, Math.min(365, durationDays));
            this.footer = footer == null ? GadgetStore.TABLE_100_FOOTER : footer;
        }

        static ProductConfig fromJson(JSONObject json) {
            if (json == null) {
                return new ProductConfig(PRODUCT_CARTELA_EVENTOS, GadgetStore.TABLE_100_TITLE, 2.49, 1.25, 1.25, 1, GadgetStore.TABLE_100_FOOTER);
            }
            return new ProductConfig(
                    json.optString("id", PRODUCT_CARTELA_EVENTOS),
                    json.optString("title", GadgetStore.TABLE_100_TITLE),
                    json.optDouble("price", 2.49),
                    json.optDouble("dailyFee", 1.25),
                    json.optDouble("power", 1.25),
                    json.optInt("durationDays", 1),
                    json.optString("footer", GadgetStore.TABLE_100_FOOTER)
            );
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
        public final String title;
        public final String ownerMessage;
        public final String copyText;
        public final String ownerContact;
        public final long expiresAt;
        public final boolean allowReservations;
        public final int reservationHours;
        public final ArrayList<CartelaChoice> choices;

        CartelaState(String tableId, String ownerDeviceId, String ownerName, String title,
                     String ownerMessage, String copyText, String ownerContact, long expiresAt, boolean allowReservations,
                     int reservationHours, ArrayList<CartelaChoice> choices) {
            this.tableId = tableId == null ? "" : tableId;
            this.ownerDeviceId = ownerDeviceId == null ? "" : ownerDeviceId;
            this.ownerName = ownerName == null ? "" : ownerName;
            this.title = title == null ? "" : title;
            this.ownerMessage = ownerMessage == null ? "" : ownerMessage;
            this.copyText = copyText == null ? "" : copyText;
            this.ownerContact = ownerContact == null ? "" : ownerContact;
            this.expiresAt = expiresAt;
            this.allowReservations = allowReservations;
            this.reservationHours = Math.max(1, Math.min(168, reservationHours));
            this.choices = choices == null ? new ArrayList<>() : choices;
        }

        static CartelaState fromJson(JSONObject json) {
            if (json == null) {
                return new CartelaState("", "", "", "", "", "", "", 0L, false, 24, new ArrayList<>());
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
                            item.optBoolean("confirmed", false),
                            item.optBoolean("reserved", false),
                            item.optLong("reservationExpiresAt", 0L),
                            item.optBoolean("removed", false)
                    ));
                }
            }
            return new CartelaState(
                    json.optString("tableId", ""),
                    json.optString("ownerDeviceId", ""),
                    json.optString("ownerName", ""),
                    json.optString("title", ""),
                    json.optString("ownerMessage", ""),
                    json.optString("copyText", ""),
                    json.optString("ownerContact", ""),
                    json.optLong("expiresAt", 0L),
                    json.optBoolean("allowReservations", false),
                    json.optInt("reservationHours", 24),
                    choices
            );
        }
    }

    public static final class CartelaChoice {
        public final String chooserDeviceId;
        public final String chooserName;
        public final int number;
        public final boolean confirmed;
        public final boolean reserved;
        public final long reservationExpiresAt;
        public final boolean removed;

        CartelaChoice(String chooserDeviceId, String chooserName, int number, boolean confirmed, boolean reserved, long reservationExpiresAt, boolean removed) {
            this.chooserDeviceId = chooserDeviceId == null ? "" : chooserDeviceId;
            this.chooserName = chooserName == null ? "" : chooserName;
            this.number = number;
            this.removed = removed;
            this.confirmed = confirmed && !removed;
            this.reserved = reserved && !confirmed && !removed;
            this.reservationExpiresAt = confirmed || removed ? 0L : reservationExpiresAt;
        }
    }
}
