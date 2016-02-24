// Copyright 2012 Square, Inc.
package com.squareup.timessquare;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.squareup.timessquare.MonthCellDescriptor.RangeState;

import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Android component to allow picking a date from a calendar view (a list of months).  Must be
 * initialized after inflation with {@link #init(Date, Date)} and can be customized with any of the
 * {@link FluentInitializer} methods returned.  The currently selected date can be retrieved with
 * {@link #getSelectedDate()}.
 */
public class CalendarPickerView extends ListView {
    public enum SelectionMode {
        /**
         * Only one date will be selectable.  If there is already a selected date and you select a new
         * one, the old date will be unselected.
         */
        SINGLE,
        /**
         * Multiple dates will be selectable.  Selecting an already-selected date will un-select it.
         */
        MULTIPLE,
        /**
         * Allows you to select a date range.  Previous selections are cleared when you either:
         * <ul>
         * <li>Have a range selected and select another date (even if it's in the current range).</li>
         * <li>Have one date selected and then select an earlier date.</li>
         * </ul>
         */
        RANGE,
        RANGE_ON_TWO_SCREENS
    }

    private final CalendarPickerView.MonthAdapter adapter;
    private final List<List<List<MonthCellDescriptor>>> cells = new ArrayList<>();
    final MonthView.Listener listener = new CellClickedListener();
    final List<MonthDescriptor> months = new ArrayList<>();
    final List<MonthCellDescriptor> selectedCells = new ArrayList<>();
    final List<MonthCellDescriptor> highlightedCells = new ArrayList<>();
    final List<Calendar> selectedCals = new ArrayList<>();
    final List<Calendar> highlightedCals = new ArrayList<>();
    private Locale locale;
    private DateFormat monthNameFormat;
    private DateFormat weekdayNameFormat;
    private DateFormat fullDateFormat;
    private Calendar minCal;
    private Calendar maxCal;
    private Calendar monthCounter;
    private boolean displayOnly;
    SelectionMode selectionMode;
    Calendar today;
    private final int dividerColor;
    private final int dayBackgroundResId;
    private final int dayTextColorResId;
    private final int titleTextColor;
    private final boolean displayHeader;
    private final int headerTextColor;
    private Typeface titleTypeface;
    private Typeface dateTypeface;

    private OnDateSelectedListener dateListener;
    private DateSelectableFilter dateConfiguredListener;
    private OnInvalidDateSelectedListener invalidDateListener =
            new DefaultOnInvalidDateSelectedListener();
    private CellClickInterceptor cellClickInterceptor;
    private List<CalendarCellDecorator> decorators;
    private DayViewAdapter dayViewAdapter = new DefaultDayViewAdapter();

    private PeakDate peakDate;
    private boolean ignoreValidatingDates;

    public void setDecorators(final List<CalendarCellDecorator> decorators) {
        this.decorators = decorators;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    public List<CalendarCellDecorator> getDecorators() {
        return decorators;
    }

    public CalendarPickerView(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        final Resources res = context.getResources();
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CalendarPickerView);
        final int bg = a.getColor(R.styleable.CalendarPickerView_android_background,
                res.getColor(R.color.calendar_bg));
        dividerColor = a.getColor(R.styleable.CalendarPickerView_tsquare_dividerColor,
                res.getColor(R.color.calendar_divider));
        dayBackgroundResId = a.getResourceId(R.styleable.CalendarPickerView_tsquare_dayBackground,
                R.drawable.calendar_bg_selector);
        dayTextColorResId = a.getResourceId(R.styleable.CalendarPickerView_tsquare_dayTextColor,
                R.color.calendar_text_selector);
        titleTextColor = a.getColor(R.styleable.CalendarPickerView_tsquare_titleTextColor,
                res.getColor(R.color.calendar_text_active));
        displayHeader = a.getBoolean(R.styleable.CalendarPickerView_tsquare_displayHeader, true);
        headerTextColor = a.getColor(R.styleable.CalendarPickerView_tsquare_headerTextColor,
                res.getColor(R.color.calendar_text_active));
        a.recycle();

        adapter = new MonthAdapter();
        setDivider(null);
        setDividerHeight(0);
        setBackgroundColor(bg);
        setCacheColorHint(bg);
        locale = Locale.getDefault();
        today = Calendar.getInstance(locale);
        minCal = Calendar.getInstance(locale);
        maxCal = Calendar.getInstance(locale);
        monthCounter = Calendar.getInstance(locale);
        monthNameFormat = new SimpleDateFormat(context.getString(R.string.month_name_format), locale);
        weekdayNameFormat = new SimpleDateFormat(context.getString(R.string.day_name_format), locale);
        fullDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);

        if (isInEditMode()) {
            final Calendar nextYear = Calendar.getInstance(locale);
            nextYear.add(Calendar.YEAR, 1);

            init(new Date(), nextYear.getTime()) //
                    .withSelectedDate(new Date());
        }
    }

    /**
     * Both date parameters must be non-null and their {@link Date#getTime()} must not return 0. Time
     * of day will be ignored.  For instance, if you pass in {@code minDate} as 11/16/2012 5:15pm and
     * {@code maxDate} as 11/16/2013 4:30am, 11/16/2012 will be the first selectable date and
     * 11/15/2013 will be the last selectable date ({@code maxDate} is exclusive).
     * <p/>
     * This will implicitly set the {@link SelectionMode} to {@link SelectionMode#SINGLE}.  If you
     * want a different selection mode, use {@link FluentInitializer#inMode(SelectionMode)} on the
     * {@link FluentInitializer} this method returns.
     * <p/>
     * The calendar will be constructed using the given locale. This means that all names
     * (months, days) will be in the language of the locale and the weeks start with the day
     * specified by the locale.
     *
     * @param minDate Earliest selectable date, inclusive.  Must be earlier than {@code maxDate}.
     * @param maxDate Latest selectable date, exclusive.  Must be later than {@code minDate}.
     */
    public FluentInitializer init(final Date minDate, final Date maxDate, final Locale locale) {
        if (minDate == null || maxDate == null) {
            throw new IllegalArgumentException(
                    "minDate and maxDate must be non-null.  " + dbg(minDate, maxDate));
        }
        if (minDate.after(maxDate)) {
            throw new IllegalArgumentException(
                    "minDate must be before maxDate.  " + dbg(minDate, maxDate));
        }
        if (locale == null) {
            throw new IllegalArgumentException("Locale is null.");
        }

        // Make sure that all calendar instances use the same locale.
        this.locale = locale;
        today = Calendar.getInstance(locale);
        minCal = Calendar.getInstance(locale);
        maxCal = Calendar.getInstance(locale);
        monthCounter = Calendar.getInstance(locale);
        monthNameFormat =
                new SimpleDateFormat(getContext().getString(R.string.month_name_format), locale);
        for (final MonthDescriptor month : months) {
            month.setLabel(monthNameFormat.format(month.getDate()));
        }
        weekdayNameFormat =
                new SimpleDateFormat(getContext().getString(R.string.day_name_format), locale);
        fullDateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);

        this.selectionMode = SelectionMode.SINGLE;
        // Clear out any previously-selected dates/cells.
        selectedCals.clear();
        selectedCells.clear();
        highlightedCals.clear();
        highlightedCells.clear();

        peakDate = null;
        ignoreValidatingDates = false;

        // Clear previous state.
        cells.clear();
        months.clear();
        minCal.setTime(minDate);
        maxCal.setTime(maxDate);
        setMidnight(minCal);
        setMidnight(maxCal);
        displayOnly = false;

        // maxDate is exclusive: bump back to the previous day so if maxDate is the first of a month,
        // we don't accidentally include that month in the view.
        maxCal.add(Calendar.MINUTE, -1);

        // Now iterate between minCal and maxCal and build up our list of months to show.
        monthCounter.setTime(minCal.getTime());
        final int maxMonth = maxCal.get(Calendar.MONTH);
        final int maxYear = maxCal.get(Calendar.YEAR);
        while ((monthCounter.get(Calendar.MONTH) <= maxMonth // Up to, including the month.
                || monthCounter.get(Calendar.YEAR) < maxYear) // Up to the year.
                && monthCounter.get(Calendar.YEAR) < maxYear + 1) { // But not > next yr.
            final Date date = monthCounter.getTime();
            final MonthDescriptor month =
                    new MonthDescriptor(monthCounter.get(Calendar.MONTH), monthCounter.get(Calendar.YEAR), date,
                            monthNameFormat.format(date));
            cells.add(getMonthCells(month, monthCounter));
            Logr.d("Adding month %s", month);
            months.add(month);
            monthCounter.add(Calendar.MONTH, 1);
        }

        validateAndUpdate();
        return new FluentInitializer();
    }

    /**
     * Both date parameters must be non-null and their {@link Date#getTime()} must not return 0. Time
     * of day will be ignored.  For instance, if you pass in {@code minDate} as 11/16/2012 5:15pm and
     * {@code maxDate} as 11/16/2013 4:30am, 11/16/2012 will be the first selectable date and
     * 11/15/2013 will be the last selectable date ({@code maxDate} is exclusive).
     * <p/>
     * This will implicitly set the {@link SelectionMode} to {@link SelectionMode#SINGLE}.  If you
     * want a different selection mode, use {@link FluentInitializer#inMode(SelectionMode)} on the
     * {@link FluentInitializer} this method returns.
     * <p/>
     * The calendar will be constructed using the default locale as returned by
     * {@link java.util.Locale#getDefault()}. If you wish the calendar to be constructed using a
     * different locale, use {@link #init(java.util.Date, java.util.Date, java.util.Locale)}.
     *
     * @param minDate Earliest selectable date, inclusive.  Must be earlier than {@code maxDate}.
     * @param maxDate Latest selectable date, exclusive.  Must be later than {@code minDate}.
     */
    public FluentInitializer init(final Date minDate, final Date maxDate) {
        return init(minDate, maxDate, Locale.getDefault());
    }

    public class FluentInitializer {
        /**
         * Override the {@link SelectionMode} from the default ({@link SelectionMode#SINGLE}).
         */
        public FluentInitializer inMode(final SelectionMode mode) {
            selectionMode = mode;
            validateAndUpdate();
            return this;
        }

        /**
         * Set an initially-selected date.  The calendar will scroll to that date if it's not already
         * visible.
         */
        public FluentInitializer withSelectedDate(final Date selectedDates) {
            return withSelectedDates(Collections.singletonList(selectedDates));
        }

        /**
         * Set multiple selected dates.  This will throw an {@link IllegalArgumentException} if you
         * pass in multiple dates and haven't already called {@link #inMode(SelectionMode)}.
         */
        public FluentInitializer withSelectedDates(final Collection<Date> selectedDates) {
            if (selectionMode == SelectionMode.SINGLE && selectedDates.size() > 1) {
                throw new IllegalArgumentException("SINGLE mode can't be used with multiple selectedDates");
            }
            if (selectionMode == SelectionMode.RANGE && selectedDates.size() > 2) {
                throw new IllegalArgumentException(
                        "RANGE mode only allows two selectedDates.  You tried to pass " + selectedDates.size());
            }
            if (selectedDates != null) {
                for (final Date date : selectedDates) {
                    selectDate(date);
                }
            }
            scrollToSelectedDates();

            validateAndUpdate();
            return this;
        }

        public FluentInitializer withHighlightedDates(final Collection<Date> dates) {
            highlightDates(dates);
            return this;
        }

        public FluentInitializer withHighlightedDate(final Date date) {
            return withHighlightedDates(Collections.singletonList(date));
        }

        @SuppressLint("SimpleDateFormat")
        public FluentInitializer setShortWeekdays(final String... newShortWeekdays) {
            final DateFormatSymbols symbols = new DateFormatSymbols(locale);
            symbols.setShortWeekdays(newShortWeekdays);
            weekdayNameFormat =
                    new SimpleDateFormat(getContext().getString(R.string.day_name_format), symbols);
            return this;
        }

        public FluentInitializer displayOnly() {
            displayOnly = true;
            return this;
        }

        public FluentInitializer setPeakDate(final PeakDate peakDate) {
            CalendarPickerView.this.peakDate = peakDate;
            return this;
        }

        public FluentInitializer ignoreValidatingDates(final boolean ignoreValidatingDates) {
            CalendarPickerView.this.ignoreValidatingDates = ignoreValidatingDates;
            return this;
        }

    }

    private void validateAndUpdate() {
        if (getAdapter() == null) {
            setAdapter(adapter);
        }
        adapter.notifyDataSetChanged();
    }

    private void scrollToSelectedMonth(final int selectedIndex) {
        scrollToSelectedMonth(selectedIndex, false);
    }

    private void scrollToSelectedMonth(final int selectedIndex, final boolean smoothScroll) {
        post(new Runnable() {
            @Override
            public void run() {
                Logr.d("Scrolling to position %d", selectedIndex);

                if (smoothScroll) {
                    smoothScrollToPosition(selectedIndex);
                } else {
                    setSelection(selectedIndex);
                }
            }
        });
    }

    private void scrollToSelectedDates() {
        Integer selectedIndex = null;
        Integer todayIndex = null;
        final Calendar today = Calendar.getInstance(locale);
        for (int c = 0; c < months.size(); c++) {
            final MonthDescriptor month = months.get(c);
            if (selectedIndex == null) {
                for (final Calendar selectedCal : selectedCals) {
                    if (sameMonth(selectedCal, month)) {
                        selectedIndex = c;
                        break;
                    }
                }
                if (selectedIndex == null && todayIndex == null && sameMonth(today, month)) {
                    todayIndex = c;
                }
            }
        }
        if (selectedIndex != null) {
            scrollToSelectedMonth(selectedIndex);
        } else if (todayIndex != null) {
            scrollToSelectedMonth(todayIndex);
        }
    }

    public boolean scrollToDate(final Date date) {
        Integer selectedIndex = null;

        final Calendar cal = Calendar.getInstance(locale);
        cal.setTime(date);
        for (int c = 0; c < months.size(); c++) {
            final MonthDescriptor month = months.get(c);
            if (sameMonth(cal, month)) {
                selectedIndex = c;
                break;
            }
        }
        if (selectedIndex != null) {
            scrollToSelectedMonth(selectedIndex);
            return true;
        }
        return false;
    }

    /**
     * This method should only be called if the calendar is contained in a dialog, and it should only
     * be called once, right after the dialog is shown (using
     * {@link android.content.DialogInterface.OnShowListener} or
     * {@link android.app.DialogFragment#onStart()}).
     */
    public void fixDialogDimens() {
        Logr.d("Fixing dimensions to h = %d / w = %d", getMeasuredHeight(), getMeasuredWidth());
        // Fix the layout height/width after the dialog has been shown.
        getLayoutParams().height = getMeasuredHeight();
        getLayoutParams().width = getMeasuredWidth();
        // Post this runnable so it runs _after_ the dimen changes have been applied/re-measured.
        post(new Runnable() {
            @Override
            public void run() {
                Logr.d("Dimens are fixed: now scroll to the selected date");
                scrollToSelectedDates();
            }
        });
    }

    /**
     * Set the typeface to be used for month titles.
     */
    public void setTitleTypeface(final Typeface titleTypeface) {
        this.titleTypeface = titleTypeface;
        validateAndUpdate();
    }

    /**
     * Sets the typeface to be used within the date grid.
     */
    public void setDateTypeface(final Typeface dateTypeface) {
        this.dateTypeface = dateTypeface;
        validateAndUpdate();
    }

    /**
     * Sets the typeface to be used for all text within this calendar.
     */
    public void setTypeface(final Typeface typeface) {
        setTitleTypeface(typeface);
        setDateTypeface(typeface);
    }

    /**
     * This method should only be called if the calendar is contained in a dialog, and it should only
     * be called when the screen has been rotated and the dialog should be re-measured.
     */
    public void unfixDialogDimens() {
        Logr.d("Reset the fixed dimensions to allow for re-measurement");
        // Fix the layout height/width after the dialog has been shown.
        getLayoutParams().height = LayoutParams.MATCH_PARENT;
        getLayoutParams().width = LayoutParams.MATCH_PARENT;
        requestLayout();
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        if (months.isEmpty()) {
            throw new IllegalStateException(
                    "Must have at least one month to display.  Did you forget to call init()?");
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public Date getSelectedDate() {
        return (!selectedCals.isEmpty() ? selectedCals.get(0).getTime() : null);
    }

    public List<Date> getSelectedDates() {
        final List<Date> selectedDates = new ArrayList<>();
        for (final MonthCellDescriptor cal : selectedCells) {
            selectedDates.add(cal.getDate());
        }
        Collections.sort(selectedDates);
        return selectedDates;
    }

    /**
     * Returns a string summarizing what the client sent us for init() params.
     */
    private static String dbg(final Date minDate, final Date maxDate) {
        return "minDate: " + minDate + "\nmaxDate: " + maxDate;
    }

    /**
     * Clears out the hours/minutes/seconds/millis of a Calendar.
     */
    static void setMidnight(final Calendar cal) {
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
    }

    private class CellClickedListener implements MonthView.Listener {
        @Override
        public void handleClick(final MonthCellDescriptor cell) {
            final Date clickedDate = cell.getDate();

            if (cellClickInterceptor != null && cellClickInterceptor.onCellClicked(clickedDate)) {
                return;
            }
            if (!betweenDates(clickedDate, minCal, maxCal) || !isDateSelectable(clickedDate)) {
                if (invalidDateListener != null) {
                    invalidDateListener.onInvalidDateSelected(clickedDate);
                }
            } else {
                final boolean wasSelected = doSelectDate(clickedDate, cell);

                if (dateListener != null) {
                    if (wasSelected) {
                        dateListener.onDateSelected(clickedDate);
                    } else {
                        dateListener.onDateUnselected(clickedDate);
                    }
                }
            }
        }
    }

    /**
     * Select a new date.  Respects the {@link SelectionMode} this CalendarPickerView is configured
     * with: if you are in {@link SelectionMode#SINGLE}, the previously selected date will be
     * un-selected.  In {@link SelectionMode#MULTIPLE}, the new date will be added to the list of
     * selected dates.
     * <p/>
     * If the selection was made (selectable date, in range), the view will scroll to the newly
     * selected date if it's not already visible.
     *
     * @return - whether we were able to set the date
     */
    public boolean selectDate(final Date date) {
        return selectDate(date, false);
    }

    /**
     * Select a new date.  Respects the {@link SelectionMode} this CalendarPickerView is configured
     * with: if you are in {@link SelectionMode#SINGLE}, the previously selected date will be
     * un-selected.  In {@link SelectionMode#MULTIPLE}, the new date will be added to the list of
     * selected dates.
     * <p/>
     * If the selection was made (selectable date, in range), the view will scroll to the newly
     * selected date if it's not already visible.
     *
     * @return - whether we were able to set the date
     */
    public boolean selectDate(final Date date, final boolean smoothScroll) {
        validateDate(date);

        final MonthCellWithMonthIndex monthCellWithMonthIndex = getMonthCellWithIndexByDate(date);
        if (monthCellWithMonthIndex == null || !isDateSelectable(date)) {
            return false;
        }
        final boolean wasSelected = doSelectDate(date, monthCellWithMonthIndex.cell);
        if (wasSelected) {
            scrollToSelectedMonth(monthCellWithMonthIndex.monthIndex, smoothScroll);
        }
        return wasSelected;
    }

    private void validateDate(final Date date) {
        if (date == null) {
            throw new IllegalArgumentException("Selected date must be non-null.");
        }
        if (ignoreValidatingDates) {
            return;
        }
        if (date.before(minCal.getTime()) || date.after(maxCal.getTime())) {
            throw new IllegalArgumentException(String.format(
                    "SelectedDate must be between minDate and maxDate."
                            + "%nminDate: %s%nmaxDate: %s%nselectedDate: %s", minCal.getTime(), maxCal.getTime(),
                    date));
        }
    }

    private boolean doSelectDate(Date date, final MonthCellDescriptor cell) {
        final Calendar newlySelectedCal = Calendar.getInstance(locale);
        newlySelectedCal.setTime(date);
        // Sanitize input: clear out the hours/minutes/seconds/millis.
        setMidnight(newlySelectedCal);

        // Clear any remaining range state.
        for (final MonthCellDescriptor selectedCell : selectedCells) {
            selectedCell.setRangeState(RangeState.NONE);
        }

        switch (selectionMode) {
            case RANGE_ON_TWO_SCREENS:
                clearOldSelections();
                break;
            case RANGE:
                if (selectedCals.size() > 1) {
                    // We've already got a range selected: clear the old one.
                    clearOldSelections();
                } else if (selectedCals.size() == 1 && newlySelectedCal.before(selectedCals.get(0))) {
                    // We're moving the start of the range back in time: clear the old start date.
                    clearOldSelections();
                }
                break;

            case MULTIPLE:
                date = applyMultiSelect(date, newlySelectedCal);
                break;

            case SINGLE:
                clearOldSelections();
                break;
            default:
                throw new IllegalStateException("Unknown selectionMode " + selectionMode);
        }

        if (date != null) {
            // Select a new cell.
            if (selectedCells.isEmpty() || !selectedCells.get(0).equals(cell)) {
                selectedCells.add(cell);
                cell.setSelected(true);
            }
            selectedCals.add(newlySelectedCal);

            if (selectionMode == SelectionMode.RANGE && selectedCells.size() > 1) {
                // Select all days in between start and end.
                final Date start = selectedCells.get(0).getDate();
                final Date end = selectedCells.get(1).getDate();
                selectedCells.get(0).setRangeState(MonthCellDescriptor.RangeState.FIRST);
                selectedCells.get(1).setRangeState(MonthCellDescriptor.RangeState.LAST);

                for (final List<List<MonthCellDescriptor>> month : cells) {
                    for (final List<MonthCellDescriptor> week : month) {
                        for (final MonthCellDescriptor singleCell : week) {
                            if (singleCell.getDate().after(start)
                                    && singleCell.getDate().before(end)
                                    && singleCell.isSelectable()) {
                                singleCell.setSelected(true);
                                singleCell.setRangeState(MonthCellDescriptor.RangeState.MIDDLE);
                                selectedCells.add(singleCell);
                            }
                        }
                    }
                }
            }

            //TODO do not enter this statement if peakDate equals selected Date
            if (selectionMode == SelectionMode.RANGE_ON_TWO_SCREENS && peakDate != null) {
                // Select all days in between start and end.

                if (peakDate instanceof LowerPeakDate) {
                    final MonthCellWithMonthIndex monthCellWithMonthIndex = getMonthCellWithIndexByDate(peakDate.getPeakDate());
                    if (monthCellWithMonthIndex == null) {
                        return false;
                    }
                    selectedCells.add(0, monthCellWithMonthIndex.cell);
                } else if (peakDate instanceof HigherPeakDate) {
                    final MonthCellWithMonthIndex monthCellWithMonthIndex = getMonthCellWithIndexByDate(peakDate.getPeakDate());
                    if (monthCellWithMonthIndex == null) {
                        return false;
                    }
                    selectedCells.add(1, monthCellWithMonthIndex.cell);
                } else {
                    return false;
                }

                final Date start = selectedCells.get(0).getDate();
                final Date end = selectedCells.get(1).getDate();

                selectedCells.get(0).setRangeState(MonthCellDescriptor.RangeState.FIRST);
                selectedCells.get(1).setRangeState(MonthCellDescriptor.RangeState.LAST);

                for (final List<List<MonthCellDescriptor>> month : cells) {
                    for (final List<MonthCellDescriptor> week : month) {
                        for (final MonthCellDescriptor singleCell : week) {
                            if (singleCell.getDate().after(start)
                                    && singleCell.getDate().before(end)
                                    && singleCell.isSelectable()) {
                                singleCell.setSelected(true);
                                singleCell.setRangeState(MonthCellDescriptor.RangeState.MIDDLE);
                                selectedCells.add(singleCell);
                            }
                        }
                    }
                }
            }
        }

        // Update the adapter.
        validateAndUpdate();
        return date != null;
    }

    private void clearOldSelections() {
        for (final MonthCellDescriptor selectedCell : selectedCells) {
            // De-select the currently-selected cell.
            selectedCell.setSelected(false);

            if (dateListener != null) {
                final Date selectedDate = selectedCell.getDate();

                if (selectionMode == SelectionMode.RANGE) {
                    final int index = selectedCells.indexOf(selectedCell);
                    if (index == 0 || index == selectedCells.size() - 1) {
                        dateListener.onDateUnselected(selectedDate);
                    }
                } else {
                    dateListener.onDateUnselected(selectedDate);
                }
            }
        }
        selectedCells.clear();
        selectedCals.clear();
    }

    private Date applyMultiSelect(Date date, final Calendar selectedCal) {
        for (final MonthCellDescriptor selectedCell : selectedCells) {
            if (selectedCell.getDate().equals(date)) {
                // De-select the currently-selected cell.
                selectedCell.setSelected(false);
                selectedCells.remove(selectedCell);
                date = null;
                break;
            }
        }
        for (final Calendar cal : selectedCals) {
            if (sameDate(cal, selectedCal)) {
                selectedCals.remove(cal);
                break;
            }
        }
        return date;
    }

    public void highlightDate(final Date date) {
        highlightDates(Collections.singletonList(date));
    }

    public void highlightDates(final Collection<Date> dates) {
        for (final Date date : dates) {
            validateDate(date);

            final MonthCellWithMonthIndex monthCellWithMonthIndex = getMonthCellWithIndexByDate(date);
            if (monthCellWithMonthIndex != null) {
                final Calendar newlyHighlightedCal = Calendar.getInstance();
                newlyHighlightedCal.setTime(date);
                final MonthCellDescriptor cell = monthCellWithMonthIndex.cell;

                highlightedCells.add(cell);
                highlightedCals.add(newlyHighlightedCal);
                cell.setHighlighted(true);
            }
        }

        validateAndUpdate();
    }

    public void clearHighlightedDates() {
        for (final MonthCellDescriptor cal : highlightedCells) {
            cal.setHighlighted(false);
        }
        highlightedCells.clear();
        highlightedCals.clear();

        validateAndUpdate();
    }

    /**
     * Hold a cell with a month-index.
     */
    private static class MonthCellWithMonthIndex {
        public MonthCellDescriptor cell;
        public int monthIndex;

        public MonthCellWithMonthIndex(final MonthCellDescriptor cell, final int monthIndex) {
            this.cell = cell;
            this.monthIndex = monthIndex;
        }
    }

    /**
     * Return cell and month-index (for scrolling) for a given Date.
     */
    private MonthCellWithMonthIndex getMonthCellWithIndexByDate(final Date date) {
        int index = 0;
        final Calendar searchCal = Calendar.getInstance(locale);
        searchCal.setTime(date);
        final Calendar actCal = Calendar.getInstance(locale);

        for (final List<List<MonthCellDescriptor>> monthCells : cells) {
            for (final List<MonthCellDescriptor> weekCells : monthCells) {
                for (final MonthCellDescriptor actCell : weekCells) {
                    actCal.setTime(actCell.getDate());
                    if (sameDate(actCal, searchCal) && actCell.isSelectable()) {
                        return new MonthCellWithMonthIndex(actCell, index);
                    }
                }
            }
            index++;
        }
        return null;
    }

    private class MonthAdapter extends BaseAdapter {
        private final LayoutInflater inflater;

        private MonthAdapter() {
            inflater = LayoutInflater.from(getContext());
        }

        @Override
        public boolean isEnabled(final int position) {
            // Disable selectability: each cell will handle that itself.
            return false;
        }

        @Override
        public int getCount() {
            return months.size();
        }

        @Override
        public Object getItem(final int position) {
            return months.get(position);
        }

        @Override
        public long getItemId(final int position) {
            return position;
        }

        @Override
        public View getView(final int position, final View convertView, final ViewGroup parent) {
            MonthView monthView = (MonthView) convertView;
            if (monthView == null
                    || !monthView.getTag(R.id.day_view_adapter_class).equals(dayViewAdapter.getClass())) {
                monthView =
                        MonthView.create(parent, inflater, weekdayNameFormat, listener, today, dividerColor,
                                dayBackgroundResId, dayTextColorResId, titleTextColor, displayHeader,
                                headerTextColor, decorators, locale, dayViewAdapter);
                monthView.setTag(R.id.day_view_adapter_class, dayViewAdapter.getClass());
            } else {
                monthView.setDecorators(decorators);
            }
            monthView.init(months.get(position), cells.get(position), displayOnly, titleTypeface,
                    dateTypeface);
            return monthView;
        }
    }

    List<List<MonthCellDescriptor>> getMonthCells(final MonthDescriptor month, final Calendar startCal) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.setTime(startCal.getTime());
        final List<List<MonthCellDescriptor>> cells = new ArrayList<>();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        final int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int offset = cal.getFirstDayOfWeek() - firstDayOfWeek;
        if (offset > 0) {
            offset -= 7;
        }
        cal.add(Calendar.DATE, offset);

        final Calendar minSelectedCal = minDate(selectedCals);
        final Calendar maxSelectedCal = maxDate(selectedCals);

        while ((cal.get(Calendar.MONTH) < month.getMonth() + 1 || cal.get(Calendar.YEAR) < month.getYear()) //
                && cal.get(Calendar.YEAR) <= month.getYear()) {
            Logr.d("Building week row starting at %s", cal.getTime());
            final List<MonthCellDescriptor> weekCells = new ArrayList<>();
            cells.add(weekCells);
            for (int c = 0; c < 7; c++) {
                final Date date = cal.getTime();
                final boolean isCurrentMonth = cal.get(Calendar.MONTH) == month.getMonth();
                final boolean isSelected = isCurrentMonth && containsDate(selectedCals, cal);
                final boolean isSelectable =
                        isCurrentMonth && betweenDates(cal, minCal, maxCal) && isDateSelectable(date);
                final boolean isToday = sameDate(cal, today);
                final boolean isHighlighted = containsDate(highlightedCals, cal);
                final int value = cal.get(Calendar.DAY_OF_MONTH);

                MonthCellDescriptor.RangeState rangeState = MonthCellDescriptor.RangeState.NONE;
                if (selectedCals.size() > 1) {
                    if (sameDate(minSelectedCal, cal)) {
                        rangeState = MonthCellDescriptor.RangeState.FIRST;
                    } else if (sameDate(maxDate(selectedCals), cal)) {
                        rangeState = MonthCellDescriptor.RangeState.LAST;
                    } else if (betweenDates(cal, minSelectedCal, maxSelectedCal)) {
                        rangeState = MonthCellDescriptor.RangeState.MIDDLE;
                    }
                }

                weekCells.add(
                        new MonthCellDescriptor(date, isCurrentMonth, isSelectable, isSelected, isToday,
                                isHighlighted, value, rangeState));
                cal.add(Calendar.DATE, 1);
            }
        }
        return cells;
    }

    private boolean containsDate(final List<Calendar> selectedCals, final Date date) {
        final Calendar cal = Calendar.getInstance(locale);
        cal.setTime(date);
        return containsDate(selectedCals, cal);
    }

    private static boolean containsDate(final List<Calendar> selectedCals, final Calendar cal) {
        for (final Calendar selectedCal : selectedCals) {
            if (sameDate(cal, selectedCal)) {
                return true;
            }
        }
        return false;
    }

    private static Calendar minDate(final List<Calendar> selectedCals) {
        if (selectedCals == null || selectedCals.isEmpty()) {
            return null;
        }
        Collections.sort(selectedCals);
        return selectedCals.get(0);
    }

    private static Calendar maxDate(final List<Calendar> selectedCals) {
        if (selectedCals == null || selectedCals.isEmpty()) {
            return null;
        }
        Collections.sort(selectedCals);
        return selectedCals.get(selectedCals.size() - 1);
    }

    private static boolean sameDate(final Calendar cal, final Calendar selectedDate) {
        return cal.get(Calendar.MONTH) == selectedDate.get(Calendar.MONTH)
                && cal.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR)
                && cal.get(Calendar.DAY_OF_MONTH) == selectedDate.get(Calendar.DAY_OF_MONTH);
    }

    private static boolean betweenDates(final Calendar cal, final Calendar minCal, final Calendar maxCal) {
        final Date date = cal.getTime();
        return betweenDates(date, minCal, maxCal);
    }

    static boolean betweenDates(final Date date, final Calendar minCal, final Calendar maxCal) {
        final Date min = minCal.getTime();
        return (date.equals(min) || date.after(min)) // >= minCal
                && date.before(maxCal.getTime()); // && < maxCal
    }

    private static boolean sameMonth(final Calendar cal, final MonthDescriptor month) {
        return (cal.get(Calendar.MONTH) == month.getMonth() && cal.get(Calendar.YEAR) == month.getYear());
    }

    private boolean isDateSelectable(final Date date) {
        return dateConfiguredListener == null || dateConfiguredListener.isDateSelectable(date);
    }

    public void setOnDateSelectedListener(final OnDateSelectedListener listener) {
        dateListener = listener;
    }

    /**
     * Set a listener to react to user selection of a disabled date.
     *
     * @param listener the listener to set, or null for no reaction
     */
    public void setOnInvalidDateSelectedListener(final OnInvalidDateSelectedListener listener) {
        invalidDateListener = listener;
    }

    /**
     * Set a listener used to discriminate between selectable and unselectable dates. Set this to
     * disable arbitrary dates as they are rendered.
     * <p/>
     * Important: set this before you call {@link #init(Date, Date)} methods.  If called afterwards,
     * it will not be consistently applied.
     */
    public void setDateSelectableFilter(final DateSelectableFilter listener) {
        dateConfiguredListener = listener;
    }


    /**
     * Set an adapter used to initialize {@link CalendarCellView} with custom layout.
     * <p/>
     * Important: set this before you call {@link #init(Date, Date)} methods.  If called afterwards,
     * it will not be consistently applied.
     */
    public void setCustomDayView(final DayViewAdapter dayViewAdapter) {
        this.dayViewAdapter = dayViewAdapter;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * Set a listener to intercept clicks on calendar cells.
     */
    public void setCellClickInterceptor(final CellClickInterceptor listener) {
        cellClickInterceptor = listener;
    }

    /**
     * Interface to be notified when a new date is selected or unselected. This will only be called
     * when the user initiates the date selection.  If you call {@link #selectDate(Date)} this
     * listener will not be notified.
     *
     * @see #setOnDateSelectedListener(OnDateSelectedListener)
     */
    public interface OnDateSelectedListener {
        void onDateSelected(Date date);

        void onDateUnselected(Date date);
    }

    /**
     * Interface to be notified when an invalid date is selected by the user. This will only be
     * called when the user initiates the date selection. If you call {@link #selectDate(Date)} this
     * listener will not be notified.
     *
     * @see #setOnInvalidDateSelectedListener(OnInvalidDateSelectedListener)
     */
    public interface OnInvalidDateSelectedListener {
        void onInvalidDateSelected(Date date);
    }

    /**
     * Interface used for determining the selectability of a date cell when it is configured for
     * display on the calendar.
     *
     * @see #setDateSelectableFilter(DateSelectableFilter)
     */
    public interface DateSelectableFilter {
        boolean isDateSelectable(Date date);
    }

    /**
     * Interface to be notified when a cell is clicked and possibly intercept the click.  Return true
     * to intercept the click and prevent any selections from changing.
     *
     * @see #setCellClickInterceptor(CellClickInterceptor)
     */
    public interface CellClickInterceptor {
        boolean onCellClicked(Date date);
    }

    private class DefaultOnInvalidDateSelectedListener implements OnInvalidDateSelectedListener {
        @Override
        public void onInvalidDateSelected(final Date date) {
            if (ignoreValidatingDates) {
                return;
            }
            final String errMessage =
                    getResources().getString(R.string.invalid_date, fullDateFormat.format(minCal.getTime()),
                            fullDateFormat.format(maxCal.getTime()));
            Toast.makeText(getContext(), errMessage, Toast.LENGTH_SHORT).show();
        }
    }
}
