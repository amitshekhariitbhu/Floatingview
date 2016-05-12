package com.floating;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

import java.util.ArrayList;

/**
 * Created by amitshekhar on 12/05/16.
 */
public class FloatingViewManager implements ScreenChangedListener, View.OnTouchListener, CloseViewListener {

    public static final int DISPLAY_MODE_SHOW_ALWAYS = 1;

    public static final int DISPLAY_MODE_HIDE_ALWAYS = 2;

    public static final int DISPLAY_MODE_HIDE_FULLSCREEN = 3;

    private static final long VIBRATE_INTERSECTS_MILLIS = 15;

    public static final float SHAPE_CIRCLE = 1.0f;

    public static final float SHAPE_RECTANGLE = 1.4142f;

    private final Context mContext;

    private final WindowManager mWindowManager;

    private FloatingView mTargetFloatingView;

    private final CloseView mCloseView;

    private final FloatingViewListener mFloatingViewListener;

    private final Rect mFloatingViewRect;

    private final Rect mTrashViewRect;

    private final Vibrator mVibrator;

    private boolean mIsMoveAccept;

    private int mDisplayMode;

    private final ArrayList<FloatingView> mFloatingViewList;

    public FloatingViewManager(Context context, FloatingViewListener listener) {
        mContext = context;
        mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        mFloatingViewListener = listener;
        mFloatingViewRect = new Rect();
        mTrashViewRect = new Rect();
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mIsMoveAccept = false;
        mDisplayMode = DISPLAY_MODE_HIDE_FULLSCREEN;

        mFloatingViewList = new ArrayList<>();
        mCloseView = new CloseView(context);
    }

    private boolean isIntersectWithTrash() {
        mCloseView.getWindowDrawingRect(mTrashViewRect);
        mTargetFloatingView.getWindowDrawingRect(mFloatingViewRect);
        return Rect.intersects(mTrashViewRect, mFloatingViewRect);
    }

    @Override
    public void onScreenChanged(boolean isFullscreen) {
        if (mDisplayMode != DISPLAY_MODE_HIDE_FULLSCREEN) {
            return;
        }

        mIsMoveAccept = false;
        final int state = mTargetFloatingView.getState();
        if (state == FloatingView.STATE_NORMAL) {
            final int size = mFloatingViewList.size();
            for (int i = 0; i < size; i++) {
                final FloatingView floatingView = mFloatingViewList.get(i);
                floatingView.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
            }
            mCloseView.dismiss();
        } else if (state == FloatingView.STATE_INTERSECTING) {
            mTargetFloatingView.setFinishing();
            mCloseView.dismiss();
        }
    }

    @Override
    public void onCloseAnimationStarted(int animationCode) {
        if (animationCode == CloseView.ANIMATION_CLOSE || animationCode == CloseView.ANIMATION_FORCE_CLOSE) {
            final int size = mFloatingViewList.size();
            for (int i = 0; i < size; i++) {
                final FloatingView floatingView = mFloatingViewList.get(i);
                floatingView.setDraggable(false);
            }
        }
    }

    @Override
    public void onCloseAnimationEnd(int animationCode) {

        final int state = mTargetFloatingView.getState();
        if (state == FloatingView.STATE_FINISHING) {
            removeViewToWindow(mTargetFloatingView);
        }

        final int size = mFloatingViewList.size();
        for (int i = 0; i < size; i++) {
            final FloatingView floatingView = mFloatingViewList.get(i);
            floatingView.setDraggable(true);
        }

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        final int action = event.getAction();

        if (action != MotionEvent.ACTION_DOWN && !mIsMoveAccept) {
            return false;
        }

        final int state = mTargetFloatingView.getState();
        mTargetFloatingView = (FloatingView) v;

        if (action == MotionEvent.ACTION_DOWN) {
            mIsMoveAccept = true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            final boolean isIntersecting = isIntersectWithTrash();
            final boolean isIntersect = state == FloatingView.STATE_INTERSECTING;
            if (isIntersecting) {
                mTargetFloatingView.setIntersecting((int) mCloseView.getTrashIconCenterX(), (int) mCloseView.getTrashIconCenterY());
            }
            if (isIntersecting && !isIntersect) {
                mVibrator.vibrate(VIBRATE_INTERSECTS_MILLIS);
                mCloseView.setScaleTrashIcon(true);
            } else if (!isIntersecting && isIntersect) {
                mTargetFloatingView.setNormal();
                mCloseView.setScaleTrashIcon(false);
            }

        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (state == FloatingView.STATE_INTERSECTING) {
                mTargetFloatingView.setFinishing();
                mCloseView.setScaleTrashIcon(false);
            }
            mIsMoveAccept = false;
        }

        if (state == FloatingView.STATE_INTERSECTING) {
            mCloseView.onTouchFloatingView(event, mFloatingViewRect.left, mFloatingViewRect.top);
        } else {
            final WindowManager.LayoutParams params = mTargetFloatingView.getWindowLayoutParams();
            mCloseView.onTouchFloatingView(event, params.x, params.y);
        }

        return false;
    }


    public void setFixedTrashIconImage(int resId) {
        mCloseView.setFixedTrashIconImage(resId);
    }

    public void setActionTrashIconImage(int resId) {
        mCloseView.setActionTrashIconImage(resId);
    }

    public void setFixedTrashIconImage(Drawable drawable) {
        mCloseView.setFixedTrashIconImage(drawable);
    }

    public void setActionTrashIconImage(Drawable drawable) {
        mCloseView.setActionTrashIconImage(drawable);
    }

    public void setDisplayMode(int displayMode) {
        mDisplayMode = displayMode;
        if (mDisplayMode == DISPLAY_MODE_SHOW_ALWAYS || mDisplayMode == DISPLAY_MODE_HIDE_FULLSCREEN) {
            for (FloatingView floatingView : mFloatingViewList) {
                floatingView.setVisibility(View.VISIBLE);
            }
        } else if (mDisplayMode == DISPLAY_MODE_HIDE_ALWAYS) {
            for (FloatingView floatingView : mFloatingViewList) {
                floatingView.setVisibility(View.GONE);
            }
            mCloseView.dismiss();
        }
    }

    public void addViewToWindow(View view, float shape, int overMargin) {
        final boolean isFirstAttach = mFloatingViewList.isEmpty();
        final FloatingView floatingView = new FloatingView(mContext);
        floatingView.addView(view);
        view.setClickable(false);
        floatingView.setOnTouchListener(this);
        floatingView.setShape(shape);
        floatingView.setOverMargin(overMargin);
        floatingView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                floatingView.getViewTreeObserver().removeOnPreDrawListener(this);
                mCloseView.calcActionTrashIconPadding(floatingView.getMeasuredWidth(), floatingView.getMeasuredHeight(), floatingView.getShape());
                return false;
            }
        });
        if (mDisplayMode == DISPLAY_MODE_HIDE_ALWAYS) {
            floatingView.setVisibility(View.GONE);
        }
        mFloatingViewList.add(floatingView);
        mCloseView.setTrashViewListener(this);

        mWindowManager.addView(floatingView, floatingView.getWindowLayoutParams());
        if (isFirstAttach) {
            mTargetFloatingView = floatingView;
        } else {
            mWindowManager.removeViewImmediate(mCloseView);
        }
        mWindowManager.addView(mCloseView, mCloseView.getWindowLayoutParams());
    }

    private void removeViewToWindow(FloatingView floatingView) {
        final int matchIndex = mFloatingViewList.indexOf(floatingView);
        if (matchIndex != -1) {
            mWindowManager.removeViewImmediate(floatingView);
            mFloatingViewList.remove(matchIndex);
        }

        if (mFloatingViewList.isEmpty()) {
            if (mFloatingViewListener != null) {
                mFloatingViewListener.onFinishFloatingView();
            }
        }
    }

    public void removeAllViewToWindow() {
        mWindowManager.removeViewImmediate(mCloseView);
        final int size = mFloatingViewList.size();
        for (int i = 0; i < size; i++) {
            final FloatingView floatingView = mFloatingViewList.get(i);
            mWindowManager.removeViewImmediate(floatingView);
        }
        mFloatingViewList.clear();
    }

}
