package uk.co.blustudio.blubox360;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

final class CoverArtStore {
    private static final int MAX_EDGE = 1600;

    private CoverArtStore() { }

    static boolean has(Context context, String gameId) {
        return file(context, gameId).isFile();
    }

    static Bitmap load(Context context, String gameId) {
        File source = file(context, gameId);
        if (!source.isFile()) return null;
        try {
            return BitmapFactory.decodeFile(source.getAbsolutePath());
        } catch (Throwable ignored) {
            return null;
        }
    }

    static void importCover(Context context, String gameId, Uri source) throws Exception {
        if (source == null) throw new IllegalArgumentException("No cover image was selected");
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) throw new IllegalStateException("Android could not open the cover image");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                || bounds.outWidth > 40_000 || bounds.outHeight > 40_000) {
            throw new IllegalArgumentException("Select a valid JPG, PNG, or WEBP cover image");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > MAX_EDGE * 2
                || bounds.outHeight / options.inSampleSize > MAX_EDGE * 2) {
            options.inSampleSize *= 2;
        }
        Bitmap decoded;
        try (InputStream in = context.getContentResolver().openInputStream(source)) {
            if (in == null) throw new IllegalStateException("Android could not reopen the cover image");
            decoded = BitmapFactory.decodeStream(in, null, options);
        }
        if (decoded == null) throw new IllegalArgumentException("The cover image could not be decoded");

        Bitmap output = decoded;
        int width = decoded.getWidth();
        int height = decoded.getHeight();
        int largest = Math.max(width, height);
        if (largest > MAX_EDGE) {
            float scale = (float) MAX_EDGE / largest;
            output = Bitmap.createScaledBitmap(decoded,
                    Math.max(1, Math.round(width * scale)),
                    Math.max(1, Math.round(height * scale)), true);
        }

        File destination = file(context, gameId);
        File temporary = new File(folder(context), destination.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temporary, false)) {
            if (!output.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                throw new IllegalStateException("The cover image could not be saved");
            }
            out.getFD().sync();
        } catch (Throwable t) {
            temporary.delete();
            throw t;
        } finally {
            if (output != decoded) output.recycle();
            decoded.recycle();
        }
        File backup = new File(folder(context), destination.getName() + ".bak");
        if (backup.exists()) backup.delete();
        if (destination.exists() && !destination.renameTo(backup)) {
            temporary.delete();
            throw new IllegalStateException("The old cover image could not be replaced");
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete();
            if (backup.exists()) backup.renameTo(destination);
            throw new IllegalStateException("The cover image could not be installed");
        }
        if (backup.exists()) backup.delete();
    }

    static void remove(Context context, String gameId) {
        File cover = file(context, gameId);
        if (cover.isFile()) cover.delete();
    }

    static File folder(Context context) {
        File directory = new File(context.getApplicationContext().getFilesDir(), "covers");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("The cover folder could not be created");
        }
        return directory;
    }

    private static File file(Context context, String gameId) {
        String safeId = gameId == null ? "unknown"
                : gameId.replaceAll("[^A-Za-z0-9_-]", "_");
        return new File(folder(context), safeId + ".jpg");
    }
}
