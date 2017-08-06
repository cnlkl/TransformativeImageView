package cn.lkllkllkl.transformativeimageview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PointF;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;
import android.view.MotionEvent;


/**
 * 多点触控加Matrix类实现图片的旋转、缩放、平移
 *
 * @attr R.styleable#TransformativeImageView_max_scale
 * @attr R.styleable#TransformativeImageView_min_scale
 * @attr R.styleable#TransformativeImageView_revert_duration
 * @attr R.styleable#TransformativeImageView_revert
 * @attr R.styleable#TransformativeImageView_scale_center
 */

public class TransformativeImageView extends AppCompatImageView {
    private static final String TAG = TransformativeImageView.class.getSimpleName();
    private static final float MAX_SCALE_FACTOR = 2.0f; // 默认最大缩放比例为2
    private static final float UNSPECIFIED_SCALE_FACTOR = -1f; // 未指定缩放比例
    private static final float MIN_SCALE_FACTOR = 1.0f; // 默认最小缩放比例为0.3
    private static final float INIT_SCALE_FACTOR = 1.2f; // 默认适应控件大小后的初始化缩放比例
    private static final int DEFAULT_REVERT_DURATION = 300;

    private int mRevertDuration = DEFAULT_REVERT_DURATION; // 回弹动画时间
    private float mMaxScaleFactor = MAX_SCALE_FACTOR; // 最大缩放比例
    private float mMinScaleFactor = UNSPECIFIED_SCALE_FACTOR; // 此最小缩放比例优先级高于下面两个
    private float mVerticalMinScaleFactor = MIN_SCALE_FACTOR; // 图片最初的最小缩放比例
    private float mHorizontalMinScaleFactor = MIN_SCALE_FACTOR; // 图片旋转90（或-90）度后的的最小缩放比例
    protected Matrix mMatrix = new Matrix(); // 用于图片旋转、平移、缩放的矩阵
    protected RectF mImageRect = new RectF(); // 保存图片所在区域矩形，坐标为相对于本View的坐标
    private boolean mOpenScaleRevert = false; // 是否开启缩放回弹
    private boolean mOpenRotateRevert = false; // 是否开启旋转回弹
    private boolean mOpenTranslateRevert = false; // 是否开启平移回弹


    public TransformativeImageView(Context context) {
        super(context);
    }

    public TransformativeImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TransformativeImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        obtainAttrs(attrs);
        init();

    }

    private void obtainAttrs(AttributeSet attrs){
        if (attrs == null) return;

        TypedArray typedArray = getContext()
                .obtainStyledAttributes(attrs, R.styleable.TransformativeImageView);
        mMaxScaleFactor = typedArray.getFloat(
                R.styleable.TransformativeImageView_max_scale, MAX_SCALE_FACTOR);
        mMinScaleFactor = typedArray.getFloat(
                R.styleable.TransformativeImageView_min_scale, UNSPECIFIED_SCALE_FACTOR);
        mRevertDuration = typedArray.getInteger(
                R.styleable.TransformativeImageView_revert_duration, DEFAULT_REVERT_DURATION);
        mOpenScaleRevert = typedArray.getBoolean(
                R.styleable.TransformativeImageView_open_scale_revert, false);
        mOpenRotateRevert = typedArray.getBoolean(
                R.styleable.TransformativeImageView_open_rotate_revert, false);
        mOpenTranslateRevert = typedArray.getBoolean(
                R.styleable.TransformativeImageView_open_translate_revert, false);
        mScaleBy = typedArray.getInt(
                R.styleable.TransformativeImageView_scale_center, SCALE_BY_IMAGE_CENTER);
        typedArray.recycle();
    }

    private void init() {
        // FIXME 修复图片锯齿,关闭硬件加速ANTI_ALIAS_FLAG才能生效
//        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setScaleType(ScaleType.MATRIX);
        mRevertAnimator.setDuration(mRevertDuration);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        initImgPositionAndSize();
    }

    /**
     * 初始化图片位置和大小
     */
    private void initImgPositionAndSize() {
        mMatrix.reset();
        // 初始化ImageRect
        refreshImageRect();

        // 计算缩放比例，使图片适应控件大小
        mHorizontalMinScaleFactor = Math.min(getWidth() / mImageRect.width(),
                getHeight() / mImageRect.height());
        mVerticalMinScaleFactor = Math.min(getHeight() / mImageRect.width(),
                getWidth() / mImageRect.height());

        // 如果用户有指定最小缩放比例则使用用户指定的
        if (mMinScaleFactor != UNSPECIFIED_SCALE_FACTOR) {
            mHorizontalMinScaleFactor = mMinScaleFactor;
            mVerticalMinScaleFactor = mMinScaleFactor;
        }

        float scaleFactor = mHorizontalMinScaleFactor;

        // 初始图片缩放比例比最小缩放比例稍大
        scaleFactor *= INIT_SCALE_FACTOR;
        mScaleFactor = scaleFactor;
        mMatrix.postScale(scaleFactor, scaleFactor, mImageRect.centerX(), mImageRect.centerY());
        refreshImageRect();
        // 移动图片到中心
        mMatrix.postTranslate(getPivotX() - mImageRect.centerX(),
                getPivotY() - mImageRect.centerY());
        applyMatrix();
    }

    private PaintFlagsDrawFilter mDrawFilter =
            new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.setDrawFilter(mDrawFilter);
        super.onDraw(canvas);
    }

    private PointF mLastPoint1 = new PointF(); // 上次事件的第一个触点
    private PointF mLastPoint2 = new PointF(); // 上次事件的第二个触点
    private PointF mCurrentPoint1 = new PointF(); // 本次事件的第一个触点
    private PointF mCurrentPoint2 = new PointF(); // 本次事件的第二个触点
    private float mScaleFactor = 1.0f; // 当前的缩放倍数
    private boolean mCanScale = false; // 是否可以缩放

    protected PointF mLastMidPoint = new PointF(); // 图片平移时记录上一次ACTION_MOVE的点
    private PointF mCurrentMidPoint = new PointF(); // 当前各触点的中点
    protected boolean mCanDrag = false; // 是否可以平移

    private PointF mLastVector = new PointF(); // 记录上一次触摸事件两指所表示的向量
    private PointF mCurrentVector = new PointF(); // 记录当前触摸事件两指所表示的向量
    private boolean mCanRotate = false; // 判断是否可以旋转

    private MatrixRevertAnimator mRevertAnimator = new MatrixRevertAnimator(); // 回弹动画
    private float[] mFromMatrixValue = new float[9]; // 动画初始时矩阵值
    private float[] mToMatrixValue = new float[9]; // 动画终结时矩阵值

    protected boolean isTransforming = false; // 图片是否正在变化

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        PointF midPoint = getMidPointOfFinger(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                // 每次触摸事件开始都初始化mLastMidPonit
                mLastMidPoint.set(midPoint);
                isTransforming = false;
                mRevertAnimator.cancel();
                // 新手指落下则需要重新判断是否可以对图片进行变换
                mCanRotate = false;
                mCanScale = false;
                mCanDrag = false;
                if (event.getPointerCount() == 2) {
                    // 旋转、平移、缩放分别使用三个判断变量，避免后期某个操作执行条件改变
                    mCanScale = true;
                    mLastPoint1.set(event.getX(0), event.getY(0));
                    mLastPoint2.set(event.getX(1), event.getY(1));
                    mCanRotate = true;
                    mLastVector.set(event.getX(1) - event.getX(0),
                            event.getY(1) - event.getY(0));
                } else if(event.getPointerCount() == 1) {
                    mCanDrag = true;
                }

                break;
            case MotionEvent.ACTION_MOVE:
                if (mCanDrag) translate(midPoint);
                if (mCanScale) scale(event);
                if (mCanRotate) rotate(event);
                // 判断图片是否发生了变换
                if (!getImageMatrix().equals(mMatrix)) isTransforming = true;
                if (mCanDrag || mCanScale || mCanRotate) applyMatrix();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // 检测是否需要回弹
                if(mOpenRotateRevert || mOpenScaleRevert || mOpenTranslateRevert) {
                    mMatrix.getValues(mFromMatrixValue);/*设置矩阵动画初始值*/
                    /* 旋转和缩放都会影响矩阵，进而影响后续需要使用到ImageRect的地方，
                     * 所以检测顺序不能改变
                     */
                    if(mOpenRotateRevert) checkRotation();
                    if(mOpenScaleRevert) checkScale();
                    if(mOpenTranslateRevert) checkBorder();
                    mMatrix.getValues(mToMatrixValue);/*设置矩阵动画结束值*/
                    // 启动回弹动画
                    mRevertAnimator.setMatrixValue(mFromMatrixValue, mToMatrixValue);
                    mRevertAnimator.cancel();
                    mRevertAnimator.start();
                }
            case MotionEvent.ACTION_POINTER_UP:
                mCanScale = false;
                mCanDrag = false;
                mCanRotate = false;
                break;
        }
        super.onTouchEvent(event);
        return true;
    }

    private void rotate(MotionEvent event) {
        // 计算当前两指触点所表示的向量
        mCurrentVector.set(event.getX(1) - event.getX(0),
                event.getY(1) - event.getY(0));
        // 获取旋转角度
        float degree = getRotateDegree(mLastVector, mCurrentVector);
        mMatrix.postRotate(degree, mImageRect.centerX(), mImageRect.centerY());
        mLastVector.set(mCurrentVector);
    }

    /**
     * 使用Math#atan2(double y, double x)方法求上次触摸事件两指所示向量与x轴的夹角，
     * 再求出本次触摸事件两指所示向量与x轴夹角，最后求出两角之差即为图片需要转过的角度
     *
     * @param lastVector 上次触摸事件两指间连线所表示的向量
     * @param currentVector 本次触摸事件两指间连线所表示的向量
     * @return 两向量夹角，单位“度”，顺时针旋转时为正数，逆时针旋转时返回负数
     */
    private float getRotateDegree(PointF lastVector, PointF currentVector) {
        //上次触摸事件向量与x轴夹角
        double lastRad = Math.atan2(lastVector.y, lastVector.x);
        //当前触摸事件向量与x轴夹角
        double currentRad = Math.atan2(currentVector.y, currentVector.x);
        // 两向量与x轴夹角之差即为需要旋转的角度
        double rad = currentRad - lastRad;
        //“弧度”转“度”
        return (float) Math.toDegrees(rad);
    }

    protected void translate(PointF midPoint) {
        float dx = midPoint.x - mLastMidPoint.x;
        float dy = midPoint.y - mLastMidPoint.y;
        mMatrix.postTranslate(dx, dy);
        mLastMidPoint.set(midPoint);
    }

    /**
     * 计算所有触点的中点
     * @param event 当前触摸事件
     * @return 本次触摸事件所有触点的中点
     */
    private PointF getMidPointOfFinger(MotionEvent event) {
        // 初始化mCurrentMidPoint
        mCurrentMidPoint.set(0f, 0f);
        int pointerCount = event.getPointerCount();
        for (int i = 0; i < pointerCount; i++) {
            mCurrentMidPoint.x += event.getX(i);
            mCurrentMidPoint.y += event.getY(i);
        }
        mCurrentMidPoint.x /= pointerCount;
        mCurrentMidPoint.y /= pointerCount;
        return mCurrentMidPoint;
    }

    private static final int SCALE_BY_IMAGE_CENTER = 0; // 以图片中心为缩放中心
    private static final int SCALE_BY_FINGER_MID_POINT = 1; // 以所有手指的中点为缩放中心
    private int mScaleBy = SCALE_BY_IMAGE_CENTER;
    private PointF scaleCenter = new PointF();

    /**
     * 获取图片的缩放中心，该属性可在外部设置，或通过xml文件设置
     * 默认中心点为图片中心
     * @return 图片的缩放中心点
     */
    private PointF getScaleCenter() {
        // 使用全局变量避免频繁创建变量
        switch (mScaleBy) {
            case SCALE_BY_IMAGE_CENTER:
                scaleCenter.set(mImageRect.centerX(), mImageRect.centerY());
                break;
            case SCALE_BY_FINGER_MID_POINT:
                scaleCenter.set(mLastMidPoint.x, mLastMidPoint.y);
                break;
        }
        return scaleCenter;
    }

    private void scale(MotionEvent event) {
        PointF scaleCenter = getScaleCenter();

        // 初始化当前两指触点
        mCurrentPoint1.set(event.getX(0), event.getY(0));
        mCurrentPoint2.set(event.getX(1), event.getY(1));
        // 计算缩放比例
        float scaleFactor = distance(mCurrentPoint1, mCurrentPoint2)
                / distance(mLastPoint1, mLastPoint2);

        // 更新当前图片的缩放比例
        mScaleFactor *= scaleFactor;

        mMatrix.postScale(scaleFactor, scaleFactor,
                scaleCenter.x, scaleCenter.y);
        mLastPoint1.set(mCurrentPoint1);
        mLastPoint2.set(mCurrentPoint2);
    }

    /**
     * 获取两点间距离
     */
    private float distance(PointF point1, PointF point2) {
        float dx = point2.x - point1.x;
        float dy = point2.y - point1.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 根据当前图片旋转的角度，判断是否回弹
     */
    private void checkRotation() {
        float currentDegree = getCurrentRotateDegree();
        float degree = currentDegree;
        // 根据当前图片旋转的角度值所在区间，判断要转到几度
        degree = Math.abs(degree);
        if (degree > 45 && degree <= 135) {
            degree = 90;
        } else if(degree > 135 && degree <= 225) {
            degree = 180;
        } else if(degree > 225 && degree <= 315) {
            degree = 270;
        } else {
            degree = 0;
        }
        // 判断顺时针还是逆时针旋转
        degree = currentDegree < 0 ? -degree : degree;
        mMatrix.postRotate(degree - currentDegree, mImageRect.centerX(), mImageRect.centerY());
    }

    private float[] xAxis = new float[]{1f, 0f}; // 表示与x轴同方向的向量
    /**
     * 获取当前图片旋转角度
     * @return 图片当前的旋转角度
     */
    private float getCurrentRotateDegree() {
        // 每次重置初始向量的值为与x轴同向
        xAxis[0] = 1f;
        xAxis[1] = 0f;
        // 初始向量通过矩阵变换后的向量
        mMatrix.mapVectors(xAxis);
        // 变换后向量与x轴夹角
        double rad = Math.atan2(xAxis[1], xAxis[0]);
        return (float) Math.toDegrees(rad);

    }

    /**
     * 检查图片缩放比例是否超过设置的大小
     */
    private void checkScale() {
        PointF scaleCenter = getScaleCenter();

        float scaleFactor = 1.0f;

        // 获取图片当前是水平还是垂直
        int imgOrientation = imgOrientation();
        // 超过设置的上限或下限则回弹到设置的限制值
        // 除以当前图片缩放比例mScaleFactor，postScale()方法执行后的图片的缩放比例即为被除数大小
        if (imgOrientation == HORIZONTAL
                && mScaleFactor < mHorizontalMinScaleFactor) {
            scaleFactor = mHorizontalMinScaleFactor / mScaleFactor;
        } else if (imgOrientation == VERTICAL
                && mScaleFactor < mVerticalMinScaleFactor) {
            scaleFactor = mVerticalMinScaleFactor / mScaleFactor;
        }else if(mScaleFactor > mMaxScaleFactor) {
            scaleFactor = mMaxScaleFactor / mScaleFactor;
        }

        mMatrix.postScale(scaleFactor, scaleFactor, scaleCenter.x, scaleCenter.y);
        mScaleFactor *= scaleFactor;
    }

    private static final int HORIZONTAL = 0;
    private static final int VERTICAL = 1;

    /**
     * 判断图片当前是水平还是垂直
     * @return 水平则返回 {@code HORIZONTAL}，垂直则返回 {@code VERTICAL}
     */
    private int imgOrientation() {
        float degree = Math.abs(getCurrentRotateDegree());
        int orientation = HORIZONTAL;
        if (degree > 45f && degree <= 135f) {
            orientation = VERTICAL;
        }
        return orientation;
    }

    /**
     * 将图片移回控件中心
     */
    private void checkBorder() {
        // 由于旋转回弹与缩放回弹会影响图片所在位置，所以此处需要更新ImageRect的值
        refreshImageRect();
        // 默认不移动
        float dx = 0f;
        float dy = 0f;

        // mImageRect中的坐标值为相对View的值
        // 图片宽大于控件时图片与控件之间不能有白边
        if (mImageRect.width() > getWidth()) {
            if (mImageRect.left > 0) {/*判断图片左边界与控件之间是否有空隙*/
                dx = -mImageRect.left;
            } else if(mImageRect.right < getWidth()) {/*判断图片右边界与控件之间是否有空隙*/
                dx = getWidth() - mImageRect.right;
            }
        } else {/*宽小于控件则移动到中心*/
            dx = getWidth() / 2 - mImageRect.centerX();
        }

        // 图片高大于控件时图片与控件之间不能有白边
        if (mImageRect.height() > getHeight()) {
            if (mImageRect.top > 0) {/*判断图片上边界与控件之间是否有空隙*/
                dy = -mImageRect.top;
            } else if(mImageRect.bottom < getHeight()) {/*判断图片下边界与控件之间是否有空隙*/
                dy = getHeight() - mImageRect.bottom;
            }
        } else {/*高小于控件则移动到中心*/
            dy = getHeight() / 2 - mImageRect.centerY();
        }
        mMatrix.postTranslate(dx, dy);
    }

    /**
     * 更新图片所在区域，并将矩阵应用到图片
     */
    protected void applyMatrix() {
        refreshImageRect(); /*将矩阵映射到ImageRect*/
        setImageMatrix(mMatrix);
    }

    /**
     * 图片使用矩阵变换后，刷新图片所对应的mImageRect所指示的区域
     */
    private void refreshImageRect() {
        if (getDrawable() != null) {
            mImageRect.set(getDrawable().getBounds());
            mMatrix.mapRect(mImageRect, mImageRect);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mRevertAnimator.cancel();
    }

    //-----Aninmator-------------------

    /**
     * 图片回弹动画
     */
    private class MatrixRevertAnimator extends ValueAnimator
            implements ValueAnimator.AnimatorUpdateListener{

        private float[] mFromMatrixValue; // 动画初始时矩阵值
        private float[] mToMatrixValue; // 动画终结时矩阵值
        private float[] mInterpolateMatrixValue; // 动画执行过程中矩阵值

        MatrixRevertAnimator() {
            mInterpolateMatrixValue = new float[9];
            setFloatValues(0f, 1f);
            addUpdateListener(this);
        }

        void setMatrixValue(float[] fromMatrixValue, final float[] toMatrixValue) {
            mFromMatrixValue = fromMatrixValue;
            mToMatrixValue = toMatrixValue;

            addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mMatrix.setValues(toMatrixValue);
                    applyMatrix();
                }
            });
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            if (mFromMatrixValue != null
                    && mToMatrixValue != null && mInterpolateMatrixValue != null) {
                // 根据动画当前进度设置矩阵的值
                for (int i = 0; i < 9; i++) {
                    float animatedValue = (float) animation.getAnimatedValue();
                    mInterpolateMatrixValue[i] = mFromMatrixValue[i]
                                    + (mToMatrixValue[i] - mFromMatrixValue[i]) * animatedValue;
                }
                mMatrix.setValues(mInterpolateMatrixValue);
                applyMatrix();
            }
        }

    }

    //-------getter and setter---------

    public void setmMaxScaleFactor(float mMaxScaleFactor) {
        this.mMaxScaleFactor = mMaxScaleFactor;
    }
}
