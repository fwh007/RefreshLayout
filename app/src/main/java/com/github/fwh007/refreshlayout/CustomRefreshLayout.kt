package com.github.fwh007.refreshlayout

import android.content.Context
import android.graphics.drawable.Drawable
import android.support.v4.view.*
import android.support.v4.widget.ListViewCompat
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.view.animation.Transformation
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import kotlin.properties.Delegates

/**
 * 下拉刷新控件
 * Created by Winter on 2018/6/13.
 */
public class CustomRefreshLayout : ViewGroup, NestedScrollingParent2, NestedScrollingChild2 {

    private val DEFAULT_REFRESH_SLOP = 100
    private val ANIMATE_DURATION = 200
    private val ANIMATE_INTERPOLATOR = DecelerateInterpolator(2f);

    private var mOnRefreshListener: OnRefreshListener? = null
    private var mRefreshProgressListener: RefreshProgressListener? = null
    private var mRefreshSlop: Int = DEFAULT_REFRESH_SLOP
    private var mIsSpinnerOver: Boolean = true

    private val mParentHelper = NestedScrollingParentHelper(this)
    private val mChildHelper = NestedScrollingChildHelper(this)
    private val mParentScrollConsumed = IntArray(2)
    private val mParentOffsetInWindow = IntArray(2)
    private var mStatus: Status = Status.Ready
    private var mTarget: View? = null // 滑动的控件
    private var mSpinner: FrameLayout by Delegates.notNull() // 刷新样式的控件
    private var mSpinnerIndex: Int = -1
    private var mTotalUnconsumed = 0f
    private var mCurrentSpinnerOffsetTop = 0
    private var mOriginalSpinnerOffsetTop = 0
    private var mAnimateFrom = 0

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs, defStyleAttr)
    }

    /**
     * Notify the widget that refresh state has changed. Do not call this when
     * refresh is triggered by a swipe gesture.
     *
     * @param refreshing Whether or not the view should show refresh progress.
     */
    public fun setRefreshing(refreshing: Boolean) {
        setRefreshing(refreshing, false)
    }

    public fun setSpinnerDrawable(drawable: Drawable?) {
        mSpinner.removeAllViews()
        if (drawable != null) {
            val imageView = ImageView(context)
            imageView.setImageDrawable(drawable)
            mSpinner.addView(imageView)
        }
        requestLayout()
    }

    public fun setSpinnerLayout(layout: View?) {
        mSpinner.removeAllViews()
        if (layout != null) {
            mSpinner.addView(layout)
        }
        requestLayout()
    }

    public fun setRefreshSlop(slop: Int) {
        mRefreshSlop = slop
    }

    public fun setOnRefreshListener(listener: OnRefreshListener?) {
        mOnRefreshListener = listener
    }

    public fun setRefreshProgressListener(listener: RefreshProgressListener?) {
        mRefreshProgressListener = listener
    }

    public fun setIsSpinnerOVer(isOver: Boolean) {
        mIsSpinnerOver = isOver
        requestLayout()
    }

    private fun init(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) {
        createSpinner()
        mSpinner.visibility = View.GONE

        val drawable = CustomRefreshDrawable()
        setSpinnerDrawable(drawable)
        setRefreshSlop(drawable.intrinsicHeight)
        setRefreshProgressListener(drawable)
    }

    private fun createSpinner() {
        mSpinner = FrameLayout(context)
        addView(mSpinner)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (mTarget == null) {
            ensureTarget()
        }
        if (mTarget == null) {
            return
        }
        mTarget?.measure(MeasureSpec.makeMeasureSpec(
                measuredWidth - paddingLeft - paddingRight,
                MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(
                measuredHeight - paddingTop - paddingBottom, MeasureSpec.EXACTLY))
        mSpinner.measure(
                MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.AT_MOST))
        mOriginalSpinnerOffsetTop = -(mSpinner.measuredHeight)
        mSpinnerIndex = -1
        // Get the index of the circleview.
        for (index in 0 until childCount) {
            if (getChildAt(index) === mSpinner) {
                mSpinnerIndex = index
                break
            }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = measuredWidth
        val height = measuredHeight
        if (childCount == 0) {
            return
        }
        if (mTarget == null) {
            ensureTarget()
        }
        val child = mTarget ?: return
        val childLeft = paddingLeft
        var childTop = paddingTop
        if (!mIsSpinnerOver) {
            childTop += mCurrentSpinnerOffsetTop - mOriginalSpinnerOffsetTop
        }
        val childWidth = width - paddingLeft - paddingRight
        val childHeight = height - paddingTop - paddingBottom
        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
        val spinnerWidth = mSpinner.measuredWidth
        val spinnerHeight = mSpinner.measuredHeight
        mSpinner.layout(width / 2 - spinnerWidth / 2, mCurrentSpinnerOffsetTop,
                width / 2 + spinnerWidth / 2, mCurrentSpinnerOffsetTop + spinnerHeight)
    }

    override fun getChildDrawingOrder(childCount: Int, i: Int): Int {
        return when {
            mSpinnerIndex < 0 -> i
            i == childCount - 1 -> // 最后一位绘制
                mSpinnerIndex
            i >= mSpinnerIndex -> // 提早一位绘制
                i + 1
            else -> // 保持原顺序绘制
                i
        }
    }

    // NestedScrollingParent

    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int, type: Int): Boolean {
        return (isEnabled && mStatus != Status.Refresh
                && nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL != 0 //只接受垂直方向的滚动
                && type == ViewCompat.TYPE_TOUCH)//只接受touch的滚动，不接受fling
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        mParentHelper.onNestedScrollAccepted(child, target, axes, type)
        // 分发事件给Nested Parent
        startNestedScroll(axes and ViewCompat.SCROLL_AXIS_VERTICAL, type)
        // 重置计数
        mTotalUnconsumed = 0f
        mStatus = Status.Pull
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray?, type: Int) {
        consumed ?: return

        // 如果在下拉过程中，直接响应并消耗上滑距离，调整Spinner位置
        if (dy > 0 && mTotalUnconsumed > 0) {
            if (dy > mTotalUnconsumed) {
                consumed[1] = dy - mTotalUnconsumed.toInt()
                mTotalUnconsumed = 0f
            } else {
                mTotalUnconsumed -= dy.toFloat()
                consumed[1] = dy
            }
            moveSpinner(mTotalUnconsumed)
        }

        // 让Nested Parent来处理剩下的滑动距离
        val parentConsumed = mParentScrollConsumed
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null, type)) {
            consumed[0] += parentConsumed[0]
            consumed[1] += parentConsumed[1]
        }
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        mParentHelper.onStopNestedScroll(target)
        // 如果有处理过滑动事件，执行滑动停止后的操作
        if (mTotalUnconsumed > 0) {
            finishSpinner(mTotalUnconsumed)
            mTotalUnconsumed = 0f
        }
        // 分发事件给Nested Parent
        stopNestedScroll(type)
    }

    override fun onNestedScroll(target: View, dxConsumed: Int, dyConsumed: Int,
                                dxUnconsumed: Int, dyUnconsumed: Int, type: Int) {
        // 首先分发事件给Nested Parent
        dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed,
                mParentOffsetInWindow, type)

        // 考虑到有时候可能被两个nested scrolling view包围，这里计算滑动距离时要加上Nested Parent滑动的距离
        // 如果可以刷新，移动刷新控件的位置
        val dy = dyUnconsumed + mParentOffsetInWindow[1]
        if (dy < 0 && !canChildScrollUp()) {
            mTotalUnconsumed += Math.abs(dy).toFloat()
            moveSpinner(mTotalUnconsumed)
        }
    }

    // NestedScrollingChild

    override fun startNestedScroll(axes: Int, type: Int): Boolean {
        return mChildHelper.startNestedScroll(axes, type)
    }

    override fun stopNestedScroll(type: Int) {
        mChildHelper.stopNestedScroll(type)
    }

    override fun hasNestedScrollingParent(type: Int): Boolean {
        return mChildHelper.hasNestedScrollingParent(type)
    }

    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int,
                                      dyUnconsumed: Int, offsetInWindow: IntArray?, type: Int): Boolean {
        return mChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, offsetInWindow, type)
    }

    override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int): Boolean {
        return mChildHelper.dispatchNestedPreScroll(
                dx, dy, consumed, offsetInWindow, type)
    }

    /**
     * 设置刷新状态
     * @param refreshing 是否在刷新
     * @param notify 是否通知listener
     */
    private fun setRefreshing(refreshing: Boolean, notify: Boolean) {
        val isRefreshing = mStatus == Status.Refresh
        if (isRefreshing != refreshing) {
            ensureTarget()
            if (refreshing) {
                mStatus = Status.Refresh
                animateSpinnerToRefresh()
                if (notify) {
                    mOnRefreshListener?.onRefresh()
                }
            } else {
                mStatus = Status.Ready
                animateSpinnerToReady()
            }
        }
    }

    /**
     * 移动刷新控件的垂直位置
     */
    private fun moveSpinner(overscrollTop: Float) {
        if (mSpinner.visibility != View.VISIBLE) {
            mSpinner.visibility = View.VISIBLE
        }
        val move = if (overscrollTop <= mRefreshSlop) {
            overscrollTop
        } else {
            mRefreshSlop + (overscrollTop - mRefreshSlop) / 2f
        }.toInt()
        val targetOffsetTop = mOriginalSpinnerOffsetTop + move
        setSpinnerOffsetTopAndBottom(targetOffsetTop - mCurrentSpinnerOffsetTop)
        updateProgress()
    }

    /**
     * 停止下拉后的操作
     */
    private fun finishSpinner(overscrollTop: Float) {
        if (overscrollTop > mRefreshSlop) {
            setRefreshing(true, true /* notify */)
        } else {
            // cancel refresh
            mStatus = Status.Ready
            animateSpinnerToReady()
        }
    }

    /**
     * 让Spinner带动画移动到准备位置
     */
    private fun animateSpinnerToReady() {
        mAnimateFrom = mCurrentSpinnerOffsetTop
        mAnimateToReady.reset()
        mAnimateToReady.duration = ANIMATE_DURATION.toLong()
        mAnimateToReady.interpolator = ANIMATE_INTERPOLATOR
        mSpinner.clearAnimation()
        mSpinner.startAnimation(mAnimateToReady)
    }

    //移动到准备位置的动画
    private val mAnimateToReady = object : Animation() {
        public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            val targetTop = mAnimateFrom + ((mOriginalSpinnerOffsetTop - mAnimateFrom) * interpolatedTime).toInt()
            val offset = targetTop - mCurrentSpinnerOffsetTop
            setSpinnerOffsetTopAndBottom(offset)
            updateProgress()
        }
    }

    /**
     * 让Spinner带动画移动到刷新位置
     */
    private fun animateSpinnerToRefresh() {
        mAnimateFrom = mCurrentSpinnerOffsetTop
        mAnimateToRefresh.reset()
        mAnimateToRefresh.duration = ANIMATE_DURATION.toLong()
        mAnimateToRefresh.interpolator = ANIMATE_INTERPOLATOR
        mSpinner.clearAnimation()
        mSpinner.startAnimation(mAnimateToRefresh)
    }

    //移动到刷新位置的动画
    private val mAnimateToRefresh = object : Animation() {
        public override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            val endTarget = mOriginalSpinnerOffsetTop + mRefreshSlop
            val targetTop = mAnimateFrom + ((endTarget - mAnimateFrom) * interpolatedTime).toInt()
            val offset = targetTop - mCurrentSpinnerOffsetTop
            setSpinnerOffsetTopAndBottom(offset)
            updateProgress()
        }
    }

    /**
     * 设置Spinner的位置
     */
    private fun setSpinnerOffsetTopAndBottom(offset: Int) {
        mSpinner.bringToFront()
        ViewCompat.offsetTopAndBottom(mSpinner, offset)
        mCurrentSpinnerOffsetTop = mSpinner.top
        if (!mIsSpinnerOver) {
            ViewCompat.offsetTopAndBottom(mTarget, offset)
        }
    }

    private fun updateProgress() {
        val progress = (mCurrentSpinnerOffsetTop - mOriginalSpinnerOffsetTop) / (mRefreshSlop.toFloat())
        mRefreshProgressListener?.updateProgress(mStatus, progress)
    }

    private fun ensureTarget() {
        // 确保要处理滑动的子View存在
        if (mTarget == null) {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child != mSpinner) {
                    mTarget = child
                    break
                }
            }
        }
    }

    /**
     * @return Whether it is possible for the child view of this layout to
     * scroll up. Override this if the child view is a custom view.
     */
    private fun canChildScrollUp(): Boolean {
        return if (mTarget is ListView) {
            ListViewCompat.canScrollList(mTarget as ListView, -1)
        } else mTarget?.canScrollVertically(-1) ?: false
    }

    public enum class Status {
        Ready, Pull, Refresh
    }

    public interface OnRefreshListener {
        fun onRefresh()
    }

    public interface RefreshProgressListener {
        fun updateProgress(status: Status, progress: Float)
    }
}