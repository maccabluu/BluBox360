package uk.co.blustudio.blubox360;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** Draws full-height retail-style Xbox 360 cases with a green plastic shell. */
final class GameCover3DView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path edge = new Path();
    private final Rect source = new Rect();
    private Bitmap cover;
    private String gameName = "Xbox 360 Game";

    GameCover3DView(Context context) {
        super(context);
        setWillNotDraw(false);
        setElevation(dp(8));
        setRotationY(-7f);
        setCameraDistance(dp(8000));
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

        float depth = Math.max(dp(8), width * 0.075f);
        float top = dp(7);
        float left = depth + dp(6);
        float right = width - dp(8);
        float bottom = height - dp(10);
        float radius = dp(7);
        RectF front = new RectF(left, top, right, bottom);

        paint.setShader(null);
        paint.setColor(Color.argb(80, 0, 0, 0));
        canvas.drawRoundRect(new RectF(left + dp(7), top + dp(8), right + dp(8),
                bottom + dp(8)), radius, radius, paint);

        edge.reset();
        edge.moveTo(left, top);
        edge.lineTo(left - depth, top + depth * 0.72f);
        edge.lineTo(left - depth, bottom - depth * 0.48f);
        edge.lineTo(left, bottom);
        edge.close();
        paint.setShader(new LinearGradient(left - depth, top, left, bottom,
                Color.rgb(71, 188, 67), Color.rgb(16, 82, 30), Shader.TileMode.CLAMP));
        canvas.drawPath(edge, paint);

        edge.reset();
        edge.moveTo(left, top);
        edge.lineTo(left - depth, top + depth * 0.72f);
        edge.lineTo(right - depth * 0.30f, top + depth * 0.72f);
        edge.lineTo(right, top);
        edge.close();
        paint.setShader(new LinearGradient(left, top, right, top,
                Color.rgb(151, 238, 102), Color.rgb(41, 145, 48), Shader.TileMode.CLAMP));
        canvas.drawPath(edge, paint);

        paint.setShader(new LinearGradient(left, top, right, bottom,
                Color.rgb(48, 157, 52), Color.rgb(9, 67, 29), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(front, radius, radius, paint);
        paint.setShader(null);

        float bannerHeight = Math.max(dp(21), height * 0.115f);
        RectF art = new RectF(left + dp(5), top + bannerHeight,
                right - dp(4), bottom - dp(4));
        paint.setColor(Color.rgb(7, 25, 53));
        canvas.drawRect(art, paint);

        boolean fullArtwork = false;
        if (cover != null && cover.getWidth() > 0 && cover.getHeight() > 0) {
            source.set(0, 0, cover.getWidth(), cover.getHeight());
            float aspect = (float) cover.getWidth() / (float) cover.getHeight();
            fullArtwork = aspect <= 0.86f;
            canvas.save();
            canvas.clipRect(art);
            if (fullArtwork) {
                drawCenterCrop(canvas, cover, art, 255);
            } else {
                drawCenterCrop(canvas, cover, art, 100);
                paint.setColor(Color.argb(150, 2, 10, 25));
                canvas.drawRect(art, paint);
                float captionHeight = Math.max(dp(28), art.height() * 0.19f);
                RectF fitted = new RectF(art.left + dp(5), art.top + dp(7),
                        art.right - dp(5), art.bottom - captionHeight - dp(5));
                drawFit(canvas, cover, fitted);
            }
            canvas.restore();
        }

        if (!fullArtwork) {
            float captionHeight = Math.max(dp(28), art.height() * 0.19f);
            RectF caption = new RectF(art.left, art.bottom - captionHeight,
                    art.right, art.bottom);
            paint.setShader(new LinearGradient(caption.left, caption.top,
                    caption.left, caption.bottom, Color.argb(225, 8, 25, 50),
                    Color.argb(250, 2, 9, 24), Shader.TileMode.CLAMP));
            canvas.drawRect(caption, paint);
            paint.setShader(null);
            textPaint.setColor(Color.WHITE);
            textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(Math.max(dp(8), width * 0.075f));
            textPaint.setTextAlign(Paint.Align.CENTER);
            String shortName = fitText(gameName, textPaint, caption.width() - dp(8));
            canvas.drawText(shortName, caption.centerX(),
                    caption.centerY() - (textPaint.ascent() + textPaint.descent()) / 2f,
                    textPaint);
        }

        paint.setColor(Color.rgb(237, 247, 255));
        canvas.drawRoundRect(new RectF(left, top, right, top + bannerHeight),
                radius, radius, paint);
        paint.setColor(Color.rgb(237, 247, 255));
        canvas.drawRect(left, top + bannerHeight - radius, right, top + bannerHeight, paint);
        float orbRadius = bannerHeight * 0.22f;
        float orbX = left + dp(7) + orbRadius;
        float orbY = top + bannerHeight * 0.50f;
        paint.setColor(Color.rgb(83, 173, 60));
        canvas.drawCircle(orbX, orbY, orbRadius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, dp(1)));
        paint.setColor(Color.WHITE);
        canvas.drawLine(orbX - orbRadius * 0.46f, orbY - orbRadius * 0.46f,
                orbX + orbRadius * 0.46f, orbY + orbRadius * 0.46f, paint);
        canvas.drawLine(orbX + orbRadius * 0.46f, orbY - orbRadius * 0.46f,
                orbX - orbRadius * 0.46f, orbY + orbRadius * 0.46f, paint);
        paint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.rgb(61, 126, 54));
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textPaint.setTextSize(Math.max(dp(7), width * 0.061f));
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("XBOX 360", orbX + orbRadius + dp(4),
                top + bannerHeight * 0.69f, textPaint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.rgb(132, 235, 116));
        canvas.drawRoundRect(front, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(Color.argb(90, 255, 255, 255));
        canvas.drawRect(left + dp(2), top + bannerHeight + dp(2),
                left + dp(4), bottom - dp(4), paint);

        canvas.save();
        canvas.rotate(-90f, left - depth * 0.50f, bottom - dp(8));
        textPaint.setColor(Color.argb(220, 240, 255, 240));
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(Math.max(dp(5), depth * 0.52f));
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText("XBOX 360", left - depth * 0.50f,
                bottom - dp(8), textPaint);
        canvas.restore();
    }

    private void drawCenterCrop(Canvas canvas, Bitmap bitmap, RectF target, int alpha) {
        float scale = Math.max(target.width() / bitmap.getWidth(),
                target.height() / bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        RectF destination = new RectF(target.centerX() - width / 2f,
                target.centerY() - height / 2f, target.centerX() + width / 2f,
                target.centerY() + height / 2f);
        paint.setAlpha(alpha);
        canvas.drawBitmap(bitmap, source, destination, paint);
        paint.setAlpha(255);
    }

    private void drawFit(Canvas canvas, Bitmap bitmap, RectF bounds) {
        float scale = Math.min(bounds.width() / bitmap.getWidth(),
                bounds.height() / bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        RectF destination = new RectF(bounds.centerX() - width / 2f,
                bounds.centerY() - height / 2f, bounds.centerX() + width / 2f,
                bounds.centerY() + height / 2f);
        canvas.drawBitmap(bitmap, source, destination, paint);
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
