package com.squareup.timessquare;

import java.util.Date;

/**
 * Created by Ilia Kurtov on 19.02.2016.
 */
public class LowerPeakDate implements PeakDate {

    private Date lowerPeakDate;

    public LowerPeakDate(final Date lowerPeakDate) {
        this.lowerPeakDate = lowerPeakDate;
    }

    @Override
    public Date getPeakDate() {
        return lowerPeakDate;
    }

}
