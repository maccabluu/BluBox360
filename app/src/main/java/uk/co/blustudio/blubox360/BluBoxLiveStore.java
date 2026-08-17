package uk.co.blustudio.blubox360;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.UUID;

/** Local account/session foundation for BluBox Live.
 *
 * 0.17.2 deliberately does not pretend to be Xbox Live. A remote BluBox service
 * still needs to be deployed before cross-device friend requests and presence sync
 * are enabled. This class keeps the local identity stable so a later backend can
 * bind it without changing the user's BluBox profile or saves.
 */
final class BluBoxLiveStore {
    private static final String PREFS = "blubox_live_v1";
    private static final String KEY_ACCOUNT_PREFIX = "account_";
    private static final String KEY_TOKEN_PREFIX = "token_";
    private static final String KEY_JOINED_PREFIX = "joined_";

    private final SharedPreferences preferences;

    BluBoxLiveStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    synchronized LiveAccount account(String profileId) {
        String safe = safe(profileId);
        String accountId = preferences.getString(KEY_ACCOUNT_PREFIX + safe, "");
        if (accountId == null || accountId.isEmpty()) return null;
        return new LiveAccount(
                accountId,
                preferences.getString(KEY_TOKEN_PREFIX + safe, ""),
                preferences.getLong(KEY_JOINED_PREFIX + safe, 0L));
    }

    synchronized LiveAccount createAccount(String profileId) {
        LiveAccount existing = account(profileId);
        if (existing != null) return existing;

        String accountId = "BLU-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.UK);
        String token = randomToken();
        long joined = System.currentTimeMillis();
        String safe = safe(profileId);
        preferences.edit()
                .putString(KEY_ACCOUNT_PREFIX + safe, accountId)
                .putString(KEY_TOKEN_PREFIX + safe, token)
                .putLong(KEY_JOINED_PREFIX + safe, joined)
                .apply();
        return new LiveAccount(accountId, token, joined);
    }

    boolean isRemoteServiceLive() {
        return false;
    }

    String serviceStatus() {
        return isRemoteServiceLive() ? "Connected" : "Account ready • server connection coming next";
    }

    private static String randomToken() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) return ProfileStore.DEFAULT_PROFILE_ID;
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    static final class LiveAccount {
        final String accountId;
        final String sessionToken;
        final long joinedAt;

        LiveAccount(String accountId, String sessionToken, long joinedAt) {
            this.accountId = accountId;
            this.sessionToken = sessionToken;
            this.joinedAt = joinedAt;
        }
    }
}
