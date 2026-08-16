package uk.co.blustudio.blubox360;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

import xendroid.compose.Application;
import xendroid.compose.Emulator;
import xendroid.compose.Utils;

final class CoreConfig {
    interface Callback { void done(String message); }

    static final String GRAPHICS_PERFORMANCE = "performance";
    static final String GRAPHICS_BALANCED = "balanced";
    static final String GRAPHICS_HD = "hd";
    static final String GRAPHICS_CUSTOM = "custom";

    static final String ASPECT_WIDE = "wide";
    static final String ASPECT_FOUR_THREE = "four_three";
    static final String ASPECT_STRETCH = "stretch";

    static final String FABLE_II_TITLE_ID = "4D5307F1";
    private static final String FABLE_II_PATCH_ASSET =
            "patches/4D5307F1 - Fable II (BluBox Performance).patch.toml";
    private static final String FABLE_II_PATCH_FILE =
            "4D5307F1 - Fable II (BluBox Performance).patch.toml";

    private static final String PREFS = "blubox360_core";
    private static final String PREF_GRAPHICS = "graphics_mode";
    private static final String PREF_FPS = "show_fps";
    private static final String PREF_RENDER_SCALE = "render_scale";
    private static final String PREF_ANTIALIASING = "antialiasing";
    private static final String PREF_UPSCALER = "upscaler";
    private static final String PREF_ANISOTROPIC = "anisotropic";
    private static final String PREF_ASPECT = "aspect";
    private static final String PREF_INTERLACED = "interlaced";
    private static final String PREF_ASYNC_SHADERS = "async_shaders";
    private static final String PREF_SKIP_DRAWS = "skip_shader_draws";
    private static final String PREF_PIPELINE_THREADS = "pipeline_threads";
    private static final String PREF_PIPELINE_PRELOAD = "pipeline_preload";
    private static final String PREF_READBACK = "readback";
    private static final String PREF_VULKAN_DRIVER = "vulkan_driver";
    private static final String PREF_COOL_MODE = "cool_mode";
    private static final String PREF_XUID_PREFIX = "xuid_";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CoreConfig() {}

    static synchronized Emulator ensureCoreLoaded() {
        if (Emulator.get == null) {
            Emulator.load_library();
        }
        return Emulator.get;
    }

    static File storageRoot() {
        return new File(Utils.get_storage_root_path());
    }

    static File contentRoot() {
        return new File(storageRoot(), "content");
    }

    static File globalConfig() {
        return Application.get_global_config_file();
    }

    /** Restores the bundled configuration after an interrupted native write. */
    private static Emulator.Config openGlobalConfigRecovering() throws Exception {
        File global = globalConfig();
        try {
            return Emulator.Config.open_config_file(global.getAbsolutePath());
        } catch (Emulator.ConfigFileException unreadable) {
            copy(Application.get_default_config_file(), global);
            try {
                return Emulator.Config.open_config_file(global.getAbsolutePath());
            } catch (Emulator.ConfigFileException repairFailed) {
                throw new IllegalStateException(
                        "graphics configuration repair failed", repairFailed);
            }
        }
    }

    static synchronized void ensureFiles() throws Exception {
        storageRoot().mkdirs();
        contentRoot().mkdirs();
        new File(storageRoot(), "cache").mkdirs();
        new File(storageRoot(), "cache0").mkdirs();
        new File(storageRoot(), "cache1").mkdirs();
        File global = globalConfig();
        if (!global.isFile() || global.length() == 0) {
            copy(Application.get_default_config_file(), global);
        }
    }

    private static void copy(File source, File destination) throws Exception {
        destination.getParentFile().mkdirs();
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
        }
    }

    static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String graphicsMode(Context context) {
        return preferences(context).getString(PREF_GRAPHICS, GRAPHICS_BALANCED);
    }

    static void setGraphicsMode(Context context, String mode) {
        SharedPreferences.Editor editor = preferences(context).edit()
                .putString(PREF_GRAPHICS, mode);
        if (GRAPHICS_PERFORMANCE.equals(mode)) {
            editor.putInt(PREF_RENDER_SCALE, 1)
                    .putString(PREF_UPSCALER, "none")
                    .putString(PREF_ANTIALIASING, "off")
                    .putInt(PREF_ANISOTROPIC, -1)
                    .putString(PREF_READBACK, "uma")
                    .putBoolean(PREF_ASYNC_SHADERS, true)
                    .putBoolean(PREF_SKIP_DRAWS, true)
                    .putInt(PREF_PIPELINE_THREADS, -1)
                    .putBoolean(PREF_PIPELINE_PRELOAD, true);
        } else if (GRAPHICS_HD.equals(mode)) {
            editor.putInt(PREF_RENDER_SCALE, 2)
                    .putString(PREF_UPSCALER, "fsr")
                    .putString(PREF_ANTIALIASING, "fxaa")
                    .putInt(PREF_ANISOTROPIC, 4)
                    .putString(PREF_READBACK, "fast")
                    .putBoolean(PREF_ASYNC_SHADERS, true)
                    .putBoolean(PREF_SKIP_DRAWS, true)
                    .putInt(PREF_PIPELINE_THREADS, -1)
                    .putBoolean(PREF_PIPELINE_PRELOAD, true);
        } else if (GRAPHICS_BALANCED.equals(mode)) {
            editor.putInt(PREF_RENDER_SCALE, 1)
                    .putString(PREF_UPSCALER, "fsr")
                    .putString(PREF_ANTIALIASING, "fxaa")
                    .putInt(PREF_ANISOTROPIC, -1)
                    .putString(PREF_READBACK, "uma")
                    .putBoolean(PREF_ASYNC_SHADERS, true)
                    .putBoolean(PREF_SKIP_DRAWS, true)
                    .putInt(PREF_PIPELINE_THREADS, -1)
                    .putBoolean(PREF_PIPELINE_PRELOAD, true);
        }
        editor.apply();
        applySettingsAsync(context, null);
    }

    static int renderScale(Context context) {
        SharedPreferences prefs = preferences(context);
        int fallback = GRAPHICS_HD.equals(graphicsMode(context)) ? 2 : 1;
        return clamp(prefs.getInt(PREF_RENDER_SCALE, fallback), 1, 7);
    }

    static void setRenderScale(Context context, int scale) {
        scale = clamp(scale, 1, 7);
        SharedPreferences prefs = preferences(context);
        SharedPreferences.Editor editor = prefs.edit()
                .putInt(PREF_RENDER_SCALE, scale)
                .putString(PREF_GRAPHICS, GRAPHICS_CUSTOM);
        if (scale > 1 && "uma".equals(readbackMode(context))) {
            editor.putString(PREF_READBACK, "fast");
        }
        editor.apply();
        applySettingsAsync(context, null);
    }

    static String antialiasing(Context context) {
        String fallback = GRAPHICS_PERFORMANCE.equals(graphicsMode(context)) ? "off" : "fxaa";
        return oneOf(preferences(context).getString(PREF_ANTIALIASING, fallback),
                fallback, "off", "fxaa", "fxaa_extreme");
    }

    static void setAntialiasing(Context context, String value) {
        setCustomString(context, PREF_ANTIALIASING,
                oneOf(value, "fxaa", "off", "fxaa", "fxaa_extreme"));
    }

    static String upscaler(Context context) {
        String fallback = GRAPHICS_PERFORMANCE.equals(graphicsMode(context)) ? "none" : "fsr";
        return oneOf(preferences(context).getString(PREF_UPSCALER, fallback),
                fallback, "none", "cas", "fsr");
    }

    static void setUpscaler(Context context, String value) {
        setCustomString(context, PREF_UPSCALER,
                oneOf(value, "fsr", "none", "cas", "fsr"));
    }

    static int anisotropic(Context context) {
        int value = preferences(context).getInt(PREF_ANISOTROPIC, -1);
        return value >= -1 && value <= 5 ? value : -1;
    }

    static void setAnisotropic(Context context, int value) {
        if (value < -1 || value > 5) value = -1;
        preferences(context).edit()
                .putInt(PREF_ANISOTROPIC, value)
                .putString(PREF_GRAPHICS, GRAPHICS_CUSTOM)
                .apply();
        applySettingsAsync(context, null);
    }

    static String aspectMode(Context context) {
        return oneOf(preferences(context).getString(PREF_ASPECT, ASPECT_WIDE),
                ASPECT_WIDE, ASPECT_WIDE, ASPECT_FOUR_THREE, ASPECT_STRETCH);
    }

    static void setAspectMode(Context context, String value) {
        preferences(context).edit().putString(PREF_ASPECT,
                oneOf(value, ASPECT_WIDE, ASPECT_WIDE, ASPECT_FOUR_THREE,
                        ASPECT_STRETCH)).apply();
        applySettingsAsync(context, null);
    }

    static boolean interlaced(Context context) {
        return preferences(context).getBoolean(PREF_INTERLACED, false);
    }

    static void setInterlaced(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(PREF_INTERLACED, enabled).apply();
        applySettingsAsync(context, null);
    }

    static boolean asyncShaders(Context context) {
        return preferences(context).getBoolean(PREF_ASYNC_SHADERS, true);
    }

    static void setAsyncShaders(Context context, boolean enabled) {
        setCustomBoolean(context, PREF_ASYNC_SHADERS, enabled);
    }

    static boolean skipShaderDraws(Context context) {
        return preferences(context).getBoolean(PREF_SKIP_DRAWS, true);
    }

    static void setSkipShaderDraws(Context context, boolean enabled) {
        setCustomBoolean(context, PREF_SKIP_DRAWS, enabled);
    }

    static int pipelineThreads(Context context) {
        int value = preferences(context).getInt(PREF_PIPELINE_THREADS, -1);
        return value == -1 || value == 2 || value == 4 || value == 6 ? value : -1;
    }

    static void setPipelineThreads(Context context, int value) {
        if (value != -1 && value != 2 && value != 4 && value != 6) value = -1;
        preferences(context).edit()
                .putInt(PREF_PIPELINE_THREADS, value)
                .putString(PREF_GRAPHICS, GRAPHICS_CUSTOM)
                .apply();
        applySettingsAsync(context, null);
    }

    static boolean pipelinePreload(Context context) {
        return preferences(context).getBoolean(PREF_PIPELINE_PRELOAD, true);
    }

    static void setPipelinePreload(Context context, boolean enabled) {
        setCustomBoolean(context, PREF_PIPELINE_PRELOAD, enabled);
    }

    static String readbackMode(Context context) {
        String fallback = renderScale(context) > 1 ? "fast" : "uma";
        return oneOf(preferences(context).getString(PREF_READBACK, fallback),
                fallback, "uma", "fast", "all", "none");
    }

    static void setReadbackMode(Context context, String value) {
        value = oneOf(value, "fast", "uma", "fast", "all", "none");
        if ("uma".equals(value) && renderScale(context) > 1) value = "fast";
        setCustomString(context, PREF_READBACK, value);
    }

    static String vulkanDriverPath(Context context) {
        String path = preferences(context).getString(PREF_VULKAN_DRIVER, "");
        return path != null && new File(path).isFile() ? path : "";
    }

    static String vulkanDriverName(Context context) {
        String path = vulkanDriverPath(context);
        if (path.isEmpty()) return "System Vulkan driver";
        File parent = new File(path).getParentFile();
        return parent == null ? new File(path).getName() : parent.getName();
    }

    static boolean supportsCustomDriver() {
        return new File("/dev/kgsl-3d0").exists()
                || gpuName().toLowerCase(java.util.Locale.ROOT).contains("adreno");
    }

    static void setVulkanDriver(Context context, String path) {
        if (path == null || !new File(path).isFile()) path = "";
        preferences(context).edit().putString(PREF_VULKAN_DRIVER, path).apply();
        applySettingsAsync(context, null);
    }

    static void removeActiveDriverAsync(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        String path = vulkanDriverPath(app);
        preferences(app).edit().putString(PREF_VULKAN_DRIVER, "").apply();
        new Thread(() -> {
            String message;
            try {
                applySettings(app);
                if (!path.isEmpty()) deleteManagedDriver(new File(path));
                message = "System Vulkan driver selected. Custom package removed.";
            } catch (Throwable t) {
                message = "Driver package could not be removed: " + safeMessage(t);
            }
            post(callback, message);
        }, "BluBox-driver-remove").start();
    }

    static boolean showFps(Context context) {
        return preferences(context).getBoolean(PREF_FPS, false);
    }

    static void setShowFps(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(PREF_FPS, enabled).apply();
        applySettingsAsync(context, null);
    }

    static boolean coolMode(Context context) {
        return preferences(context).getBoolean(PREF_COOL_MODE, true);
    }

    static void setCoolMode(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(PREF_COOL_MODE, enabled).apply();
    }

    static void resetSettings(Context context) {
        preferences(context).edit()
                .remove(PREF_GRAPHICS)
                .remove(PREF_FPS)
                .remove(PREF_RENDER_SCALE)
                .remove(PREF_ANTIALIASING)
                .remove(PREF_UPSCALER)
                .remove(PREF_ANISOTROPIC)
                .remove(PREF_ASPECT)
                .remove(PREF_INTERLACED)
                .remove(PREF_ASYNC_SHADERS)
                .remove(PREF_SKIP_DRAWS)
                .remove(PREF_PIPELINE_THREADS)
                .remove(PREF_PIPELINE_PRELOAD)
                .remove(PREF_READBACK)
                .remove(PREF_VULKAN_DRIVER)
                .remove(PREF_COOL_MODE)
                .apply();
        applySettingsAsync(context, null);
    }

    static void clearShaderCacheAsync(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            String message;
            try {
                Emulator emulator = Emulator.get;
                if (emulator != null && emulator.is_running()) {
                    throw new IllegalStateException("close the running game first");
                }
                File cacheRoot = new File(storageRoot(), "cache_host");
                File shaders = new File(cacheRoot, "shaders");
                deleteTreeInside(cacheRoot, shaders);
                if (!shaders.mkdirs() && !shaders.isDirectory()) {
                    throw new IllegalStateException("shader cache folder could not be recreated");
                }
                message = "Shader and Vulkan pipeline caches cleared.";
            } catch (Throwable t) {
                message = "Shader cache could not be cleared: " + safeMessage(t);
            }
            post(callback, message);
        }, "BluBox-shader-cache").start();
    }

    static void applySettingsAsync(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            String message;
            try {
                applySettings(app);
                message = "Graphics settings saved for the next launch.";
            } catch (Throwable t) {
                message = "Could not save graphics settings: " + safeMessage(t);
            }
            post(callback, message);
        }, "BluBox-config").start();
    }

    static synchronized void applySettings(Context context) throws Exception {
        ensureCoreLoaded();
        ensureFiles();
        int scale = renderScale(context);
        String readback = readbackMode(context);
        if (scale > 1 && "uma".equals(readback)) readback = "fast";
        String aspect = aspectMode(context);
        boolean async = asyncShaders(context);
        boolean skipDraws = async && skipShaderDraws(context);
        int threads = async ? pipelineThreads(context) : 0;
        String driverPath = vulkanDriverPath(context);
        Emulator.Config config = openGlobalConfigRecovering();
        try {
            config.save_config_entry("GPU|draw_resolution_scale_x", Integer.toString(scale));
            config.save_config_entry("GPU|draw_resolution_scale_y", Integer.toString(scale));
            config.save_config_entry("Display|postprocess_scaling_and_sharpening",
                    upscaler(context));
            config.save_config_entry("Display|postprocess_antialiasing", antialiasing(context));
            config.save_config_entry("Display|postprocess_ffx_cas_additional_sharpness",
                    "none".equals(upscaler(context)) ? "0.0" : "0.25");
            config.save_config_entry("Display|show_debug_overlay",
                    Boolean.toString(showFps(context)));
            config.save_config_entry("Display|present_letterbox",
                    Boolean.toString(!ASPECT_STRETCH.equals(aspect)));
            config.save_config_entry("Kernel|widescreen",
                    Boolean.toString(!ASPECT_FOUR_THREE.equals(aspect)));
            config.save_config_entry("Video|interlaced",
                    Boolean.toString(interlaced(context)));
            config.save_config_entry("GPU|anisotropic_override",
                    Integer.toString(anisotropic(context)));
            config.save_config_entry("GPU|readback_resolve", readback);
            config.save_config_entry("GPU|readback_resolve_sync",
                    Boolean.toString("all".equals(readback)));
            config.save_config_entry("GPU|async_shader_compilation", Boolean.toString(async));
            config.save_config_entry("GPU|async_shader_vs_interpreter", Boolean.toString(async));
            config.save_config_entry("GPU|async_shader_skip_draws", Boolean.toString(skipDraws));
            config.save_config_entry("GPU|pipeline_storage_precreate",
                    Boolean.toString(pipelinePreload(context)));
            config.save_config_entry("GPU|framerate_limit", "0");
            config.save_config_entry("GPU|vulkan_mid_frame_submission_draws", "1300");
            config.save_config_entry("Vulkan|vulkan_pipeline_creation_threads",
                    Integer.toString(threads));
            config.save_config_entry("Vulkan|vulkan_async_skip_draws",
                    Boolean.toString(skipDraws));
            config.save_config_entry("Vulkan|adrenotools_force_max_clocks", "false");
            config.save_config_entry("Vulkan|vulkan_lib_path", driverPath);
            config.save_config_entry("HID|vibration", Boolean.toString(
                    ControllerSettings.load(context, 1).rumbleEnabled));
            config.save_config_entry("General|apply_patches", "true");
            config.save_config_entry("Kernel|default_achievements_backend", "GPD");
            config.save_config_entry("UI|show_achievement_notification", "true");
            config.close_config_file();
        } catch (Throwable t) {
            config.free_config();
            throw t;
        }
    }

    /** Applies normal settings, then the optional low-heat preset and title fixes. */
    static synchronized boolean applyLaunchSettings(Context context, String titleId,
                                                    String gameName) throws Exception {
        applySettings(context);
        boolean fableII = isFableII(titleId, gameName);
        if (!coolMode(context) && !fableII) return false;

        if (fableII) installFableIIPatch(context);
        Emulator.Config config = openGlobalConfigRecovering();
        try {
            // Low-heat mode avoids resolution and post-processing load, limits background
            // shader work, and keeps maximum-clock requests disabled. ClusterTune may place
            // independent CPU and GPU ceilings on top of this app profile.
            config.save_config_entry("GPU|draw_resolution_scale_x", "1");
            config.save_config_entry("GPU|draw_resolution_scale_y", "1");
            config.save_config_entry("Display|postprocess_scaling_and_sharpening", "bilinear");
            config.save_config_entry("Display|postprocess_antialiasing", "none");
            config.save_config_entry("Display|postprocess_ffx_cas_additional_sharpness", "0.0");
            config.save_config_entry("GPU|anisotropic_override", "-1");
            config.save_config_entry("GPU|readback_resolve", "uma");
            config.save_config_entry("GPU|readback_resolve_sync", "false");
            config.save_config_entry("GPU|render_target_path", "performance");
            config.save_config_entry("GPU|store_shaders", "true");
            config.save_config_entry("GPU|execute_unclipped_draw_vs_on_cpu", "true");
            config.save_config_entry("GPU|guest_display_refresh_cap", "true");
            config.save_config_entry("GPU|framerate_limit", fableII ? "24" : "30");
            config.save_config_entry("GPU|async_shader_compilation", "true");
            config.save_config_entry("GPU|async_shader_vs_interpreter", "true");
            config.save_config_entry("GPU|async_shader_skip_draws", "true");
            config.save_config_entry("GPU|pipeline_storage_precreate", "false");
            config.save_config_entry("Vulkan|vulkan_pipeline_creation_threads", "2");
            config.save_config_entry("Vulkan|vulkan_async_skip_draws", "true");
            config.save_config_entry("Vulkan|vulkan_placeholder_pipelines", "false");
            config.save_config_entry("Vulkan|vulkan_avoid_geometry_shaders", "true");
            config.save_config_entry("Vulkan|vulkan_dynamic_pipeline_state", "true");
            config.save_config_entry("GPU|vulkan_mid_frame_submission_draws", "0");
            config.save_config_entry("Vulkan|adrenotools_force_max_clocks", "false");
            config.save_config_entry("General|apply_patches", "true");
            config.close_config_file();
        } catch (Throwable t) {
            config.free_config();
            throw t;
        }
        return fableII;
    }

    private static boolean isFableII(String titleId, String gameName) {
        if (titleId != null && FABLE_II_TITLE_ID.equalsIgnoreCase(titleId.trim())) return true;
        if (gameName == null) return false;
        String normalized = gameName.toLowerCase(Locale.ROOT)
                .replace('_', ' ').replace('-', ' ').trim();
        return normalized.contains("fable ii") || normalized.contains("fable 2");
    }

    private static void installFableIIPatch(Context context) throws Exception {
        File patches = new File(storageRoot(), "patches");
        if (!patches.isDirectory() && !patches.mkdirs()) {
            throw new IllegalStateException("patch folder could not be created");
        }
        File destination = new File(patches, FABLE_II_PATCH_FILE);
        try (InputStream in = context.getApplicationContext().getAssets()
                .open(FABLE_II_PATCH_ASSET);
             FileOutputStream out = new FileOutputStream(destination, false)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
    }

    static void syncActiveProfileAsync(Context context, ProfileStore.Profile profile,
                                       Callback callback) {
        Context app = context.getApplicationContext();
        Bitmap avatar = AvatarView.renderProfile(context, profile, 64);
        new Thread(() -> {
            String message;
            try {
                String xuid = syncProfile(app, profile);
                writeAvatarTiles(xuid, avatar);
                message = "Xbox 360 profile ready: " + profile.name;
            } catch (Throwable t) {
                message = "Profile could not be prepared: " + safeMessage(t);
            } finally {
                avatar.recycle();
            }
            post(callback, message);
        }, "BluBox-profile").start();
    }

    static synchronized String syncProfile(Context context, ProfileStore.Profile profile)
            throws Exception {
        Emulator emulator = ensureCoreLoaded();
        ensureFiles();
        SharedPreferences prefs = preferences(context);
        String key = PREF_XUID_PREFIX + profile.id;
        String xuid = prefs.getString(key, "");
        if (!xuid.isEmpty()) {
            int status = emulator.rename_profile(contentRoot().getAbsolutePath(), xuid,
                    profile.name, 1, 103);
            if (status != 0) {
                xuid = "";
            }
        }
        if (xuid.isEmpty()) {
            xuid = emulator.create_profile(contentRoot().getAbsolutePath(), profile.name, 1, 103);
            if (xuid == null || xuid.isEmpty()) {
                throw new IllegalStateException("profile creation failed");
            }
            xuid = xuid.toUpperCase();
            prefs.edit().putString(key, xuid).apply();
        }

        Emulator.Config config = openGlobalConfigRecovering();
        try {
            config.save_config_entry("Profiles|logged_profile_slot_0_xuid", xuid);
            config.close_config_file();
        } catch (Throwable t) {
            config.free_config();
            throw t;
        }
        return xuid;
    }

    static boolean hasNativeProfile(Context context, String profileId) {
        String xuid = preferences(context).getString(PREF_XUID_PREFIX + profileId, "");
        if (xuid.isEmpty()) return false;
        File account = new File(contentRoot(), xuid + "/FFFE07D1/00010000/" + xuid + "/Account");
        return account.isFile() && account.length() > 0;
    }

    static String profileXuid(Context context, String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) return "";
        String value = preferences(context).getString(PREF_XUID_PREFIX + profileId, "");
        if (value == null) return "";
        value = value.trim().toUpperCase(java.util.Locale.ROOT);
        return value.matches("[0-9A-F]{16}") ? value : "";
    }

    private static void writeAvatarTiles(String xuid, Bitmap avatar) throws Exception {
        File dir = new File(contentRoot(), xuid + "/FFFE07D1/00010000/" + xuid);
        dir.mkdirs();
        writePng(avatar, 64, new File(dir, "tile_64.png"));
        writePng(avatar, 32, new File(dir, "tile_32.png"));
    }

    private static void writePng(Bitmap source, int size, File destination) throws Exception {
        Bitmap scaled = source.getWidth() == size && source.getHeight() == size
                ? source : Bitmap.createScaledBitmap(source, size, size, true);
        try (FileOutputStream out = new FileOutputStream(destination)) {
            if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IllegalStateException("avatar encoding failed");
            }
        } finally {
            if (scaled != source) scaled.recycle();
        }
    }

    static String gpuName() {
        String name = Application.gpu_device_name_vk;
        return name == null || name.trim().isEmpty() ? "No Vulkan GPU detected" : name;
    }

    private static void setCustomString(Context context, String key, String value) {
        preferences(context).edit()
                .putString(key, value)
                .putString(PREF_GRAPHICS, GRAPHICS_CUSTOM)
                .apply();
        applySettingsAsync(context, null);
    }

    private static void setCustomBoolean(Context context, String key, boolean value) {
        preferences(context).edit()
                .putBoolean(key, value)
                .putString(PREF_GRAPHICS, GRAPHICS_CUSTOM)
                .apply();
        applySettingsAsync(context, null);
    }

    private static String oneOf(String value, String fallback, String... allowed) {
        if (value != null) {
            for (String candidate : allowed) {
                if (candidate.equals(value)) return value;
            }
        }
        return fallback;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void deleteManagedDriver(File library) throws Exception {
        File root = Application.get_custom_driver_dir().getCanonicalFile();
        File folder = library.getCanonicalFile().getParentFile();
        if (folder == null || folder.equals(root)
                || !folder.getPath().startsWith(root.getPath() + File.separator)) {
            throw new SecurityException("driver path is outside BluBox storage");
        }
        deleteTreeInside(root, folder);
    }

    private static void deleteTreeInside(File allowedRoot, File target) throws Exception {
        File root = allowedRoot.getCanonicalFile();
        File file = target.getCanonicalFile();
        if (file.equals(root) || !file.getPath().startsWith(root.getPath() + File.separator)) {
            throw new SecurityException("refusing to delete outside BluBox cache");
        }
        deleteTree(file);
    }

    private static void deleteTree(File file) throws Exception {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteTree(child);
            }
        }
        if (!file.delete() && file.exists()) {
            throw new IllegalStateException("could not delete " + file.getName());
        }
    }

    private static void post(Callback callback, String message) {
        if (callback != null) MAIN.post(() -> callback.done(message));
    }

    private static String safeMessage(Throwable t) {
        String value = t.getMessage();
        return value == null || value.trim().isEmpty() ? t.getClass().getSimpleName() : value;
    }
}
