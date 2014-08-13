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
package android.support.v17.leanback.app;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.RecyclerView;
import android.support.v17.leanback.R;
import android.support.v17.leanback.animation.LogAccelerateInterpolator;
import android.support.v17.leanback.animation.LogDecelerateInterpolator;
import android.support.v17.leanback.widget.ItemBridgeAdapter;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.ObjectAdapter.DataObserver;
import android.support.v17.leanback.widget.VerticalGridView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;


/**
 * A fragment for displaying playback controls and related content.
 * The {@link android.support.v17.leanback.widget.PlaybackControlsRow} is expected to be
 * at position 0 in the adapter.
 */
public class PlaybackOverlayFragment extends DetailsFragment {

    /**
     * No background.
     */
    public static final int BG_NONE = 0;

    /**
     * A dark translucent background.
     */
    public static final int BG_DARK = 1;

    /**
     * A light translucent background.
     */
    public static final int BG_LIGHT = 2;

    public static class OnFadeCompleteListener {
        public void onFadeInComplete() {
        }
        public void onFadeOutComplete() {
        }
    }

    private static final String TAG = "PlaybackOverlayFragment";
    private static final boolean DEBUG = false;
    private static final int ANIMATION_MULTIPLIER = 1;

    private static int START_FADE_OUT = 1;

    // Fading status
    private static final int IDLE = 0;
    private static final int IN = 1;
    private static final int OUT = 2;

    private int mAlignPosition;
    private View mRootView;
    private int mBackgroundType = BG_DARK;
    private int mBgDarkColor;
    private int mBgLightColor;
    private int mShowTimeMs;
    private int mFadeTranslateY;
    private OnFadeCompleteListener mFadeCompleteListener;
    private boolean mFadingEnabled = true;
    private int mFadingStatus = IDLE;
    private int mBgAlpha;
    private ValueAnimator mBgFadeInAnimator, mBgFadeOutAnimator;
    private ValueAnimator mControlRowFadeInAnimator, mControlRowFadeOutAnimator;
    private ValueAnimator mOtherRowFadeInAnimator, mOtherRowFadeOutAnimator;
    private boolean mTranslateAnimationEnabled;
    private RecyclerView.ItemAnimator mItemAnimator;

    private final Animator.AnimatorListener mFadeListener =
            new Animator.AnimatorListener() {
        @Override
        public void onAnimationStart(Animator animation) {
            enableVerticalGridAnimations(false);
        }
        @Override
        public void onAnimationRepeat(Animator animation) {
        }
        @Override
        public void onAnimationCancel(Animator animation) {
        }
        @Override
        public void onAnimationEnd(Animator animation) {
            if (DEBUG) Log.v(TAG, "onAnimationEnd " + mBgAlpha);
            if (mBgAlpha > 0) {
                enableVerticalGridAnimations(true);
                startFadeTimer();
                if (mFadeCompleteListener != null) {
                    mFadeCompleteListener.onFadeInComplete();
                }
            } else {
                if (getVerticalGridView() != null) {
                    // Reset focus to the controls row
                    getVerticalGridView().setSelectedPosition(0);
                }
                if (mFadeCompleteListener != null) {
                    mFadeCompleteListener.onFadeOutComplete();
                }
            }
            mFadingStatus = IDLE;
        }
    };

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            if (message.what == START_FADE_OUT && mFadingEnabled) {
                fade(false);
            }
        }
    };

    private final VerticalGridView.OnTouchInterceptListener mOnTouchInterceptListener =
            new VerticalGridView.OnTouchInterceptListener() {
        public boolean onInterceptTouchEvent(MotionEvent event) {
            return onInterceptInputEvent();
        }
    };

    private final VerticalGridView.OnMotionInterceptListener mOnMotionInterceptListener =
            new VerticalGridView.OnMotionInterceptListener() {
        public boolean onInterceptMotionEvent(MotionEvent event) {
            return onInterceptInputEvent();
        }
    };

    private final VerticalGridView.OnKeyInterceptListener mOnKeyInterceptListener =
            new VerticalGridView.OnKeyInterceptListener() {
        public boolean onInterceptKeyEvent(KeyEvent event) {
            return onInterceptInputEvent();
        }
    };

    private void setBgAlpha(int alpha) {
        mBgAlpha = alpha;
        if (mRootView != null) {
            mRootView.getBackground().setAlpha(alpha);
        }
    }

    private void enableVerticalGridAnimations(boolean enable) {
        if (getVerticalGridView() == null) {
            return;
        }
        if (enable && mItemAnimator != null) {
            getVerticalGridView().setItemAnimator(mItemAnimator);
        } else if (!enable) {
            mItemAnimator = getVerticalGridView().getItemAnimator();
            getVerticalGridView().setItemAnimator(null);
        }
    }

    /**
     * Enables or disables view fading.  If enabled,
     * the view will be faded in when the fragment starts,
     * and will fade out after a time period.  The timeout
     * period is reset each time {@link #tickle} is called.
     *
     */
    public void setFadingEnabled(boolean enabled) {
        if (DEBUG) Log.v(TAG, "setFadingEnabled " + enabled);
        if (enabled != mFadingEnabled) {
            mFadingEnabled = enabled;
            if (isResumed()) {
                if (mFadingEnabled) {
                    if (mFadingStatus == IDLE && !mHandler.hasMessages(START_FADE_OUT)) {
                        startFadeTimer();
                    }
                } else {
                    // Ensure fully opaque
                    mHandler.removeMessages(START_FADE_OUT);
                    fade(true);
                }
            }
        }
    }

    /**
     * Returns true if view fading is enabled.
     */
    public boolean isFadingEnabled() {
        return mFadingEnabled;
    }

    /**
     * Sets the listener to be called when fade in or out has completed.
     */
    public void setFadeCompleteListener(OnFadeCompleteListener listener) {
        mFadeCompleteListener = listener;
    }

    /**
     * Returns the listener to be called when fade in or out has completed.
     */
    public OnFadeCompleteListener getFadeCompleteListener() {
        return mFadeCompleteListener;
    }

    /**
     * Tickles the playback controls.  Fades in the view if it was faded out,
     * otherwise resets the fade out timer.  Tickling on input events is handled
     * by the fragment.
     */
    public void tickle() {
        if (DEBUG) Log.v(TAG, "tickle enabled " + mFadingEnabled + " isResumed " + isResumed());
        if (!mFadingEnabled || !isResumed()) {
            return;
        }
        if (mHandler.hasMessages(START_FADE_OUT)) {
            // Restart the timer
            startFadeTimer();
        } else {
            fade(true);
        }
    }

    private boolean onInterceptInputEvent() {
        if (DEBUG) Log.v(TAG, "onInterceptInputEvent status " + mFadingStatus);
        boolean consumeEvent = (mFadingStatus == IDLE && mBgAlpha == 0);
        tickle();
        return consumeEvent;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mFadingEnabled) {
            setBgAlpha(0);
            fade(true);
        }
        getVerticalGridView().setOnTouchInterceptListener(mOnTouchInterceptListener);
        getVerticalGridView().setOnMotionInterceptListener(mOnMotionInterceptListener);
        getVerticalGridView().setOnKeyInterceptListener(mOnKeyInterceptListener);
    }

    private void startFadeTimer() {
        if (mHandler != null) {
            mHandler.removeMessages(START_FADE_OUT);
            mHandler.sendEmptyMessageDelayed(START_FADE_OUT, mShowTimeMs);
        }
    }

    private static ValueAnimator loadAnimator(Context context, int resId) {
        ValueAnimator animator = (ValueAnimator) AnimatorInflater.loadAnimator(context, resId);
        animator.setDuration(animator.getDuration() * ANIMATION_MULTIPLIER);
        return animator;
    }

    private void loadBgAnimator() {
        AnimatorUpdateListener listener = new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator arg0) {
                setBgAlpha((Integer) arg0.getAnimatedValue());
            }
        };

        mBgFadeInAnimator = loadAnimator(getActivity(), R.animator.lb_playback_bg_fade_in);
        mBgFadeInAnimator.addUpdateListener(listener);
        mBgFadeInAnimator.addListener(mFadeListener);

        mBgFadeOutAnimator = loadAnimator(getActivity(), R.animator.lb_playback_bg_fade_out);
        mBgFadeOutAnimator.addUpdateListener(listener);
        mBgFadeOutAnimator.addListener(mFadeListener);
    }

    private TimeInterpolator mLogDecelerateInterpolator = new LogDecelerateInterpolator(100,0);
    private TimeInterpolator mLogAccelerateInterpolator = new LogAccelerateInterpolator(100,0);

    private void loadControlRowAnimator() {
        AnimatorUpdateListener listener = new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator arg0) {
                if (getVerticalGridView() == null) {
                    return;
                }
                RecyclerView.ViewHolder vh = getVerticalGridView().findViewHolderForPosition(0);
                if (vh != null) {
                    final float fraction = (Float) arg0.getAnimatedValue();
                    if (DEBUG) Log.v(TAG, "fraction " + fraction);
                    vh.itemView.setAlpha(fraction);
                    if (mTranslateAnimationEnabled) {
                        vh.itemView.setTranslationY((float) mFadeTranslateY * (1f - fraction));
                    }
                }
            }
        };

        mControlRowFadeInAnimator = loadAnimator(
                getActivity(), R.animator.lb_playback_controls_fade_in);
        mControlRowFadeInAnimator.addUpdateListener(listener);
        mControlRowFadeInAnimator.setInterpolator(mLogDecelerateInterpolator);

        mControlRowFadeOutAnimator = loadAnimator(
                getActivity(), R.animator.lb_playback_controls_fade_out);
        mControlRowFadeOutAnimator.addUpdateListener(listener);
        mControlRowFadeOutAnimator.setInterpolator(mLogAccelerateInterpolator);
    }

    private void loadOtherRowAnimator() {
        AnimatorUpdateListener listener = new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator arg0) {
                if (getVerticalGridView() == null) {
                    return;
                }
                final float fraction = (Float) arg0.getAnimatedValue();
                final int count = getVerticalGridView().getChildCount();
                for (int i = 0; i < count; i++) {
                    View view = getVerticalGridView().getChildAt(i);
                    if (getVerticalGridView().getChildPosition(view) > 0) {
                        view.setAlpha(fraction);
                        if (mTranslateAnimationEnabled) {
                            view.setTranslationY((float) mFadeTranslateY * (1f - fraction));
                        }
                    }
                }
            }
        };

        mOtherRowFadeInAnimator = loadAnimator(
                getActivity(), R.animator.lb_playback_controls_fade_in);
        mOtherRowFadeInAnimator.addUpdateListener(listener);
        mOtherRowFadeInAnimator.setInterpolator(mLogDecelerateInterpolator);

        mOtherRowFadeOutAnimator = loadAnimator(
                getActivity(), R.animator.lb_playback_controls_fade_out);
        mOtherRowFadeOutAnimator.addUpdateListener(listener);
        mOtherRowFadeOutAnimator.setInterpolator(mLogDecelerateInterpolator);
    }

    private void fade(boolean fadeIn) {
        if (DEBUG) Log.v(TAG, "fade " + fadeIn);
        if (getView() == null) {
            return;
        }
        if ((fadeIn && mFadingStatus == IN) || (!fadeIn && mFadingStatus == OUT)) {
            if (DEBUG) Log.v(TAG, "requested fade in progress");
            return;
        }
        if ((fadeIn && mBgAlpha == 255) || (!fadeIn && mBgAlpha == 0)) {
            if (DEBUG) Log.v(TAG, "fade is no-op");
            return;
        }

        mTranslateAnimationEnabled = getVerticalGridView().getSelectedPosition() == 0;

        if (mFadingStatus == IDLE) {
            if (fadeIn) {
                mBgFadeInAnimator.start();
                mControlRowFadeInAnimator.start();
                mOtherRowFadeInAnimator.start();
            } else {
                mBgFadeOutAnimator.start();
                mControlRowFadeOutAnimator.start();
                mOtherRowFadeOutAnimator.start();
            }
        } else {
            if (fadeIn) {
                mBgFadeOutAnimator.reverse();
                mControlRowFadeOutAnimator.reverse();
                mOtherRowFadeOutAnimator.reverse();
            } else {
                mBgFadeInAnimator.reverse();
                mControlRowFadeInAnimator.reverse();
                mOtherRowFadeInAnimator.reverse();
            }
        }

        // If fading in while control row is focused, set initial translationY so
        // views slide in from below.
        if (fadeIn && mFadingStatus == IDLE && mTranslateAnimationEnabled) {
            final int count = getVerticalGridView().getChildCount();
            for (int i = 0; i < count; i++) {
                getVerticalGridView().getChildAt(i).setTranslationY(mFadeTranslateY);
            }
        }

        mFadingStatus = fadeIn ? IN : OUT;
    }

    /**
     * Sets the list of rows for the fragment.
     */
    @Override
    public void setAdapter(ObjectAdapter adapter) {
        if (getAdapter() != null) {
            getAdapter().unregisterObserver(mObserver);
        }
        super.setAdapter(adapter);
        if (adapter != null) {
            adapter.registerObserver(mObserver);
        }
        setVerticalGridViewLayout(getVerticalGridView());
    }

    @Override
    void setVerticalGridViewLayout(VerticalGridView listview) {
        if (listview == null || getAdapter() == null) {
            return;
        }
        final int alignPosition = getAdapter().size() > 1 ? mAlignPosition : 0;
        listview.setItemAlignmentOffset(alignPosition);
        listview.setItemAlignmentOffsetPercent(100);
        listview.setWindowAlignmentOffset(0);
        listview.setWindowAlignmentOffsetPercent(100);
        listview.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_HIGH_EDGE);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAlignPosition =
            getResources().getDimensionPixelSize(R.dimen.lb_playback_controls_align_bottom);
        mBgDarkColor =
                getResources().getColor(R.color.lb_playback_controls_background_dark);
        mBgLightColor =
                getResources().getColor(R.color.lb_playback_controls_background_light);
        mShowTimeMs =
                getResources().getInteger(R.integer.lb_playback_controls_show_time_ms);
        mFadeTranslateY =
                getResources().getDimensionPixelSize(R.dimen.lb_playback_fade_translate_y);

        loadBgAnimator();
        loadControlRowAnimator();
        loadOtherRowAnimator();
    }

    /**
     * Sets the background type.
     *
     * @param type One of BG_LIGHT, BG_DARK, or BG_NONE.
     */
    public void setBackgroundType(int type) {
        switch (type) {
        case BG_LIGHT:
        case BG_DARK:
        case BG_NONE:
            if (type != mBackgroundType) {
                mBackgroundType = type;
                updateBackground();
            }
            break;
        default:
            throw new IllegalArgumentException("Invalid background type");
        }
    }

    /**
     * Returns the background type.
     */
    public int getBackgroundType() {
        return mBackgroundType;
    }

    private void updateBackground() {
        if (mRootView != null) {
            int color = mBgDarkColor;
            switch (mBackgroundType) {
                case BG_DARK: break;
                case BG_LIGHT: color = mBgLightColor; break;
                case BG_NONE: color = Color.TRANSPARENT; break;
            }
            mRootView.setBackground(new ColorDrawable(color));
        }
    }

    private final ItemBridgeAdapter.AdapterListener mAdapterListener =
            new ItemBridgeAdapter.AdapterListener() {
        @Override
        public void onAttachedToWindow(ItemBridgeAdapter.ViewHolder vh) {
            if (DEBUG) Log.v(TAG, "onAttachedToWindow " + vh.getViewHolder().view);
            if ((mFadingStatus == IDLE && mBgAlpha == 0) || mFadingStatus == OUT) {
                if (DEBUG) Log.v(TAG, "setting alpha to 0");
                vh.getViewHolder().view.setAlpha(0);
            }
        }
        @Override
        public void onDetachedFromWindow(ItemBridgeAdapter.ViewHolder vh) {
            if (DEBUG) Log.v(TAG, "onDetachedFromWindow " + vh.getViewHolder().view);
            // Reset animation state
            vh.getViewHolder().view.setAlpha(1f);
            vh.getViewHolder().view.setTranslationY(0);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = super.onCreateView(inflater, container, savedInstanceState);
        mBgAlpha = 255;
        updateBackground();
        getRowsFragment().setExternalAdapterListener(mAdapterListener);
        return mRootView;
    }

    @Override
    public void onDestroyView() {
        mRootView = null;
        super.onDestroyView();
    }

    private final DataObserver mObserver = new DataObserver() {
        public void onChanged() {
            setVerticalGridViewLayout(getVerticalGridView());
        }
    };
}