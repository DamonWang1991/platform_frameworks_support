/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package android.support.v17.leanback.graphics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.v17.leanback.R;
import android.view.View;

/**
 * Helper class for assigning dim color to Paint.
 * The class holds alpha value according to current active level.
 */
public final class ColorOverlayDimmer {

    private final float mActiveLevel;
    private final float mDimmedLevel;

    private final Paint mPaint;

    private int mAlpha;
    private float mAlphaFloat;

    /**
     * Constructor for this ColorOverlayDimmer class.
     *
     * @param dimColor    The color for fully dimmed.  Only r/g/b are used, alpha channel is
     *                    ignored.
     * @param activeLevel The Level of dimming for when the view is in its Active state. Must be a
     *                    float value between 0.0 and 1.0.
     * @param dimmedLevel The Level of dimming for when the view is in its Dimmed state. Must be a
     *                    float value between 0.0 and 1.0.
     */
    public static ColorOverlayDimmer createOverlayColorDimmer(int dimColor, float activeLevel,
            float dimmedLevel) {
        return new ColorOverlayDimmer(dimColor, activeLevel, dimmedLevel);
    }

    /**
     * Constructor to create a default ColorOverlayDimmer.
     */
    public static ColorOverlayDimmer createDefault(Context context) {
        return new ColorOverlayDimmer(
                context.getResources().getColor(R.color.lb_view_dim_mask_color), 0,
                context.getResources().getFraction(R.dimen.lb_view_dimmed_level, 1, 1));
    }

    private ColorOverlayDimmer(int dimColor, float activeLevel, float dimmedLevel) {
        if (activeLevel < 0 || activeLevel > 1) {
            throw new IllegalArgumentException("activeLevel must be between 0 and 1");
        }
        if (dimmedLevel < 0 || dimmedLevel > 1) {
            throw new IllegalArgumentException("dimmedLevel must be between 0 and 1");
        }
        mPaint = new Paint();
        dimColor = Color.rgb(Color.red(dimColor), Color.green(dimColor), Color.blue(dimColor));
        mPaint.setColor(dimColor);
        mActiveLevel = activeLevel;
        mDimmedLevel = dimmedLevel;
        setActiveLevel(1);
    }

    /**
     * Set level of active and change alpha value and paint object.
     * @param level Between 0 for dim and 1 for fully active.
     */
    public void setActiveLevel(float level) {
        mAlphaFloat = (mDimmedLevel + level * (mActiveLevel - mDimmedLevel));
        mAlpha = (int) (255 * mAlphaFloat);
        mPaint.setAlpha(mAlpha);
    }

    /**
     * Returns true if dimmer needs to draw.
     */
    public boolean needsDraw() {
        return mAlpha != 0;
    }

    /**
     * Returns the alpha value for dimmer.
     */
    public int getAlpha() {
        return mAlpha;
    }

    /**
     * Returns the float value between 0~1,  corresponding to alpha between 0~255.
     */
    public float getAlphaFloat() {
        return mAlphaFloat;
    }

    /**
     * Returns the paint object set to current alpha value.
     */
    public Paint getPaint() {
        return mPaint;
    }

    /**
     * Change r,g,b of color according to current dim level.  Keeps alpha of color.
     */
    public int applyToColor(int color) {
        float f = 1 - mAlphaFloat;
        return Color.argb(Color.alpha(color),
                (int)(Color.red(color) * f),
                (int)(Color.green(color) * f),
                (int)(Color.blue(color) * f));
    }

    /**
     * Draw a dim color overlay on top of a child view inside canvas of parent view.
     * @param c   Canvas of parent view.
     * @param v   Child of parent view.
     * @param includePadding  Set to true to draw overlay on padding area of the view.
     */
    public void drawColorOverlay(Canvas c, View v, boolean includePadding) {
        c.save();
        float dx = v.getLeft() + v.getTranslationX();
        float dy = v.getTop() + v.getTranslationY();
        c.translate(dx, dy);
        c.concat(v.getMatrix());
        c.translate(-dx, -dy);
        if (includePadding) {
            c.drawRect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom(), mPaint);
        } else {
            c.drawRect(v.getLeft() + v.getPaddingLeft(),
                    v.getTop() + v.getPaddingTop(),
                    v.getRight() - v.getPaddingRight(),
                    v.getBottom() - v.getPaddingBottom(), mPaint);
        }
        c.restore();
    }
}