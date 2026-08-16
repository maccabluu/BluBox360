package uk.co.blustudio.blubox360;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

final class GameStore {
    private static final String PREFS = "blubox360_library";
    private static final String KEY_GAMES = "games";
    private static final String KEY_FOLDER_PATH = "game_folder_path";
    private static final String KEY_FOLDER_URI = "game_folder_uri";
    private final SharedPreferences prefs;
    private final List<Game> games = new ArrayList<>();

    GameStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        reload();
    }

    synchronized void reload() {
        games.clear();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_GAMES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                Game game = Game.fromJson(array.optJSONObject(i));
                if (game != null) games.add(game);
            }
        } catch (Throwable ignored) {
            games.clear();
        }
        sort();
    }

    synchronized List<Game> games() {
        List<Game> copy = new ArrayList<>();
        for (Game game : games) copy.add(game.copy());
        return Collections.unmodifiableList(copy);
    }

    synchronized boolean containsPath(String path) {
        return path != null && findPath(path) != null;
    }

    synchronized void setGameFolder(String path, String uri) {
        prefs.edit()
                .putString(KEY_FOLDER_PATH, path == null ? "" : path)
                .putString(KEY_FOLDER_URI, uri == null ? "" : uri)
                .apply();
    }

    String gameFolderPath() {
        return prefs.getString(KEY_FOLDER_PATH, "");
    }

    String gameFolderUri() {
        return prefs.getString(KEY_FOLDER_URI, "");
    }

    synchronized Game addOrUpdate(String path, String uri, String displayName, long size) {
        Game existing = findPath(path);
        if (existing == null) {
            existing = new Game(UUID.randomUUID().toString(), path, uri,
                    cleanName(displayName), null, null, size, 0L);
            games.add(existing);
        } else {
            existing.uri = uri;
            existing.size = size;
            if (existing.name == null || existing.name.isEmpty()) {
                existing.name = cleanName(displayName);
            }
        }
        persist();
        return existing.copy();
    }

    synchronized void applyMetadata(String id, String name, String titleId, byte[] icon) {
        Game game = findId(id);
        if (game == null) return;
        if (name != null && !name.trim().isEmpty()) game.name = name.trim();
        if (titleId != null && !titleId.trim().isEmpty()) game.titleId = titleId.trim();
        if (icon != null && icon.length > 0) game.iconBase64 = Base64.encodeToString(icon, Base64.NO_WRAP);
        persist();
    }

    synchronized void markPlayed(String id) {
        Game game = findId(id);
        if (game == null) return;
        game.lastPlayed = System.currentTimeMillis();
        sort();
        persist();
    }

    synchronized void remove(String id) {
        Game game = findId(id);
        if (game != null) {
            games.remove(game);
            persist();
        }
    }

    private void sort() {
        games.sort(Comparator.comparingLong((Game g) -> g.lastPlayed).reversed()
                .thenComparing(g -> g.name == null ? "" : g.name));
    }

    private Game findId(String id) {
        for (Game game : games) if (game.id.equals(id)) return game;
        return null;
    }

    private Game findPath(String path) {
        for (Game game : games) if (game.path.equals(path)) return game;
        return null;
    }

    private synchronized void persist() {
        JSONArray array = new JSONArray();
        for (Game game : games) array.put(game.toJson());
        prefs.edit().putString(KEY_GAMES, array.toString()).apply();
    }

    private static String cleanName(String value) {
        if (value == null || value.trim().isEmpty()) return "Xbox 360 game";
        String name = value.trim();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name;
    }

    static final class Game {
        final String id;
        final String path;
        String uri;
        String name;
        String titleId;
        String iconBase64;
        long size;
        long lastPlayed;

        Game(String id, String path, String uri, String name, String titleId,
             String iconBase64, long size, long lastPlayed) {
            this.id = id;
            this.path = path;
            this.uri = uri;
            this.name = name;
            this.titleId = titleId;
            this.iconBase64 = iconBase64;
            this.size = size;
            this.lastPlayed = lastPlayed;
        }

        Game copy() {
            return new Game(id, path, uri, name, titleId, iconBase64, size, lastPlayed);
        }

        byte[] iconBytes() {
            if (iconBase64 == null || iconBase64.isEmpty()) return null;
            try { return Base64.decode(iconBase64, Base64.DEFAULT); }
            catch (Throwable ignored) { return null; }
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("path", path);
                json.put("uri", uri == null ? "" : uri);
                json.put("name", name);
                json.put("title_id", titleId == null ? "" : titleId);
                json.put("icon", iconBase64 == null ? "" : iconBase64);
                json.put("size", size);
                json.put("last_played", lastPlayed);
            } catch (Throwable ignored) {
            }
            return json;
        }

        static Game fromJson(JSONObject json) {
            if (json == null) return null;
            String id = json.optString("id", "");
            String path = json.optString("path", "");
            if (id.isEmpty() || path.isEmpty()) return null;
            return new Game(id, path, json.optString("uri", ""),
                    json.optString("name", "Xbox 360 game"),
                    json.optString("title_id", ""), json.optString("icon", ""),
                    json.optLong("size", 0L), json.optLong("last_played", 0L));
        }
    }
}
