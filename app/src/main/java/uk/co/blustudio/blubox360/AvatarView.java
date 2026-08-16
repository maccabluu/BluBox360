package uk.co.blustudio.blubox360;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Draws an original, full-body console-style avatar without external image assets. */
public final class AvatarView extends View {
    static final int[] SKIN_COLORS = {
            Color.rgb(255, 224, 189), Color.rgb(241, 194, 125),
            Color.rgb(224, 172, 105), Color.rgb(198, 134, 66),
            Color.rgb(141, 85, 36), Color.rgb(88, 51, 28)
    };
    static final int[] HAIR_COLORS = {
            Color.rgb(33, 24, 22), Color.rgb(90, 54, 32),
            Color.rgb(218, 169, 73), Color.rgb(202, 76, 69),
            Color.rgb(40, 109, 212), Color.rgb(145, 72, 190)
    };
    static final int[] OUTFIT_COLORS = {
            Color.rgb(22, 136, 255), Color.rgb(52, 216, 255),
            Color.rgb(69, 217, 154), Color.rgb(255, 102, 120),
            Color.rgb(255, 190, 65), Color.rgb(134, 91, 220)
    };
    static final int[] BACKGROUND_COLORS = {
            Color.rgb(7, 87, 212), Color.rgb(5, 151, 187),
            Color.rgb(28, 116, 89), Color.rgb(119, 61, 177),
            Color.rgb(181, 68, 91), Color.rgb(179, 118, 31)
    };
    static final int EXPRESSION_COUNT = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int skin;
    private int hair;
    private int outfit;
    private int expression;
    private int background;

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
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("Full-body BluBox profile avatar");
    }

    void setProfile(ProfileStore.Profile profile) {
        if (profile == null) return;
        setAvatar(profile.skin, profile.hair, profile.outfit,
                profile.expression, profile.background);
    }

    void setAvatar(int skin, int hair, int outfit, int expression, int background) {
        this.skin = wrap(skin, SKIN_COLORS.length);
        this.hair = wrap(hair, HAIR_COLORS.length);
        this.outfit = wrap(outfit, OUTFIT_COLORS.length);
        this.expression = wrap(expression, EXPRESSION_COUNT);
        this.background = wrap(background, BACKGROUND_COLORS.length);
        invalidate();
    }

    static Bitmap renderProfile(Context context, ProfileStore.Profile profile, int size) {
        AvatarView view = new AvatarView(context);
        view.setProfile(profile);
        int exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
        view.measure(exact, exact);
        view.layout(0, 0, size, size);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float size = Math.min(width, height);
        float left = (width - size) / 2f;
        float top = (height - size) / 2f;

        drawCard(canvas, left, top, size);

        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(65, 12, 26, 54));
        canvas.drawOval(new RectF(left + size * 0.27f, top + size * 0.875f,
                left + size * 0.73f, top + size * 0.94f), paint);

        drawLegs(canvas, left, top, size);
        drawArms(canvas, left, top, size);
        drawTorso(canvas, left, top, size);
        drawNeckAndHead(canvas, left, top, size);
        drawHair(canvas, left, top, size);
        drawFace(canvas, left, top, size);

        stroke.setColor(Color.argb(125, 255, 255, 255));
        stroke.setStrokeWidth(Math.max(1f, size * 0.012f));
        canvas.drawRoundRect(new RectF(left + size * 0.025f, top + size * 0.025f,
                left + size * 0.975f, top + size * 0.975f),
                size * 0.17f, size * 0.17f, stroke);
    }

    private void drawCard(Canvas canvas, float left, float top, float size) {
        int accent = BACKGROUND_COLORS[background];
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(left, top, left + size, top + size,
                lighten(accent, 0.87f), lighten(accent, 0.53f), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(left + size * 0.02f, top + size * 0.02f,
                left + size * 0.98f, top + size * 0.98f),
                size * 0.18f, size * 0.18f, paint);
        paint.setShader(null);

        paint.setColor(Color.argb(58, Color.red(accent), Color.green(accent), Color.blue(accent)));
        canvas.drawCircle(left + size * 0.13f, top + size * 0.18f, size * 0.12f, paint);
        canvas.drawCircle(left + size * 0.88f, top + size * 0.37f, size * 0.19f, paint);
        paint.setColor(Color.argb(42, 255, 255, 255));
        canvas.drawCircle(left + size * 0.76f, top + size * 0.11f, size * 0.07f, paint);
    }

    private void drawLegs(Canvas canvas, float left, float top, float size) {
        int trousers = darken(OUTFIT_COLORS[outfit], outfit == 4 ? 0.64f : 0.48f);
        float hipY = top + size * 0.635f;
        float ankleY = top + size * 0.855f;
        float spread = expression == 1 ? size * 0.052f : size * 0.025f;

        if (outfit % 6 == 2) {
            Path skirt = new Path();
            skirt.moveTo(left + size * 0.37f, top + size * 0.56f);
            skirt.lineTo(left + size * 0.30f, top + size * 0.72f);
            skirt.quadTo(left + size * 0.50f, top + size * 0.77f,
                    left + size * 0.70f, top + size * 0.72f);
            skirt.lineTo(left + size * 0.63f, top + size * 0.56f);
            skirt.close();
            paint.setColor(darken(OUTFIT_COLORS[outfit], 0.18f));
            canvas.drawPath(skirt, paint);
            hipY = top + size * 0.71f;
            trousers = darken(SKIN_COLORS[skin], 0.03f);
        }

        drawSegment(canvas, left + size * 0.455f, hipY,
                left + size * 0.43f - spread, ankleY, size * 0.095f, trousers);
        drawSegment(canvas, left + size * 0.545f, hipY,
                left + size * 0.57f + spread, ankleY, size * 0.095f, trousers);

        int shoe = outfit % 3 == 1 ? Color.rgb(242, 246, 252) : Color.rgb(34, 42, 57);
        paint.setColor(shoe);
        canvas.drawRoundRect(new RectF(left + size * 0.305f - spread, top + size * 0.825f,
                left + size * 0.47f - spread, top + size * 0.895f),
                size * 0.03f, size * 0.03f, paint);
        canvas.drawRoundRect(new RectF(left + size * 0.53f + spread, top + size * 0.825f,
                left + size * 0.695f + spread, top + size * 0.895f),
                size * 0.03f, size * 0.03f, paint);
        paint.setColor(lighten(shoe, 0.35f));
        canvas.drawRoundRect(new RectF(left + size * 0.31f - spread, top + size * 0.868f,
                left + size * 0.475f - spread, top + size * 0.895f),
                size * 0.014f, size * 0.014f, paint);
        canvas.drawRoundRect(new RectF(left + size * 0.525f + spread, top + size * 0.868f,
                left + size * 0.69f + spread, top + size * 0.895f),
                size * 0.014f, size * 0.014f, paint);
    }

    private void drawArms(Canvas canvas, float left, float top, float size) {
        int sleeve = darken(OUTFIT_COLORS[outfit], 0.08f);
        int skinColor = SKIN_COLORS[skin];
        float shoulderY = top + size * 0.46f;

        if (expression == 1) {
            drawSegment(canvas, left + size * 0.35f, shoulderY,
                    left + size * 0.24f, top + size * 0.59f, size * 0.092f, sleeve);
            drawSegment(canvas, left + size * 0.24f, top + size * 0.59f,
                    left + size * 0.29f, top + size * 0.73f, size * 0.065f, skinColor);
            drawHand(canvas, left + size * 0.30f, top + size * 0.75f, size, skinColor);

            drawSegment(canvas, left + size * 0.65f, shoulderY,
                    left + size * 0.75f, top + size * 0.32f, size * 0.092f, sleeve);
            drawSegment(canvas, left + size * 0.75f, top + size * 0.32f,
                    left + size * 0.72f, top + size * 0.16f, size * 0.064f, skinColor);
            drawHand(canvas, left + size * 0.72f, top + size * 0.13f, size, skinColor);
        } else if (expression == 2) {
            drawSegment(canvas, left + size * 0.35f, shoulderY,
                    left + size * 0.24f, top + size * 0.58f, size * 0.092f, sleeve);
            drawSegment(canvas, left + size * 0.24f, top + size * 0.58f,
                    left + size * 0.36f, top + size * 0.64f, size * 0.065f, skinColor);
            drawHand(canvas, left + size * 0.37f, top + size * 0.64f, size, skinColor);
            drawSegment(canvas, left + size * 0.65f, shoulderY,
                    left + size * 0.76f, top + size * 0.58f, size * 0.092f, sleeve);
            drawSegment(canvas, left + size * 0.76f, top + size * 0.58f,
                    left + size * 0.64f, top + size * 0.64f, size * 0.065f, skinColor);
            drawHand(canvas, left + size * 0.63f, top + size * 0.64f, size, skinColor);
        } else {
            float leftHandX = expression == 3 ? 0.23f : 0.28f;
            float rightHandX = expression == 3 ? 0.77f : 0.72f;
            drawSegment(canvas, left + size * 0.35f, shoulderY,
                    left + size * 0.27f, top + size * 0.61f, size * 0.092f, sleeve);
            drawSegment(canvas, left + size * 0.27f, top + size * 0.61f,
                    left + size * leftHandX, top + size * 0.73f, size * 0.064f, skinColor);
            drawHand(canvas, left + size * leftHandX, top + size * 0.75f, size, skinColor);
            drawSegment(canvas, left + size * 0.65f, shoulderY,
                    left + size * 0.73f, top + size * 0.61f, size * 0.092f, sleeve);
            drawSegment(canvas, left + size * 0.73f, top + size * 0.61f,
                    left + size * rightHandX, top + size * 0.73f, size * 0.064f, skinColor);
            drawHand(canvas, left + size * rightHandX, top + size * 0.75f, size, skinColor);
        }
    }

    private void drawTorso(Canvas canvas, float left, float top, float size) {
        int outfitColor = OUTFIT_COLORS[outfit];
        Path torso = new Path();
        torso.moveTo(left + size * 0.36f, top + size * 0.41f);
        torso.quadTo(left + size * 0.50f, top + size * 0.37f,
                left + size * 0.64f, top + size * 0.41f);
        torso.lineTo(left + size * 0.65f, top + size * 0.65f);
        torso.quadTo(left + size * 0.50f, top + size * 0.69f,
                left + size * 0.35f, top + size * 0.65f);
        torso.close();
        paint.setColor(outfitColor);
        paint.setShadowLayer(size * 0.025f, 0, size * 0.014f, Color.argb(70, 0, 0, 0));
        canvas.drawPath(torso, paint);
        paint.clearShadowLayer();

        int style = outfit % 6;
        if (style == 0) {
            paint.setColor(Color.argb(215, 255, 255, 255));
            canvas.drawCircle(left + size * 0.50f, top + size * 0.52f, size * 0.048f, paint);
            paint.setColor(darken(outfitColor, 0.26f));
            canvas.drawRoundRect(new RectF(left + size * 0.485f, top + size * 0.48f,
                    left + size * 0.515f, top + size * 0.56f),
                    size * 0.012f, size * 0.012f, paint);
        } else if (style == 1) {
            stroke.setColor(lighten(outfitColor, 0.55f));
            stroke.setStrokeWidth(Math.max(1f, size * 0.022f));
            canvas.drawLine(left + size * 0.50f, top + size * 0.42f,
                    left + size * 0.50f, top + size * 0.65f, stroke);
            paint.setColor(Color.rgb(238, 244, 252));
            canvas.drawRoundRect(new RectF(left + size * 0.40f, top + size * 0.445f,
                    left + size * 0.60f, top + size * 0.49f),
                    size * 0.02f, size * 0.02f, paint);
        } else if (style == 3) {
            paint.setColor(lighten(outfitColor, 0.28f));
            canvas.drawRoundRect(new RectF(left + size * 0.37f, top + size * 0.55f,
                    left + size * 0.63f, top + size * 0.61f),
                    size * 0.02f, size * 0.02f, paint);
        } else if (style == 4) {
            paint.setColor(Color.rgb(36, 44, 61));
            Path lapel = new Path();
            lapel.moveTo(left + size * 0.42f, top + size * 0.42f);
            lapel.lineTo(left + size * 0.50f, top + size * 0.53f);
            lapel.lineTo(left + size * 0.58f, top + size * 0.42f);
            lapel.lineTo(left + size * 0.54f, top + size * 0.58f);
            lapel.lineTo(left + size * 0.46f, top + size * 0.58f);
            lapel.close();
            canvas.drawPath(lapel, paint);
        } else if (style == 5) {
            stroke.setColor(lighten(outfitColor, 0.50f));
            stroke.setStrokeWidth(Math.max(1f, size * 0.020f));
            for (int i = 0; i < 3; i++) {
                float y = top + size * (0.49f + i * 0.052f);
                canvas.drawLine(left + size * 0.39f, y, left + size * 0.61f, y, stroke);
            }
        }
    }

    private void drawNeckAndHead(Canvas canvas, float left, float top, float size) {
        int skinColor = SKIN_COLORS[skin];
        paint.setColor(darken(skinColor, 0.08f));
        canvas.drawRoundRect(new RectF(left + size * 0.455f, top + size * 0.34f,
                left + size * 0.545f, top + size * 0.45f),
                size * 0.035f, size * 0.035f, paint);

        paint.setColor(skinColor);
        canvas.drawCircle(left + size * 0.365f, top + size * 0.275f, size * 0.052f, paint);
        canvas.drawCircle(left + size * 0.635f, top + size * 0.275f, size * 0.052f, paint);
        paint.setShadowLayer(size * 0.025f, 0, size * 0.014f, Color.argb(65, 0, 0, 0));
        canvas.drawOval(new RectF(left + size * 0.34f, top + size * 0.105f,
                left + size * 0.66f, top + size * 0.405f), paint);
        paint.clearShadowLayer();

        paint.setColor(lighten(skinColor, 0.19f));
        canvas.drawOval(new RectF(left + size * 0.40f, top + size * 0.15f,
                left + size * 0.47f, top + size * 0.23f), paint);
    }

    private void drawHair(Canvas canvas, float left, float top, float size) {
        paint.setColor(HAIR_COLORS[hair]);
        int style = hair % 6;
        if (style == 0) {
            canvas.drawArc(new RectF(left + size * 0.33f, top + size * 0.07f,
                    left + size * 0.67f, top + size * 0.30f), 180, 180, true, paint);
            canvas.drawRoundRect(new RectF(left + size * 0.335f, top + size * 0.17f,
                    left + size * 0.39f, top + size * 0.285f),
                    size * 0.025f, size * 0.025f, paint);
        } else if (style == 1) {
            Path spikes = new Path();
            spikes.moveTo(left + size * 0.33f, top + size * 0.23f);
            spikes.lineTo(left + size * 0.35f, top + size * 0.09f);
            spikes.lineTo(left + size * 0.42f, top + size * 0.14f);
            spikes.lineTo(left + size * 0.48f, top + size * 0.055f);
            spikes.lineTo(left + size * 0.54f, top + size * 0.14f);
            spikes.lineTo(left + size * 0.64f, top + size * 0.08f);
            spikes.lineTo(left + size * 0.67f, top + size * 0.24f);
            spikes.close();
            canvas.drawPath(spikes, paint);
        } else if (style == 2) {
            canvas.drawArc(new RectF(left + size * 0.35f, top + size * 0.095f,
                    left + size * 0.65f, top + size * 0.255f), 180, 180, true, paint);
        } else if (style == 3) {
            canvas.drawOval(new RectF(left + size * 0.30f, top + size * 0.09f,
                    left + size * 0.70f, top + size * 0.30f), paint);
            canvas.drawRoundRect(new RectF(left + size * 0.31f, top + size * 0.18f,
                    left + size * 0.38f, top + size * 0.39f),
                    size * 0.03f, size * 0.03f, paint);
            canvas.drawRoundRect(new RectF(left + size * 0.62f, top + size * 0.18f,
                    left + size * 0.69f, top + size * 0.39f),
                    size * 0.03f, size * 0.03f, paint);
        } else if (style == 4) {
            for (int i = 0; i < 7; i++) {
                float angle = (float) (Math.PI * 2.0 * i / 7.0);
                canvas.drawCircle(left + size * (0.50f + 0.145f * (float) Math.cos(angle)),
                        top + size * (0.18f + 0.085f * (float) Math.sin(angle)),
                        size * 0.067f, paint);
            }
        } else {
            canvas.drawArc(new RectF(left + size * 0.33f, top + size * 0.08f,
                    left + size * 0.67f, top + size * 0.30f), 180, 180, true, paint);
            canvas.drawOval(new RectF(left + size * 0.60f, top + size * 0.075f,
                    left + size * 0.73f, top + size * 0.20f), paint);
            canvas.drawOval(new RectF(left + size * 0.68f, top + size * 0.10f,
                    left + size * 0.78f, top + size * 0.21f), paint);
        }
    }

    private void drawFace(Canvas canvas, float left, float top, float size) {
        int ink = Color.rgb(31, 39, 55);
        paint.setColor(ink);
        stroke.setColor(ink);
        stroke.setStrokeWidth(Math.max(1f, size * 0.016f));
        float eyeY = top + size * 0.265f;
        float leftEye = left + size * 0.445f;
        float rightEye = left + size * 0.555f;

        if (expression == 2) {
            canvas.drawArc(new RectF(left + size * 0.415f, eyeY - size * 0.016f,
                    left + size * 0.475f, eyeY + size * 0.025f), 200, 140, false, stroke);
            canvas.drawArc(new RectF(left + size * 0.525f, eyeY - size * 0.016f,
                    left + size * 0.585f, eyeY + size * 0.025f), 200, 140, false, stroke);
        } else {
            canvas.drawOval(new RectF(leftEye - size * 0.014f, eyeY - size * 0.019f,
                    leftEye + size * 0.014f, eyeY + size * 0.019f), paint);
            if (expression == 3) {
                canvas.drawLine(rightEye - size * 0.022f, eyeY,
                        rightEye + size * 0.022f, eyeY, stroke);
            } else {
                canvas.drawOval(new RectF(rightEye - size * 0.014f, eyeY - size * 0.019f,
                        rightEye + size * 0.014f, eyeY + size * 0.019f), paint);
            }
        }

        paint.setColor(darken(SKIN_COLORS[skin], 0.12f));
        canvas.drawOval(new RectF(left + size * 0.492f, top + size * 0.282f,
                left + size * 0.515f, top + size * 0.318f), paint);

        RectF mouth = new RectF(left + size * 0.445f, top + size * 0.315f,
                left + size * 0.555f, top + size * 0.37f);
        if (expression == 1) {
            paint.setColor(Color.rgb(53, 30, 40));
            canvas.drawOval(mouth, paint);
            paint.setColor(Color.WHITE);
            canvas.drawRoundRect(new RectF(left + size * 0.462f, top + size * 0.326f,
                    left + size * 0.538f, top + size * 0.343f),
                    size * 0.006f, size * 0.006f, paint);
        } else if (expression == 2) {
            canvas.drawArc(mouth, 15, 150, false, stroke);
        } else {
            canvas.drawArc(mouth, 8, 164, false, stroke);
        }
    }

    private void drawSegment(Canvas canvas, float x1, float y1, float x2, float y2,
                             float width, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(width);
        canvas.drawLine(x1, y1, x2, y2, stroke);
    }

    private void drawHand(Canvas canvas, float x, float y, float size, int color) {
        paint.setColor(color);
        canvas.drawCircle(x, y, size * 0.042f, paint);
    }

    private static int wrap(int value, int count) {
        int result = value % count;
        return result < 0 ? result + count : result;
    }

    private static int lighten(int color, float amount) {
        return Color.rgb(
                Math.min(255, Math.round(Color.red(color) + (255 - Color.red(color)) * amount)),
                Math.min(255, Math.round(Color.green(color) + (255 - Color.green(color)) * amount)),
                Math.min(255, Math.round(Color.blue(color) + (255 - Color.blue(color)) * amount)));
    }

    private static int darken(int color, float amount) {
        return Color.rgb(
                Math.max(0, Math.round(Color.red(color) * (1f - amount))),
                Math.max(0, Math.round(Color.green(color) * (1f - amount))),
                Math.max(0, Math.round(Color.blue(color) * (1f - amount))));
    }
}
