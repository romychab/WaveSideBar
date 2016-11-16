package com.gjiazhe.wavesidebar;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.CountDownTimer;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.Arrays;


public class WaveSideBar extends View {

    private final static int DEFAULT_TEXT_SIZE = 14; // sp
    private final static int DEFAULT_MAX_OFFSET = 80; //dp

    private final static int DEFAULT_SCROLL_DELAY = 125; // ms
    private final static int DEFAULT_SCROLL_THRESHOLD = 50; // px

    private final static int ANIMATION_PERIOD = 20;

    private final static String[] DEFAULT_INDEX_ITEMS = {"A", "B", "C", "D", "E", "F", "G", "H", "I",
            "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};

    private String[] mIndexItems;

    /**
     * the index in {@link #mIndexItems} of the current selected index item,
     * it's reset to -1 when the finger up
     */
    private int mCurrentIndex = -1;

    /**
     * Y coordinate of the point where finger is touching,
     * the baseline is top of {@link #mStartTouchingArea}
     * it's reset to -1 when the finger up
     */
    private float mCurrentY = -1;

    private Paint mPaint;
    private int mTextColor;
    private float mTextSize;

    /**
     * the height of each index item
     */
    private float mIndexItemHeight;

    /**
     * offset of the current selected index item
     */
    private float mMaxOffset;

    /**
     * {@link #mStartTouching} will be set to true when {@link MotionEvent#ACTION_DOWN}
     * happens in this area, and the side bar should start working.
     */
    private RectF mStartTouchingArea = new RectF();

    /**
     * height and width of {@link #mStartTouchingArea}
     */
    private float mBarHeight;
    private float mBarWidth;

    /**
     * Flag that the finger is starting touching.
     * If true, it means the {@link MotionEvent#ACTION_DOWN} happened but
     * {@link MotionEvent#ACTION_UP} not yet.
     */
    private boolean mStartTouching = false;

    /**
     * if true, the {@link WaveSideBar.OnSelectIndexItemListener#onSelectIndexItem(String)}
     * will not be called until the finger up.
     * if false, it will be called when the finger down, up and move.
     */
    private boolean mLazyRespond = false;

    /**
     * the position of the side bar, default is {@link #POSITION_RIGHT}.
     * You can set it to {@link #POSITION_LEFT} for people who use phone with left hand.
     */
    private int mSideBarPosition;
    public static final int POSITION_RIGHT = 0;
    public static final int POSITION_LEFT = 1;

    /**
     * observe the current selected index item
     */
    private OnSelectIndexItemListener onSelectIndexItemListener;

    /**
     * for {@link #dp2px(int)} and {@link #sp2px(int)}
     */
    private DisplayMetrics mDisplayMetrics;


    // ---
    private int mItemShift;
    private boolean mAutoScrollProcessed = true;
    private boolean mOverhead = false;
    private int mLastCurrent = -1;
    private int mHeight;
    Paint.FontMetrics mFontMetrics;


    private Paint mCirclePaint;
    private float mDestY;
    private float mLastY = -1;
    private int mVisibility = INVISIBLE;

    private int mActiveTextColor = Color.BLUE;
    private int mCircleColor = Color.WHITE;


    public WaveSideBar(Context context) {
        this(context, null);
    }

    public WaveSideBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WaveSideBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDisplayMetrics = context.getResources().getDisplayMetrics();

        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.WaveSideBar);
        mLazyRespond = typedArray.getBoolean(R.styleable.WaveSideBar_sidebar_lazy_respond, false);
        mTextColor = typedArray.getColor(R.styleable.WaveSideBar_sidebar_text_color, Color.GRAY);
        mMaxOffset = typedArray.getDimension(R.styleable.WaveSideBar_sidebar_max_offset, dp2px(DEFAULT_MAX_OFFSET));
        mSideBarPosition = typedArray.getInt(R.styleable.WaveSideBar_sidebar_position, POSITION_RIGHT);
        typedArray.recycle();

        mTextSize = sp2px(DEFAULT_TEXT_SIZE);

        mIndexItems = DEFAULT_INDEX_ITEMS;

        initPaint();
    }

    private void initPaint() {
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setTextAlign(Paint.Align.CENTER);
        mPaint.setColor(mTextColor);
        mPaint.setTextSize(mTextSize);

        mCirclePaint = new Paint();
        mCirclePaint.setStyle(Paint.Style.FILL_AND_STROKE);
        mCirclePaint.setAntiAlias(true);
        mCirclePaint.setColor(mCircleColor);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int height = View.MeasureSpec.getSize(heightMeasureSpec);
        mHeight = height;
        int width = View.MeasureSpec.getSize(widthMeasureSpec);

        Paint.FontMetrics fontMetrics = mPaint.getFontMetrics();
        mFontMetrics = fontMetrics;
        mIndexItemHeight = fontMetrics.bottom - fontMetrics.top;
        mBarHeight = mIndexItems.length * mIndexItemHeight;

        // calculate the width of the longest text as the width of side bar
        for (String indexItem : mIndexItems) {
            mBarWidth = Math.max(mBarWidth, mPaint.measureText(indexItem));
        }

        float areaLeft = (mSideBarPosition == POSITION_LEFT) ? 0 : (width - mBarWidth - getPaddingRight());
        float areaRight = (mSideBarPosition == POSITION_LEFT) ? (getPaddingLeft() + areaLeft + mBarWidth) : width;
        float areaTop = height/2 - mBarHeight/2;
        float areaBottom = areaTop + mBarHeight;
        mStartTouchingArea.set(
                areaLeft,
                areaTop,
                areaRight,
                areaBottom);

        // the baseline Y of the first item' text to draw
        float totalItemsHeight = mIndexItems.length*mIndexItemHeight;

        if (totalItemsHeight < height) {
            mOverhead = false;
        }
        else {
            mOverhead = true;
        }


    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // draw
        if (mLastCurrent >= 0 || mCurrentIndex >= 0) {
            drawCircle(canvas);
        }

        // draw each item
        for (int i = 0, mIndexItemsLength = mIndexItems.length; i < mIndexItemsLength; i++) {
            float baseLineY = getBaseY() + mIndexItemHeight*i;

            // calculate the scale factor of the item to draw
            float scale = getScale(i);

            int alphaScale = (i == mCurrentIndex) ? (255) : (int) (255 * (1-scale));
            mPaint.setAlpha(alphaScale);

            mPaint.setTextSize(mTextSize + mTextSize*scale);

            mPaint.setColor(mLastCurrent == i ? mActiveTextColor : mTextColor);
            mPaint.setFakeBoldText(mLastCurrent == i);

            float drawX = (mSideBarPosition == POSITION_LEFT) ?
                    (getPaddingLeft() + mBarWidth/2 + mMaxOffset*scale) :
                    (getWidth() - getPaddingRight() - mBarWidth/2 - mMaxOffset*scale);

            canvas.drawText(
                    mIndexItems[i], //item text to draw
                    drawX, //center text X
                    baseLineY, // baseLineY
                    mPaint);

        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        mVisibility = visibility;
        if (visibility == VISIBLE) {
            mTimer.start();
        }
        else {
            mTimer.cancel();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisibility = INVISIBLE;
        mTimer.cancel();
    }

    private void updateCircleCoords() {
        if (Math.abs(mLastY - mDestY) > 1) {
            mLastY += (mDestY - mLastY) / 10;
            invalidate();
        }
    }

    private void drawCircle(Canvas canvas) {
        int index = mCurrentIndex >= 0 ? mCurrentIndex : mLastCurrent;

        float baseLineY = getBaseY() + mIndexItemHeight*index;
        float drawX = (mSideBarPosition == POSITION_LEFT) ?
                (getPaddingLeft() + mBarWidth/2 ) :
                (getWidth() - getPaddingRight() - mBarWidth/2);
        mDestY = baseLineY - mTextSize/2.5f;

        canvas.drawCircle(drawX, mLastY, mTextSize/1.5f, mCirclePaint);
    }

    /**
     * calculate the scale factor of the item to draw
     *
     * @param index the index of the item in array {@link #mIndexItems}
     * @return the scale factor of the item to draw
     */
    private float getScale(int index) {
        float scale = 0;
        if (mCurrentIndex != -1) {
            float distance = Math.abs(mCurrentY - (mIndexItemHeight*(index+mItemShift)+mIndexItemHeight/2)) / mIndexItemHeight;
            scale = 1 - distance*distance/16;
            scale = Math.max(scale, 0);
//                Log.i("scale", mIndexItems[index] + ": " + scale);
        }
        return scale;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mIndexItems.length == 0) {
            return super.onTouchEvent(event);
        }

        float eventY = event.getY();
        float eventX = event.getX();
        mCurrentIndex = getSelectedIndex(eventY);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (mStartTouchingArea.contains(eventX, eventY)) {
                    mStartTouching = true;
                    if (!mLazyRespond && onSelectIndexItemListener != null) {
                        onSelectIndexItemListener.onSelectIndexItem(mIndexItems[mCurrentIndex]);
                    }
                    invalidate();
                    return true;
                } else {
                    mCurrentIndex = -1;
                    return false;
                }

            case MotionEvent.ACTION_MOVE:
                if (mStartTouching && !mLazyRespond && onSelectIndexItemListener != null) {
                    mLastCurrent = mCurrentIndex;
                    onSelectIndexItemListener.onSelectIndexItem(mIndexItems[mCurrentIndex]);
                }

                if (mAutoScrollProcessed) {
                    float bottom = (mItemShift + mIndexItems.length) * mIndexItemHeight;
                    boolean done = false;
                    if (getHeight() - eventY < DEFAULT_SCROLL_THRESHOLD && bottom > getHeight()) {
                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mItemShift--;
                                mAutoScrollProcessed = true;
                            }
                        }, DEFAULT_SCROLL_DELAY);
                        done = true;
                    }
                    else if (eventY < DEFAULT_SCROLL_THRESHOLD && mItemShift < 0) {
                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mItemShift++;
                                mAutoScrollProcessed = true;
                            }
                        }, DEFAULT_SCROLL_DELAY);
                        done = true;
                    }
                    if (done) {
                        mAutoScrollProcessed = false;
                    }
                }

                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mLazyRespond && onSelectIndexItemListener != null) {
                    onSelectIndexItemListener.onSelectIndexItem(mIndexItems[mCurrentIndex]);
                }
                mCurrentIndex = -1;
                mStartTouching = false;
                invalidate();
                return true;
        }

        return super.onTouchEvent(event);
    }

    private float getBaseY() {
        if (mOverhead) {
            return (mItemShift+1) * mIndexItemHeight;
        }
        else {
            return (mHeight / 2 - mIndexItems.length * mIndexItemHeight / 2)
                    + (mIndexItemHeight / 2 - (mFontMetrics.descent - mFontMetrics.ascent) / 2)
                    - mFontMetrics.ascent;
        }
    }


    private int getSelectedIndex(float eventY) {
        if (!mOverhead) {
            mCurrentY = eventY - (getHeight() / 2 - mBarHeight / 2);
        }
        else {
            mCurrentY = eventY;
        }
        if (mCurrentY <= 0) {
            return 0;
        }

        int index = (int) (mCurrentY / this.mIndexItemHeight);
        index -= mItemShift;
        if (index >= this.mIndexItems.length) {
            index = this.mIndexItems.length - 1;
        }
        if (index < 0) {
            index = 0;
        }
        return index;
    }

    private float dp2px(int dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, this.mDisplayMetrics);
    }

    private float sp2px(int sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, this.mDisplayMetrics);
    }

    public void setIndexItems(String... indexItems) {
        mIndexItems = Arrays.copyOf(indexItems, indexItems.length);
        requestLayout();
    }

    public void setTextColor(int color) {
        mTextColor = color;
        mPaint.setColor(color);
        invalidate();
    }

    public void setActiveTextColor(int color) {
        mActiveTextColor = color;
        invalidate();
    }

    public void setCircleColor(int color) {
        mCircleColor = color;
        mCirclePaint.setColor(color);
        invalidate();
    }




    public void setPosition(int position) {
        if (position != POSITION_RIGHT && position != POSITION_LEFT) {
            throw new IllegalArgumentException("the position must be POSITION_RIGHT or POSITION_LEFT");
        }

        mSideBarPosition = position;
        requestLayout();
    }

    public void setMaxOffset(int offset) {
        mMaxOffset = offset;
        invalidate();
    }

    public void setLazyRespond(boolean lazyRespond) {
        mLazyRespond = lazyRespond;
    }

    public void setCurrentIndex(int pos) {
        mLastCurrent = pos;
        if (mHeight > 0 && mOverhead) {
            float posY = (pos + mItemShift) * mIndexItemHeight;

            if (posY > mHeight - mIndexItemHeight) {
                float dif = posY - (mHeight - mIndexItemHeight);
                dif /= mIndexItemHeight;
                mItemShift -= (dif + 1);
            }
            else if (posY < 0) {
                mItemShift -= (int)(posY / mIndexItemHeight);
            }

        }
        invalidate();
    }

    public void setOnSelectIndexItemListener(OnSelectIndexItemListener onSelectIndexItemListener) {
        this.onSelectIndexItemListener = onSelectIndexItemListener;
    }

    public interface OnSelectIndexItemListener {
        void onSelectIndexItem(String index);
    }

    private CountDownTimer mTimer = new CountDownTimer(500, ANIMATION_PERIOD) {
        @Override
        public void onTick(long millisUntilFinished) {
            if (mVisibility == VISIBLE) {
                updateCircleCoords();
            }
        }

        @Override
        public void onFinish() {
            if (mVisibility == VISIBLE) {
                mTimer.start();
            }
        }
    };
}
