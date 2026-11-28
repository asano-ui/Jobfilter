package com.example.jobfilter

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Susun chip mengikut baris dan turun baris bila tak muat. */
class FlowLayout(ctx: Context, private val hGap: Int, private val vGap: Int) : ViewGroup(ctx) {
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val maxW = MeasureSpec.getSize(wSpec) - paddingLeft - paddingRight
        var x = 0; var y = 0; var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val w = c.measuredWidth; val h = c.measuredHeight
            if (x > 0 && x + w > maxW) { x = 0; y += rowH + vGap; rowH = 0 }
            x += w + hGap; rowH = maxOf(rowH, h)
        }
        setMeasuredDimension(MeasureSpec.getSize(wSpec), y + rowH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0; var y = 0; var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            val w = c.measuredWidth; val h = c.measuredHeight
            if (x > 0 && x + w > maxW) { x = 0; y += rowH + vGap; rowH = 0 }
            c.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + w, paddingTop + y + h)
            x += w + hGap; rowH = maxOf(rowH, h)
        }
    }
}
