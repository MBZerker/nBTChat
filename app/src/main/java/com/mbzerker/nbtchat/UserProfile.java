package com.mbzerker.nbtchat;

import org.json.JSONException;
import org.json.JSONObject;

public final class UserProfile {
    public static final String GENDER_MALE = "male";
    public static final String GENDER_FEMALE = "female";
    public static final String GENDER_OTHER = "other";

    private final String displayName;
    private final String status;
    private final String gender;
    private final String photoBase64;

    public UserProfile(String displayName, String status, String gender, String photoBase64) {
        this.displayName = clean(displayName);
        this.status = clean(status);
        this.gender = clean(gender).isEmpty() ? GENDER_OTHER : clean(gender);
        this.photoBase64 = clean(photoBase64);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStatus() {
        return status;
    }

    public String getGender() {
        return gender;
    }

    public String getPhotoBase64() {
        return photoBase64;
    }

    public boolean hasPhoto() {
        return !photoBase64.isEmpty();
    }

    public boolean isComplete() {
        return !displayName.trim().isEmpty();
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("displayName", displayName);
        json.put("status", status);
        json.put("gender", gender);
        json.put("photoBase64", photoBase64);
        return json;
    }

    public static UserProfile fromJson(JSONObject json) {
        if (json == null) {
            return empty();
        }
        return new UserProfile(
                json.optString("displayName", ""),
                json.optString("status", ""),
                json.optString("gender", GENDER_OTHER),
                json.optString("photoBase64", "")
        );
    }

    public static UserProfile empty() {
        return new UserProfile("", "", GENDER_OTHER, "");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
