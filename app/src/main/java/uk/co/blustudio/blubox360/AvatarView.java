package uk.co.blustudio.blubox360;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;

/** Displays a user-selected profile photo. The old avatar creator is no longer used. */
public final class AvatarView extends View {
    // Kept for profile-data compatibility with older alpha builds.
    static final int[] SKIN_COLORS = {0, 1, 2, 3, 4, 5};
    static final int[] HAIR_COLORS = {0, 1, 2, 3, 4, 5};
    static final int[] OUTFIT_COLORS = {0, 1, 2, 3, 4, 5};
    static final int[] BACKGROUND_COLORS = {0, 1, 2, 3, 4, 5};
    static final int EXPRESSION_COUNT = 4;

    private static final int SAVED_PHOTO_SIZE = 768;
    private static final String PHOTO_FILE = "profile_photo.jpg";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap photo;
    private String profileId;
    private String profileName = "Player";

    public AvatarView(Context context) {
        super(context);
        init();
    }

    public AvatarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AvatarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        border.setStyle(Paint.Style.STROKE);
        setContentDescription("BluBox profile photo");
    }

    void setProfile(ProfileStore.Profile profile) {
        if (profile == null) return;
        profileId = profile.id;
        profileName = profile.name == null || profile.name.trim().isEmpty()
                ? "Player" : profile.name.trim();
        loadPhoto();
        setContentDescription(profileName + " profile photo");
        invalidate();
    }

    // Kept so older code paths still compile. Profile appearance now comes from the photo.
    void setAvatar(int skin, int hair, int outfit, int expression, int background) {
        invalidate();
    }

    static File photoFile(Context context, String profileId) {
        String id = profileId == null || profileId.trim().isEmpty()
                ? ProfileStore.DEFAULT_PROFILE_ID : profileId;
        String safeId = id.replaceAll("[^A-Za-z0-9._-]", "_");
        return new File(context.getApplicationContext().getFilesDir(),
                "profiles/" + safeId + "/" + PHOTO_FILE);
    }

    static boolean hasProfilePhoto(Context context, String profileId) {
        File file = photoFile(context, profileId);
        return file.isFile() && file.length() > 0;
    }

    static boolean importProfilePhoto(Context context, String profileId, Uri uri) {
        if (context == null || uri == null || profileId == null || profileId.trim().isEmpty()) {
            return false;
        }
        Bitmap decoded = null;
        Bitmap square = null;
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), uri);
            decoded = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                int width = Math.max(1, info.getSize().getWidth());
                int height = Math.max(1, info.getSize().getHeight());
                int longest = Math.max(width, height);
                if (longest > 1600) {
                    float scale = 1600f / longest;
                    decoder.setTargetSize(Math.max(1, Math.round(width * scale)),
                            Math.max(1, Math.round(height * scale)));
                }
            });
            square = cropSquare(decoded, SAVED_PHOTO_SIZE);
            File destination = photoFile(context, profileId);
            File parent = destination.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return false;
            }
            File temporary = new File(parent, PHOTO_FILE + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                if (!square.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                    temporary.delete();
                    return false;
                }
                out.flush();
            }
            if (destination.exists() && !destination.delete()) {
                temporary.delete();
                return false;
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete();
                return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (square != null && square != decoded && !square.isRecycled()) square.recycle();
            if (decoded != null && !decoded.isRecycled()) decoded.recycle();
        }
    }

    static boolean removeProfilePhoto(Context context, String profileId) {
        File file = photoFile(context, profileId);
        return !file.exists() || file.delete();
    }

    static Bitmap renderProfile(Context context, ProfileStore.Profile profile, int size) {
        int safeSize = Math.max(32, size);
        Bitmap source = decodePhoto(photoFile(context, profile == null ? null : profile.id),
                Math.max(safeSize, 256));
        if (source != null) {
            Bitmap square = cropSquare(source, safeSize);
            if (square != source && !source.isRecycled()) source.recycle();
            return square;
        }

        Bitmap fallback = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(fallback);
        drawFallback(canvas, new RectF(0, 0, safeSize, safeSize),
                profile == null ? "P" : profile.name);
        return fallback;
    }

    private void loadPhoto() {
        if (photo != null && !photo.isRecycled()) photo.recycle();
        photo = decodePhoto(photoFile(getContext(), profileId), 768);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float size = Math.min(width, height);
        float left = (width - size) / 2f;
        float top = (height - size) / 2f;
        RectF area = new RectF(left + size * 0.02f, top + size * 0.02f,
                left + size * 0.98f, top + size * 0.98f);
        float radius = size * 0.18f;

        if (photo == null || photo.isRecycled()) {
            drawFallback(canvas, area, profileName);
        } else {
            Path clip = new Path();
            clip.addRoundRect(area, radius, radius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(clip);
            Rect source = centeredSquare(photo.getWidth(), photo.getHeight());
            canvas.drawBitmap(photo, source, area, paint);
            canvas.restoreToCount(save);
        }

        border.setColor(Color.argb(150, 255, 255, 255));
        border.setStrokeWidth(Math.max(1f, size * 0.012f));
        canvas.drawRoundRect(area, radius, radius, border);
    }

    private static void drawFallback(Canvas canvas, RectF area, String name) {
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setShader(new LinearGradient(area.left, area.top, area.right, area.bottom,
                Color.rgb(20, 119, 235), Color.rgb(8, 54, 130), Shader.TileMode.CLAMP));
        float radius = Math.min(area.width(), area.height()) * 0.18f;
        canvas.drawRoundRect(area, radius, radius, fill);
        fill.setShader(null);
        fill.setColor(Color.WHITE);
        fill.setTextAlign(Paint.Align.CENTER);
        fill.setFakeBoldText(true);
        fill.setTextSize(Math.min(area.width(), area.height()) * 0.42f);
        Paint.FontMetrics metrics = fill.getFontMetrics();
        float baseline = area.centerY() - (metrics.ascent + metrics.descent) / 2f;
        String initial = "P";
        if (name != null && !name.trim().isEmpty()) {
            initial = name.trim().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        }
        canvas.drawText(initial, area.centerX(), baseline, fill);
    }

    private static Bitmap decodePhoto(File file, int requestedSize) {
        if (file == null || !file.isFile() || file.length() <= 0) return null;
        try {
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            int shortest = Math.min(bounds.outWidth, bounds.outHeight);
            while (shortest / (sample * 2) >= requestedSize) sample *= 2;
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Bitmap cropSquare(Bitmap source, int targetSize) {
        if (source == null) return null;
        int width = source.getWidth();
        int height = source.getHeight();
        int edge = Math.min(width, height);
        int left = Math.max(0, (width - edge) / 2);
        int top = Math.max(0, (height - edge) / 2);
        Bitmap cropped = Bitmap.createBitmap(source, left, top, edge, edge);
        if (edge == targetSize) return cropped;
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true);
        if (scaled != cropped && cropped != source && !cropped.isRecycled()) cropped.recycle();
        return scaled;
    }

    private static Rect centeredSquare(int width, int height) {
        int edge = Math.min(width, height);
        int left = Math.max(0, (width - edge) / 2);
        int top = Math.max(0, (height - edge) / 2);
        return new Rect(left, top, left + edge, top + edge);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (photo != null && !photo.isRecycled()) {
            photo.recycle();
            photo = null;
        }
        super.onDetachedFromWindow();
    }
}
