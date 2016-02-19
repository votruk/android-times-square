package com.squareup.timessquare;

import java.util.Date;

/**
 * Created by Ilia Kurtov on 19.02.2016.
 */
public class HigherPeakDate implements PeakDate {

    private Date higherPeakDate;

    public HigherPeakDate(final Date higherPeakDate) {
        this.higherPeakDate = higherPeakDate;
    }

    @Override
    public Date getPeakDate() {
        return higherPeakDate;
    }
}
