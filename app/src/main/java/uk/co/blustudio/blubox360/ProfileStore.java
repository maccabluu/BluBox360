package uk.co.blustudio.blubox360;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

final class ProfileStore {
    static final int MAX_PROFILES = 8;
    static final String DEFAULT_PROFILE_ID = "default";

    private static final String PREFS = "blubox_profiles_v1";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ACTIVE = "active_profile";

    private final Context context;
    private final SharedPreferences preferences;
    private final List<Profile> profiles = new ArrayList<>();
    private String activeProfileId;

    ProfileStore(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        reload();
    }

    synchronized void reload() {
        profiles.clear();
        String encoded = preferences.getString(KEY_PROFILES, "");
        if (encoded != null && !encoded.isEmpty()) {
            try {
                JSONArray array = new JSONArray(encoded);
                for (int i = 0; i < array.length() && profiles.size() < MAX_PROFILES; i++) {
                    JSONObject object = array.optJSONObject(i);
                    if (object == null) {
                        continue;
                    }
                    Profile profile = Profile.fromJson(object);
                    if (profile != null && findById(profile.id) == null) {
                        profiles.add(profile);
                    }
                }
            } catch (Exception ignored) {
                profiles.clear();
            }
        }
        if (profiles.isEmpty()) {
            profiles.add(new Profile(DEFAULT_PROFILE_ID, "Player 1", 2, 0, 0, 0, 0,
                    System.currentTimeMillis()));
        }
        activeProfileId = preferences.getString(KEY_ACTIVE, profiles.get(0).id);
        if (findById(activeProfileId) == null) {
            activeProfileId = profiles.get(0).id;
        }
        persist();
    }

    synchronized List<Profile> getProfiles() {
        List<Profile> result = new ArrayList<>();
        for (Profile profile : profiles) {
            result.add(profile.copy());
        }
        return Collections.unmodifiableList(result);
    }

    synchronized Profile getActive() {
        Profile active = findById(activeProfileId);
        if (active == null) {
            active = profiles.get(0);
            activeProfileId = active.id;
        }
        return active.copy();
    }

    synchronized Profile getById(String id) {
        Profile profile = findById(id);
        return profile == null ? null : profile.copy();
    }

    synchronized boolean setActive(String id) {
        if (findById(id) == null) {
            return false;
        }
        activeProfileId = id;
        persist();
        return true;
    }

    synchronized Profile newDraft() {
        int number = profiles.size() + 1;
        return new Profile(UUID.randomUUID().toString(), "Player " + number,
                number % AvatarView.SKIN_COLORS.length,
                number % AvatarView.HAIR_COLORS.length,
                number % AvatarView.OUTFIT_COLORS.length,
                number % AvatarView.EXPRESSION_COUNT,
                number % AvatarView.BACKGROUND_COLORS.length,
                System.currentTimeMillis());
    }

    synchronized SaveResult save(Profile candidate) {
        if (candidate == null) {
            return new SaveResult(false, "Profile is missing.", null);
        }
        String normalizedName = normalizeName(candidate.name);
        if (normalizedName.isEmpty()) {
            return new SaveResult(false, "Enter a gamertag.", null);
        }
        for (Profile profile : profiles) {
            if (!profile.id.equals(candidate.id) && profile.name.equalsIgnoreCase(normalizedName)) {
                return new SaveResult(false, "That gamertag is already used.", null);
            }
        }

        Profile clean = candidate.copy();
        clean.name = normalizedName;
        clean.skin = wrap(clean.skin, AvatarView.SKIN_COLORS.length);
        clean.hair = wrap(clean.hair, AvatarView.HAIR_COLORS.length);
        clean.outfit = wrap(clean.outfit, AvatarView.OUTFIT_COLORS.length);
        clean.expression = wrap(clean.expression, AvatarView.EXPRESSION_COUNT);
        clean.background = wrap(clean.background, AvatarView.BACKGROUND_COLORS.length);

        Profile existing = findById(clean.id);
        if (existing == null) {
            if (profiles.size() >= MAX_PROFILES) {
                return new SaveResult(false, "BluBox supports up to " + MAX_PROFILES + " profiles.", null);
            }
            profiles.add(clean);
        } else {
            int index = profiles.indexOf(existing);
            profiles.set(index, clean);
        }
        persist();
        return new SaveResult(true, "Profile saved.", clean.copy());
    }

    synchronized boolean delete(String id) {
        if (profiles.size() <= 1) {
            return false;
        }
        Profile profile = findById(id);
        if (profile == null) {
            return false;
        }
        profiles.remove(profile);
        if (id.equals(activeProfileId)) {
            activeProfileId = profiles.get(0).id;
        }
        persist();
        return true;
    }

    File profileDirectory(String profileId) {
        String safeId = profileId == null ? DEFAULT_PROFILE_ID
                : profileId.replaceAll("[^A-Za-z0-9._-]", "_");
        return new File(context.getFilesDir(), "profiles/" + safeId);
    }

    File profileHddFile(String profileId) {
        return new File(profileDirectory(profileId), "hdd.img");
    }

    boolean hasHdd(String profileId) {
        File profileHdd = profileHddFile(profileId);
        if (profileHdd.isFile() && profileHdd.length() > 0) {
            return true;
        }
        return DEFAULT_PROFILE_ID.equals(profileId) &&
                new File(context.getFilesDir(), "xbox-files/hdd.img").isFile();
    }

    private Profile findById(String id) {
        if (id == null) {
            return null;
        }
        for (Profile profile : profiles) {
            if (profile.id.equals(id)) {
                return profile;
            }
        }
        return null;
    }

    private synchronized void persist() {
        JSONArray array = new JSONArray();
        for (Profile profile : profiles) {
            array.put(profile.toJson());
        }
        preferences.edit()
                .putString(KEY_PROFILES, array.toString())
                .putString(KEY_ACTIVE, activeProfileId)
                .apply();
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String value = name.trim().replaceAll("\\s+", " ");
        if (value.length() > 15) {
            value = value.substring(0, 15).trim();
        }
        return value;
    }

    private static int wrap(int value, int count) {
        if (count <= 0) {
            return 0;
        }
        int result = value % count;
        return result < 0 ? result + count : result;
    }

    static final class Profile {
        final String id;
        String name;
        int skin;
        int hair;
        int outfit;
        int expression;
        int background;
        final long createdAt;

        Profile(String id, String name, int skin, int hair, int outfit,
                int expression, int background, long createdAt) {
            this.id = id;
            this.name = name;
            this.skin = skin;
            this.hair = hair;
            this.outfit = outfit;
            this.expression = expression;
            this.background = background;
            this.createdAt = createdAt;
        }

        Profile copy() {
            return new Profile(id, name, skin, hair, outfit, expression, background, createdAt);
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("id", id);
                object.put("name", name);
                object.put("skin", skin);
                object.put("hair", hair);
                object.put("outfit", outfit);
                object.put("expression", expression);
                object.put("background", background);
                object.put("created_at", createdAt);
            } catch (Exception ignored) {
            }
            return object;
        }

        static Profile fromJson(JSONObject object) {
            String id = object.optString("id", "").trim();
            String name = normalizeName(object.optString("name", ""));
            if (id.isEmpty() || name.isEmpty()) {
                return null;
            }
            return new Profile(
                    id,
                    name,
                    wrap(object.optInt("skin", 2), AvatarView.SKIN_COLORS.length),
                    wrap(object.optInt("hair", 0), AvatarView.HAIR_COLORS.length),
                    wrap(object.optInt("outfit", 0), AvatarView.OUTFIT_COLORS.length),
                    wrap(object.optInt("expression", 0), AvatarView.EXPRESSION_COUNT),
                    wrap(object.optInt("background", 0), AvatarView.BACKGROUND_COLORS.length),
                    object.optLong("created_at", System.currentTimeMillis()));
        }
    }

    static final class SaveResult {
        final boolean success;
        final String message;
        final Profile profile;

        SaveResult(boolean success, String message, Profile profile) {
            this.success = success;
            this.message = message;
            this.profile = profile;
        }
    }
}
