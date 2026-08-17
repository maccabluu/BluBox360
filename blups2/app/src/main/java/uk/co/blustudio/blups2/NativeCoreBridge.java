package uk.co.blustudio.blups2;

public final class NativeCoreBridge {
    private static boolean loaded;
    private static String loadError = "";

    static {
        try {
            System.loadLibrary("play_libretro");
            loaded = true;
        } catch (Throwable error) {
            loaded = false;
            loadError = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        }
    }

    private NativeCoreBridge() {}

    public static boolean isLoaded() {
        return loaded;
    }

    public static String status() {
        return loaded ? "Play! ARM64 native core packaged" : "Native core unavailable: " + loadError;
    }
}
