package uk.co.blustudio.blubox360;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class AchievementData {
    private static final int XDBF_MAGIC = 0x58444246;
    private static final int HEADER_SIZE = 24;
    private static final int ENTRY_SIZE = 18;
    private static final int FREE_ENTRY_SIZE = 8;
    private static final int ACHIEVEMENT_HEADER_SIZE = 0x1C;
    private static final int SECTION_ACHIEVEMENT = 1;
    private static final int SECTION_IMAGE = 2;
    private static final int FLAG_SHOW_UNACHIEVED = 0x8;
    private static final int FLAG_ACHIEVED = 0x20000;
    private static final long MAX_GPD_BYTES = 128L * 1024L * 1024L;
    private static final long WINDOWS_TO_UNIX_EPOCH_MS = 11_644_473_600_000L;

    private AchievementData() {}

    static Snapshot readTitle(Context context, String profileId, String titleId,
                              String gameName) {
        String normalized = normalizeTitleId(titleId);
        if (normalized.isEmpty()) return Snapshot.empty(gameName, titleId);
        File folder = profileFolder(context, profileId);
        if (folder == null) return Snapshot.empty(gameName, normalized);
        return parseFile(new File(folder, normalized + ".gpd"), normalized, gameName);
    }

    static Snapshot readNewestTitle(Context context, String profileId, String gameName) {
        File folder = profileFolder(context, profileId);
        if (folder == null || !folder.isDirectory()) return Snapshot.empty(gameName, "");
        File[] files = folder.listFiles(file -> file.isFile()
                && file.getName().matches("(?i)[0-9a-f]{8}\\.gpd")
                && !file.getName().equalsIgnoreCase("FFFE07D1.gpd"));
        if (files == null || files.length == 0) return Snapshot.empty(gameName, "");
        File newest = files[0];
        for (File file : files) {
            if (file.lastModified() > newest.lastModified()) newest = file;
        }
        String titleId = newest.getName().substring(0, 8).toUpperCase(Locale.ROOT);
        return parseFile(newest, titleId, gameName);
    }

    static ProfileSummary readProfile(Context context, String profileId,
                                      List<GameStore.Game> games) {
        File folder = profileFolder(context, profileId);
        if (folder == null || !folder.isDirectory()) return ProfileSummary.empty();

        Map<String, String> names = new HashMap<>();
        if (games != null) {
            for (GameStore.Game game : games) {
                String id = normalizeTitleId(game.titleId);
                if (!id.isEmpty()) names.put(id, game.name);
            }
        }

        File[] files = folder.listFiles(file -> file.isFile()
                && file.getName().matches("(?i)[0-9a-f]{8}\\.gpd"));
        if (files == null) return ProfileSummary.empty();

        List<Snapshot> titles = new ArrayList<>();
        List<Achievement> recent = new ArrayList<>();
        int unlocked = 0;
        int total = 0;
        int earned = 0;
        int possible = 0;
        for (File file : files) {
            String titleId = file.getName().substring(0, 8).toUpperCase(Locale.ROOT);
            if ("FFFE07D1".equals(titleId)) continue;
            Snapshot snapshot = parseFile(file, titleId,
                    names.containsKey(titleId) ? names.get(titleId) : "Xbox 360 title");
            if (!snapshot.available) continue;
            titles.add(snapshot);
            unlocked += snapshot.unlockedCount;
            total += snapshot.totalCount;
            earned += snapshot.earnedScore;
            possible += snapshot.totalScore;
            for (Achievement achievement : snapshot.achievements) {
                if (achievement.unlocked) recent.add(achievement.withGame(
                        snapshot.gameName, snapshot.titleId));
            }
        }
        titles.sort(Comparator.comparingLong((Snapshot item) -> item.modifiedAt).reversed());
        recent.sort(Comparator.comparingLong((Achievement item) -> item.unlockTimeMs).reversed());
        if (recent.size() > 20) recent = new ArrayList<>(recent.subList(0, 20));
        return new ProfileSummary(titles, recent, unlocked, total, earned, possible);
    }

    static Achievement newlyUnlocked(Snapshot previous, Snapshot current) {
        if (previous == null || current == null || !previous.available || !current.available) {
            return null;
        }
        Set<Integer> before = new HashSet<>();
        for (Achievement achievement : previous.achievements) {
            if (achievement.unlocked) before.add(achievement.id);
        }
        Achievement newest = null;
        for (Achievement achievement : current.achievements) {
            if (achievement.unlocked && !before.contains(achievement.id)
                    && (newest == null || achievement.unlockTimeMs > newest.unlockTimeMs)) {
                newest = achievement;
            }
        }
        return newest;
    }

    static boolean changed(Snapshot previous, Snapshot current) {
        if (previous == null || current == null) return true;
        if (previous.available != current.available
                || previous.modifiedAt != current.modifiedAt
                || previous.fileSize != current.fileSize
                || previous.unlockedCount != current.unlockedCount
                || previous.earnedScore != current.earnedScore
                || previous.totalCount != current.totalCount) return true;
        for (int i = 0; i < previous.achievements.size(); i++) {
            if (i >= current.achievements.size()) return true;
            Achievement a = previous.achievements.get(i);
            Achievement b = current.achievements.get(i);
            if (a.id != b.id || a.unlocked != b.unlocked || a.score != b.score) return true;
        }
        return previous.achievements.size() != current.achievements.size();
    }

    static Snapshot parseFile(File file, String titleId, String gameName) {
        if (file == null || !file.isFile() || file.length() <= HEADER_SIZE
                || file.length() > MAX_GPD_BYTES) {
            return Snapshot.empty(gameName, titleId);
        }
        try {
            long beforeLength = file.length();
            long beforeModified = file.lastModified();
            byte[] data = readAll(file, beforeLength);
            if (file.length() != beforeLength || file.lastModified() != beforeModified) {
                return Snapshot.empty(gameName, titleId);
            }
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
            if (buffer.getInt(0) != XDBF_MAGIC) return Snapshot.empty(gameName, titleId);
            long entryCount = unsignedInt(buffer.getInt(8));
            long entryUsed = unsignedInt(buffer.getInt(12));
            long freeCount = unsignedInt(buffer.getInt(16));
            if (entryCount < 1 || entryUsed > entryCount || entryCount > 1_000_000
                    || freeCount > 1_000_000) return Snapshot.empty(gameName, titleId);
            long dataStartLong = HEADER_SIZE + entryCount * ENTRY_SIZE
                    + freeCount * FREE_ENTRY_SIZE;
            if (dataStartLong < HEADER_SIZE || dataStartLong > data.length) {
                return Snapshot.empty(gameName, titleId);
            }
            int dataStart = (int) dataStartLong;
            Map<Long, Location> images = new HashMap<>();
            List<Location> achievements = new ArrayList<>();
            for (int i = 0; i < (int) entryUsed; i++) {
                int position = HEADER_SIZE + i * ENTRY_SIZE;
                if (position < 0 || position + ENTRY_SIZE > dataStart) break;
                int section = buffer.getShort(position) & 0xFFFF;
                long id = buffer.getLong(position + 2);
                long offset = unsignedInt(buffer.getInt(position + 10));
                long size = unsignedInt(buffer.getInt(position + 14));
                long absolute = dataStartLong + offset;
                if (size <= 0 || absolute < dataStartLong || absolute + size > data.length) continue;
                Location location = new Location(id, (int) absolute, (int) size);
                if (section == SECTION_IMAGE) images.put(id, location);
                else if (section == SECTION_ACHIEVEMENT && id <= 0xFFFFFFFFL) {
                    achievements.add(location);
                }
            }

            List<Achievement> result = new ArrayList<>();
            for (Location location : achievements) {
                Achievement achievement = parseAchievement(data, buffer, location, images);
                if (achievement != null) result.add(achievement);
            }
            result.sort((a, b) -> {
                if (a.unlocked != b.unlocked) return a.unlocked ? -1 : 1;
                if (a.unlocked && a.unlockTimeMs != b.unlockTimeMs) {
                    return Long.compare(b.unlockTimeMs, a.unlockTimeMs);
                }
                return Integer.compare(a.id, b.id);
            });
            int unlockedCount = 0;
            int earnedScore = 0;
            int totalScore = 0;
            for (Achievement achievement : result) {
                totalScore += Math.max(0, achievement.score);
                if (achievement.unlocked) {
                    unlockedCount++;
                    earnedScore += Math.max(0, achievement.score);
                }
            }
            return new Snapshot(true, cleanGameName(gameName), normalizeTitleId(titleId),
                    Collections.unmodifiableList(result), unlockedCount, result.size(),
                    earnedScore, totalScore, beforeModified, beforeLength);
        } catch (Throwable ignored) {
            return Snapshot.empty(gameName, titleId);
        }
    }

    private static Achievement parseAchievement(byte[] data, ByteBuffer buffer,
                                                Location location,
                                                Map<Long, Location> images) {
        int start = location.offset;
        int end = start + location.size;
        if (location.size < ACHIEVEMENT_HEADER_SIZE || end > data.length) return null;
        int magic = buffer.getInt(start);
        if (magic != ACHIEVEMENT_HEADER_SIZE) return null;
        int id = buffer.getInt(start + 4);
        long imageId = unsignedInt(buffer.getInt(start + 8));
        int score = buffer.getInt(start + 12);
        int flags = buffer.getInt(start + 16);
        long unlockFileTime = buffer.getLong(start + 20);
        int[] cursor = {start + ACHIEVEMENT_HEADER_SIZE};
        String name = readUtf16Be(data, cursor, end);
        String unlockedDescription = readUtf16Be(data, cursor, end);
        String lockedDescription = readUtf16Be(data, cursor, end);
        boolean unlocked = (flags & FLAG_ACHIEVED) != 0;
        boolean visibleWhileLocked = (flags & FLAG_SHOW_UNACHIEVED) != 0;
        if (!unlocked && !visibleWhileLocked) {
            name = "Secret achievement";
            lockedDescription = "Keep playing to reveal this achievement.";
        }
        if (name.isEmpty()) name = unlocked ? "Achievement unlocked" : "Locked achievement";
        byte[] icon = null;
        Location image = images.get(imageId);
        if (image != null && image.size > 0 && image.size <= 4 * 1024 * 1024) {
            icon = Arrays.copyOfRange(data, image.offset, image.offset + image.size);
        }
        long unlockTimeMs = fileTimeToUnixMillis(unlockFileTime);
        return new Achievement(id, imageId, Math.max(0, score), flags, unlocked,
                visibleWhileLocked, name, unlockedDescription, lockedDescription,
                unlockTimeMs, icon, "", "");
    }

    private static byte[] readAll(File file, long length) throws Exception {
        if (length <= 0 || length > Integer.MAX_VALUE || length > MAX_GPD_BYTES) {
            throw new IllegalArgumentException("Invalid GPD size");
        }
        byte[] data = new byte[(int) length];
        int offset = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count < 0) break;
                if (count > 0) offset += count;
            }
        }
        if (offset != data.length) throw new IllegalStateException("Incomplete GPD read");
        return data;
    }

    private static String readUtf16Be(byte[] data, int[] cursor, int end) {
        StringBuilder value = new StringBuilder();
        int position = cursor[0];
        while (position + 1 < end && value.length() < 4096) {
            char character = (char) (((data[position] & 0xFF) << 8)
                    | (data[position + 1] & 0xFF));
            position += 2;
            if (character == 0) break;
            if (character >= 0x20 || character == '\n') value.append(character);
        }
        cursor[0] = position;
        return value.toString().trim();
    }

    private static long fileTimeToUnixMillis(long fileTime) {
        if (fileTime <= 0) return 0L;
        long result = fileTime / 10_000L - WINDOWS_TO_UNIX_EPOCH_MS;
        long now = System.currentTimeMillis();
        return result > 0 && result < now + 86_400_000L ? result : 0L;
    }

    private static long unsignedInt(int value) {
        return value & 0xFFFFFFFFL;
    }

    private static File profileFolder(Context context, String profileId) {
        String xuid = CoreConfig.profileXuid(context, profileId);
        if (xuid.isEmpty()) return null;
        return new File(CoreConfig.contentRoot(), xuid + "/FFFE07D1/00010000/" + xuid);
    }

    static String normalizeTitleId(String value) {
        if (value == null) return "";
        String clean = value.trim().toUpperCase(Locale.ROOT);
        return clean.matches("[0-9A-F]{8}") ? clean : "";
    }

    private static String cleanGameName(String value) {
        return value == null || value.trim().isEmpty() ? "Xbox 360 game" : value.trim();
    }

    static final class Achievement {
        final int id;
        final long imageId;
        final int score;
        final int flags;
        final boolean unlocked;
        final boolean visibleWhileLocked;
        final String name;
        final String unlockedDescription;
        final String lockedDescription;
        final long unlockTimeMs;
        final byte[] icon;
        final String gameName;
        final String titleId;

        Achievement(int id, long imageId, int score, int flags, boolean unlocked,
                    boolean visibleWhileLocked, String name, String unlockedDescription,
                    String lockedDescription, long unlockTimeMs, byte[] icon,
                    String gameName, String titleId) {
            this.id = id;
            this.imageId = imageId;
            this.score = score;
            this.flags = flags;
            this.unlocked = unlocked;
            this.visibleWhileLocked = visibleWhileLocked;
            this.name = name;
            this.unlockedDescription = unlockedDescription;
            this.lockedDescription = lockedDescription;
            this.unlockTimeMs = unlockTimeMs;
            this.icon = icon;
            this.gameName = gameName;
            this.titleId = titleId;
        }

        Achievement withGame(String gameName, String titleId) {
            return new Achievement(id, imageId, score, flags, unlocked,
                    visibleWhileLocked, name, unlockedDescription, lockedDescription,
                    unlockTimeMs, icon, gameName, titleId);
        }

        String description() {
            return unlocked ? unlockedDescription : lockedDescription;
        }
    }

    static final class Snapshot {
        final boolean available;
        final String gameName;
        final String titleId;
        final List<Achievement> achievements;
        final int unlockedCount;
        final int totalCount;
        final int earnedScore;
        final int totalScore;
        final long modifiedAt;
        final long fileSize;

        Snapshot(boolean available, String gameName, String titleId,
                 List<Achievement> achievements, int unlockedCount, int totalCount,
                 int earnedScore, int totalScore, long modifiedAt, long fileSize) {
            this.available = available;
            this.gameName = gameName;
            this.titleId = titleId;
            this.achievements = achievements;
            this.unlockedCount = unlockedCount;
            this.totalCount = totalCount;
            this.earnedScore = earnedScore;
            this.totalScore = totalScore;
            this.modifiedAt = modifiedAt;
            this.fileSize = fileSize;
        }

        static Snapshot empty(String gameName, String titleId) {
            return new Snapshot(false, cleanGameName(gameName), normalizeTitleId(titleId),
                    Collections.emptyList(), 0, 0, 0, 0, 0L, 0L);
        }

        int percent() {
            if (totalScore > 0) return Math.min(100, Math.round(earnedScore * 100f / totalScore));
            if (totalCount > 0) return Math.min(100, Math.round(unlockedCount * 100f / totalCount));
            return 0;
        }
    }

    static final class ProfileSummary {
        final List<Snapshot> titles;
        final List<Achievement> recent;
        final int unlockedCount;
        final int totalCount;
        final int earnedScore;
        final int totalScore;

        ProfileSummary(List<Snapshot> titles, List<Achievement> recent,
                       int unlockedCount, int totalCount,
                       int earnedScore, int totalScore) {
            this.titles = Collections.unmodifiableList(new ArrayList<>(titles));
            this.recent = Collections.unmodifiableList(new ArrayList<>(recent));
            this.unlockedCount = unlockedCount;
            this.totalCount = totalCount;
            this.earnedScore = earnedScore;
            this.totalScore = totalScore;
        }

        static ProfileSummary empty() {
            return new ProfileSummary(Collections.emptyList(), Collections.emptyList(),
                    0, 0, 0, 0);
        }
    }

    private static final class Location {
        final long id;
        final int offset;
        final int size;

        Location(long id, int offset, int size) {
            this.id = id;
            this.offset = offset;
            this.size = size;
        }
    }
}
