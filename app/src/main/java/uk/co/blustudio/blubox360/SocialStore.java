package uk.co.blustudio.blubox360;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class SocialStore {
    private static final String PREFS = "blubox_social_v1";
    private static final String KEY_FRIENDS_PREFIX = "friends_";
    private static final String KEY_STATUS_PREFIX = "status_";
    private static final String KEY_ONLINE_PREFIX = "online_";

    static final String STATUS_ONLINE = "Online";
    static final String STATUS_AWAY = "Away";
    static final String STATUS_OFFLINE = "Offline";

    private final SharedPreferences preferences;

    SocialStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized List<Friend> friends(String profileId) {
        String encoded = preferences.getString(KEY_FRIENDS_PREFIX + safe(profileId), "");
        if (encoded == null || encoded.isEmpty()) return Collections.emptyList();
        ArrayList<Friend> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(encoded);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                Friend friend = Friend.fromJson(object);
                if (friend != null) result.add(friend);
            }
        } catch (Exception ignored) {
        }
        return Collections.unmodifiableList(result);
    }

    synchronized AddResult addFriend(String profileId, String rawTag) {
        String tag = normalizeTag(rawTag);
        if (tag.isEmpty()) return new AddResult(false, "Enter a BluTag.", null);
        if (tag.length() < 3) return new AddResult(false, "BluTags need at least 3 characters.", null);

        ArrayList<Friend> friends = new ArrayList<>(friends(profileId));
        for (Friend friend : friends) {
            if (friend.bluTag.equalsIgnoreCase(tag)) {
                return new AddResult(false, "That friend is already added.", friend);
            }
        }

        Friend friend = new Friend(UUID.randomUUID().toString(), tag,
                displayNameFromTag(tag), STATUS_OFFLINE, "Not playing", System.currentTimeMillis());
        friends.add(friend);
        persistFriends(profileId, friends);
        return new AddResult(true, friend.displayName + " added to your friends.", friend);
    }

    synchronized boolean removeFriend(String profileId, String friendId) {
        ArrayList<Friend> friends = new ArrayList<>(friends(profileId));
        boolean removed = false;
        for (int i = friends.size() - 1; i >= 0; i--) {
            if (friends.get(i).id.equals(friendId)) {
                friends.remove(i);
                removed = true;
            }
        }
        if (removed) persistFriends(profileId, friends);
        return removed;
    }

    synchronized void setPresence(String profileId, String status) {
        String clean = STATUS_AWAY.equals(status) ? STATUS_AWAY
                : STATUS_OFFLINE.equals(status) ? STATUS_OFFLINE : STATUS_ONLINE;
        preferences.edit().putString(KEY_STATUS_PREFIX + safe(profileId), clean).apply();
    }

    synchronized String presence(String profileId) {
        return preferences.getString(KEY_STATUS_PREFIX + safe(profileId), STATUS_ONLINE);
    }

    synchronized void setOnlineEnabled(String profileId, boolean enabled) {
        preferences.edit().putBoolean(KEY_ONLINE_PREFIX + safe(profileId), enabled).apply();
    }

    synchronized boolean onlineEnabled(String profileId) {
        return preferences.getBoolean(KEY_ONLINE_PREFIX + safe(profileId), false);
    }

    String bluTag(ProfileStore.Profile profile) {
        if (profile == null) return "Player#0000";
        int hash = Math.abs(profile.id.hashCode()) % 10000;
        String base = profile.name == null ? "Player" : profile.name.trim();
        base = base.replaceAll("[^A-Za-z0-9]", "");
        if (base.isEmpty()) base = "Player";
        if (base.length() > 10) base = base.substring(0, 10);
        return String.format(Locale.UK, "%s#%04d", base, hash);
    }

    private void persistFriends(String profileId, List<Friend> friends) {
        JSONArray array = new JSONArray();
        for (Friend friend : friends) array.put(friend.toJson());
        preferences.edit().putString(KEY_FRIENDS_PREFIX + safe(profileId), array.toString()).apply();
    }

    private static String normalizeTag(String value) {
        if (value == null) return "";
        String clean = value.trim().replaceAll("\\s+", "");
        if (clean.length() > 24) clean = clean.substring(0, 24);
        return clean;
    }

    private static String displayNameFromTag(String tag) {
        int split = tag.indexOf('#');
        String name = split > 0 ? tag.substring(0, split) : tag;
        return name.isEmpty() ? "BluBox Friend" : name;
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) return ProfileStore.DEFAULT_PROFILE_ID;
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    static final class Friend {
        final String id;
        final String bluTag;
        final String displayName;
        final String status;
        final String playing;
        final long addedAt;

        Friend(String id, String bluTag, String displayName, String status, String playing, long addedAt) {
            this.id = id;
            this.bluTag = bluTag;
            this.displayName = displayName;
            this.status = status;
            this.playing = playing;
            this.addedAt = addedAt;
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("blutag", bluTag);
                object.put("name", displayName);
                object.put("status", status);
                object.put("playing", playing);
                object.put("added_at", addedAt);
            } catch (Exception ignored) {
            }
            return object;
        }

        static Friend fromJson(JSONObject object) {
            if (object == null) return null;
            String id = object.optString("id", "");
            String tag = object.optString("blutag", "");
            if (id.isEmpty() || tag.isEmpty()) return null;
            return new Friend(id, tag,
                    object.optString("name", displayNameFromTag(tag)),
                    object.optString("status", STATUS_OFFLINE),
                    object.optString("playing", "Not playing"),
                    object.optLong("added_at", System.currentTimeMillis()));
        }
    }

    static final class AddResult {
        final boolean success;
        final String message;
        final Friend friend;

        AddResult(boolean success, String message, Friend friend) {
            this.success = success;
            this.message = message;
            this.friend = friend;
        }
    }
}
