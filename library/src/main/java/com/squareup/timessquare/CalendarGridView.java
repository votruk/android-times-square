// Copyright 2012 Square, Inc.
package com.squareup.timessquare;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * ViewGroup that draws a grid of calendar cells.  All children must be {@link CalendarRowView}s.
 * The first row is assumed to be a header and no divider is drawn above it.
 */
public class CalendarGridView extends ViewGroup {
    /**
     * The grid lines don't exactly line up on certain devices (Nexus 7, Nexus 5). Fudging the
     * co-ordinates by half a point seems to fix this without breaking other devices.
     */
    private static final float FLOAT_FUDGE = 0.5f;

    private final Paint dividerPaint = new Paint();
    private int oldWidthMeasureSize;
    private int oldNumRows;

    public CalendarGridView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        dividerPaint.setColor(getResources().getColor(R.color.calendar_divider));
    }

    public void setDividerColor(final int color) {
        dividerPaint.setColor(color);
    }

    public void setDayViewAdapter(final DayViewAdapter adapter) {
        for (int i = 0; i < getChildCount(); i++) {
            ((CalendarRowView) getChildAt(i)).setDayViewAdapter(adapter);
        }
    }

    public void setDayBackground(final int resId) {
        for (int i = 1; i < getChildCount(); i++) {
            ((CalendarRowView) getChildAt(i)).setCellBackground(resId);
        }
    }

    public void setDayTextColor(final int resId) {
        for (int i = 0; i < getChildCount(); i++) {
            final ColorStateList colors = getResources().getColorStateList(resId);
            ((CalendarRowView) getChildAt(i)).setCellTextColor(colors);
        }
    }

    public void setDisplayHeader(final boolean displayHeader) {
        getChildAt(0).setVisibility(displayHeader ? VISIBLE : GONE);
    }

    public void setHeaderTextColor(final int color) {
        ((CalendarRowView) getChildAt(0)).setCellTextColor(color);
    }

    public void setTypeface(final Typeface typeface) {
        for (int i = 0; i < getChildCount(); i++) {
            ((CalendarRowView) getChildAt(i)).setTypeface(typeface);
        }
    }

    @Override
    public void addView(final View child, final int index, final ViewGroup.LayoutParams params) {
        if (getChildCount() == 0) {
            ((CalendarRowView) child).setIsHeaderRow(true);
        }
        super.addView(child, index, params);
    }

    @Override
    protected void dispatchDraw(final Canvas canvas) {
        super.dispatchDraw(canvas);
        final ViewGroup row = (ViewGroup) getChildAt(1);
        final int top = row.getTop();
        final int bottom = getBottom();
        // Left side border.
        final int left = row.getChildAt(0).getLeft() + getLeft();
        canvas.drawLine(left + FLOAT_FUDGE, top, left + FLOAT_FUDGE, bottom, dividerPaint);

        // Each cell's right-side border.
        for (int c = 0; c < 7; c++) {
            final float x = left + row.getChildAt(c).getRight() - FLOAT_FUDGE;
            canvas.drawLine(x, top, x, bottom, dividerPaint);
        }
    }

    @Override
    protected boolean drawChild(final Canvas canvas, final View child, final long drawingTime) {
        final boolean retVal = super.drawChild(canvas, child, drawingTime);
        // Draw a bottom border.
        final int bottom = child.getBottom() - 1;
        canvas.drawLine(child.getLeft(), bottom, child.getRight() - 2, bottom, dividerPaint);
        return retVal;
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        Logr.d("Grid.onMeasure w=%s h=%s", MeasureSpec.toString(widthMeasureSpec),
                MeasureSpec.toString(heightMeasureSpec));
        int widthMeasureSize = MeasureSpec.getSize(widthMeasureSpec);
        if (oldWidthMeasureSize == widthMeasureSize) {
            Logr.d("SKIP Grid.onMeasure");
            setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight());
            return;
        }
        final long start = System.currentTimeMillis();
        oldWidthMeasureSize = widthMeasureSize;
        final int cellSize = widthMeasureSize / 7;
        // Remove any extra pixels since /7 is unlikely to give whole nums.
        widthMeasureSize = cellSize * 7;
        int totalHeight = 0;
        final int rowWidthSpec = MeasureSpec.makeMeasureSpec(widthMeasureSize, MeasureSpec.EXACTLY);
        final int rowHeightSpec = MeasureSpec.makeMeasureSpec(cellSize, MeasureSpec.EXACTLY);
        for (int c = 0, numChildren = getChildCount(); c < numChildren; c++) {
            final View child = getChildAt(c);
            if (child.getVisibility() == View.VISIBLE) {
                if (c == 0) { // It's the header: height should be wrap_content.
                    measureChild(child, rowWidthSpec, MeasureSpec.makeMeasureSpec(cellSize, MeasureSpec.AT_MOST));
                } else {
                    measureChild(child, rowWidthSpec, rowHeightSpec);
                }
                totalHeight += child.getMeasuredHeight();
            }
        }
        final int measuredWidth = widthMeasureSize + 2; // Fudge factor to make the borders show up.
        setMeasuredDimension(measuredWidth, totalHeight);
        Logr.d("Grid.onMeasure %d ms", System.currentTimeMillis() - start);
    }

    @Override
    protected void onLayout(final boolean changed, final int left, int top, final int right, final int bottom) {
        final long start = System.currentTimeMillis();
        top = 0;
        for (int c = 0, numChildren = getChildCount(); c < numChildren; c++) {
            final View child = getChildAt(c);
            final int rowHeight = child.getMeasuredHeight();
            child.layout(left, top, right, top + rowHeight);
            top += rowHeight;
        }
        Logr.d("Grid.onLayout %d ms", System.currentTimeMillis() - start);
    }

    public void setNumRows(final int numRows) {
        if (oldNumRows != numRows) {
            // If the number of rows changes, make sure we do a re-measure next time around.
            oldWidthMeasureSize = 0;
        }
        oldNumRows = numRows;
    }
}
