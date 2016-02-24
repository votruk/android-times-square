// Copyright 2012 Square, Inc.
package com.squareup.timessquare;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * TableRow that draws a divider between each cell. To be used with {@link CalendarGridView}.
 */
public class CalendarRowView extends ViewGroup implements View.OnClickListener {
    private boolean isHeaderRow;
    private MonthView.Listener listener;

    public CalendarRowView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void addView(final View child, final int index, final ViewGroup.LayoutParams params) {
        child.setOnClickListener(this);
        super.addView(child, index, params);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        final long start = System.currentTimeMillis();
        final int totalWidth = MeasureSpec.getSize(widthMeasureSpec);
        int rowHeight = 0;
        for (int c = 0, numChildren = getChildCount(); c < numChildren; c++) {
            final View child = getChildAt(c);
            // Calculate width cells, making sure to cover totalWidth.
            final int l = ((c + 0) * totalWidth) / 7;
            final int r = ((c + 1) * totalWidth) / 7;
            final int cellSize = r - l;
            final int cellWidthSpec = MeasureSpec.makeMeasureSpec(cellSize, MeasureSpec.EXACTLY);
            final int cellHeightSpec = isHeaderRow ? MeasureSpec.makeMeasureSpec(cellSize, MeasureSpec.AT_MOST) : cellWidthSpec;
            child.measure(cellWidthSpec, cellHeightSpec);
            // The row height is the height of the tallest cell.
            if (child.getMeasuredHeight() > rowHeight) {
                rowHeight = child.getMeasuredHeight();
            }
        }
        final int widthWithPadding = totalWidth + getPaddingLeft() + getPaddingRight();
        final int heightWithPadding = rowHeight + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(widthWithPadding, heightWithPadding);
        Logr.d("Row.onMeasure %d ms", System.currentTimeMillis() - start);
    }

    @Override
    protected void onLayout(final boolean changed, final int left, final int top, final int right, final int bottom) {
        final long start = System.currentTimeMillis();
        final int cellHeight = bottom - top;
        final int width = (right - left);
        for (int c = 0, numChildren = getChildCount(); c < numChildren; c++) {
            final View child = getChildAt(c);
            final int l = ((c + 0) * width) / 7;
            final int r = ((c + 1) * width) / 7;
            child.layout(l, 0, r, cellHeight);
        }
        Logr.d("Row.onLayout %d ms", System.currentTimeMillis() - start);
    }

    public void setIsHeaderRow(final boolean isHeaderRow) {
        this.isHeaderRow = isHeaderRow;
    }

    @Override
    public void onClick(final View v) {
        // Header rows don't have a click listener
        if (listener != null) {
            listener.handleClick((MonthCellDescriptor) v.getTag());
        }
    }

    public void setListener(final MonthView.Listener listener) {
        this.listener = listener;
    }

    public void setDayViewAdapter(final DayViewAdapter adapter) {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof CalendarCellView) {
                final CalendarCellView cell = ((CalendarCellView) getChildAt(i));
                cell.removeAllViews();
                adapter.makeCellView(cell);
            }
        }
    }

    public void setCellBackground(final int resId) {
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).setBackgroundResource(resId);
        }
    }

    public void setCellTextColor(final int resId) {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof CalendarCellView) {
                ((CalendarCellView) getChildAt(i)).getDayOfMonthTextView().setTextColor(resId);
            } else {
                ((TextView) getChildAt(i)).setTextColor(resId);
            }
        }
    }

    public void setCellTextColor(final ColorStateList colors) {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof CalendarCellView) {
                ((CalendarCellView) getChildAt(i)).getDayOfMonthTextView().setTextColor(colors);
            } else {
                ((TextView) getChildAt(i)).setTextColor(colors);
            }
        }
    }

    public void setTypeface(final Typeface typeface) {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i) instanceof CalendarCellView) {
                ((CalendarCellView) getChildAt(i)).getDayOfMonthTextView().setTypeface(typeface);
            } else {
                ((TextView) getChildAt(i)).setTypeface(typeface);
            }
        }
    }
}
