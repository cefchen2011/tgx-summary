package com.dsh.tgxsummary;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * Material 3 style color tokens and drawing helpers.
 * Everything is built in code: no XML resources, no theme attributes, no third-party code.
 */
public final class Md3 {

    private Md3() {
    }

    private static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int primary(Context context) {
        return isNight(context) ? 0xFFD0BCFF : 0xFF6750A4;
    }

    public static int onPrimary(Context context) {
        return isNight(context) ? 0xFF381E72 : 0xFFFFFFFF;
    }

    public static int primaryContainer(Context context) {
        return isNight(context) ? 0xFF4F378B : 0xFFEADDFF;
    }

    public static int onPrimaryContainer(Context context) {
        return isNight(context) ? 0xFFEADDFF : 0xFF21005D;
    }

    public static int surface(Context context) {
        return isNight(context) ? 0xFF1C1B1F : 0xFFFFFBFE;
    }

    public static int surfaceContainer(Context context) {
        return isNight(context) ? 0xFF211F26 : 0xFFF3EDF7;
    }

    public static int onSurface(Context context) {
        return isNight(context) ? 0xFFE6E1E5 : 0xFF1C1B1F;
    }

    public static int onSurfaceVariant(Context context) {
        return isNight(context) ? 0xFFCAC4D0 : 0xFF49454F;
    }

    public static int outline(Context context) {
        return isNight(context) ? 0xFF938F99 : 0xFF79747E;
    }

    public static int error(Context context) {
        return isNight(context) ? 0xFFF2B8B5 : 0xFFB3261E;
    }

    public static int dp(Context context, float value) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    public static int sp(Context context, float value) {
        float scaledDensity = context.getResources().getDisplayMetrics().scaledDensity;
        return Math.round(value * scaledDensity);
    }

    public static int withAlpha(int color, int alpha) {
        int a = alpha;
        if (a < 0) {
            a = 0;
        }
        if (a > 255) {
            a = 255;
        }
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static GradientDrawable rounded(Context context, int fillColor, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable roundedStroke(Context context, int fillColor, float radiusDp,
            int strokeColor, float strokeDp) {
        GradientDrawable drawable = rounded(context, fillColor, radiusDp);
        drawable.setStroke(dp(context, strokeDp), strokeColor);
        return drawable;
    }

    public static void card(View v, Context c, int fillColor, float radiusDp) {
        final float radius = dp(c, radiusDp);
        v.setBackground(rounded(c, fillColor, radiusDp));
        v.setElevation(dp(c, 2f));
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        v.setClipToOutline(true);
    }
}
