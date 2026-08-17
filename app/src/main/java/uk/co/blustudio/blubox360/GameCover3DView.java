package uk.co.blustudio.blubox360;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/**
 * Flat front-cover renderer used by every BluBox library layout.
 *
 * The class name is kept for source compatibility with the existing library code,
 * but v0.16.2 deliberately removes the old 3D Xbox 360 plastic case treatment.
 */
final class GameCover3DView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect source = new Rect();
    private Bitmap cover;
    private String gameName = "Xbox 360 Game";

    GameCover3DView(Context context) {
        super(context);
        setWillNotDraw(false);
        setElevation(dp(3));
        setRotationX(0f);
        setRotationY(0f);
    }

    void setCover(Bitmap value, String title) {
        cover = value;
        gameName = title == null || title.trim().isEmpty() ? "Xbox 360 Game" : title.trim();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 1 || height <= 1) return;

        // Small inset keeps neighbouring covers visually separate while letting the
        // artwork use far more of the tile than the old plastic-case renderer.
        float inset = dp(3);
        float radius = dp(6);
        RectF art = new RectF(inset, inset, width - inset, height - inset);

        // Soft shadow behind the flat cover.
        paint.setShader(null);
        paint.setColor(Color.argb(78, 0, 0, 0));
        canvas.drawRoundRect(new RectF(
                art.left + dp(3), art.top + dp(4),
                art.right + dp(3), art.bottom + dp(4)),
                radius, radius, paint);

        // Dark backing is visible only while artwork is unavailable.
        paint.setColor(Color.rgb(7, 21, 43));
        canvas.drawRoundRect(art, radius, radius, paint);

        if (cover != null && cover.getWidth() > 0 && cover.getHeight() > 0) {
            source.set(0, 0, cover.getWidth(), cover.getHeight());
            canvas.save();
            canvas.clipPath(roundedRectPath(art, radius));
            drawCenterCrop(canvas, cover, art);
            canvas.restore();
        } else {
            drawPlaceholder(canvas, art);
        }

        // Thin neutral edge. No green plastic shell, top banner, spine, or 3D tilt.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, dp(1)));
        paint.setColor(Color.argb(95, 255, 255, 255));
        canvas.drawRoundRect(art, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private android.graphics.Path roundedRectPath(RectF rect, float radius) {
        android.graphics.Path path = new android.graphics.Path();
        path.addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW);
        return path;
    }

    private void drawCenterCrop(Canvas canvas, Bitmap bitmap, RectF target) {
        float scale = Math.max(target.width() / bitmap.getWidth(),
                target.height() / bitmap.getHeight());
        float scaledWidth = bitmap.getWidth() * scale;
        float scaledHeight = bitmap.getHeight() * scale;
        RectF destination = new RectF(
                target.centerX() - scaledWidth / 2f,
                target.centerY() - scaledHeight / 2f,
                target.centerX() + scaledWidth / 2f,
                target.centerY() + scaledHeight / 2f);
        paint.setAlpha(255);
        canvas.drawBitmap(bitmap, source, destination, paint);
    }

    private void drawPlaceholder(Canvas canvas, RectF bounds) {
        textPaint.setColor(Color.WHITE);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(Math.max(dp(9), bounds.width() * 0.09f));
        String shortName = fitText(gameName, textPaint, bounds.width() - dp(18));
        canvas.drawText(shortName, bounds.centerX(),
                bounds.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f,
                textPaint);
    }

    private static String fitText(String value, Paint paint, float maxWidth) {
        if (paint.measureText(value) <= maxWidth) return value;
        String suffix = "…";
        int count = paint.breakText(value, true,
                Math.max(0, maxWidth - paint.measureText(suffix)), null);
        return value.substring(0, Math.max(0, count)).trim() + suffix;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
