package com.github.fwh007.refreshlayout

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

/**
 * Created by Winter on 2018/6/20 0020.
 */
class CustomRefreshDrawable : Drawable(), CustomRefreshLayout.RefreshProgressListener {

    private val PIECE_SIZE = UIUtils.dip2px(14)
    private val PIECE_MARGIN = UIUtils.dip2px(8)
    private val PIECE_OFFSET = UIUtils.dip2px(2)
    private val PIECE_COUNT = 3
    private val COLOR = Color.parseColor("#ff6666")
    private val ALPHA_MAX = 0.8f
    private val ALPHA_MIN = 0.3f
    private val PADDING = UIUtils.dip2px(19)
    private val ANIMATION_DURATION = 1000L

    private val mPaint = Paint()
    private val mPath = Path()
    private var mAlphaControlValue: Float = -2f
    private val mAnimator: ObjectAnimator

    init {
        mPaint.isAntiAlias = true
        mPaint.color = COLOR
        mPaint.style = Paint.Style.FILL

        mAnimator = ObjectAnimator.ofFloat(this, "alphaControlValue", -2f, PIECE_COUNT + 1f)
        mAnimator.duration = ANIMATION_DURATION
        mAnimator.interpolator = LinearInterpolator()
        mAnimator.repeatCount = ValueAnimator.INFINITE
    }

    public fun setAlphaControlValue(value: Float) {
        this.mAlphaControlValue = value
        invalidateSelf()
    }

    override fun getIntrinsicWidth(): Int {
        return PIECE_COUNT * PIECE_SIZE + (PIECE_COUNT - 1) * PIECE_MARGIN + PIECE_OFFSET + PADDING * 2
    }

    override fun getIntrinsicHeight(): Int {
        return PIECE_SIZE + PADDING * 2
    }

    override fun draw(canvas: Canvas) {
        val size = PIECE_SIZE.toFloat()
        val offset = PIECE_OFFSET.toFloat()
        val margin = PIECE_MARGIN
        var left = PADDING.toFloat()
        val top = PADDING.toFloat()
        for (i in 0 until PIECE_COUNT) {
            val diff = Math.max(0f, Math.min(Math.abs(i - mAlphaControlValue), 2f)) / 2
            val alpha = ALPHA_MIN + (ALPHA_MAX - ALPHA_MIN) * (1 - diff)
            mPaint.alpha = (alpha * 255).toInt()
            mPath.reset()
            mPath.moveTo(left + offset, top)
            mPath.rLineTo(size, 0f)
            mPath.rLineTo(-offset, size)
            mPath.rLineTo(-size, 0f)
            mPath.close()
            canvas.drawPath(mPath, mPaint)
            left += size + margin
        }
    }

    override fun setAlpha(alpha: Int) {
        //do nothing
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSPARENT
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mPaint.colorFilter = colorFilter
    }

    override fun updateProgress(status: CustomRefreshLayout.Status, progress: Float) {
        when (status) {
            CustomRefreshLayout.Status.Ready -> {
                mAnimator.pause()
                setAlphaControlValue(-2f)
            }
            CustomRefreshLayout.Status.Pull -> {
                mAnimator.pause()
                setAlphaControlValue(-2f)
            }
            CustomRefreshLayout.Status.Refresh -> {
                mAnimator.start()
            }
        }
    }
}