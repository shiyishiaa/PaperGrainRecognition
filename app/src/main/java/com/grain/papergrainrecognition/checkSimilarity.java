package com.grain.papergrainrecognition;

public class checkSimilarity {
    private static final Double initial = 7.0, target = 10.0, lowest = 3.0;
    private static final Double errorWeight = -3.0, ambiguousWeight = 0.0, rightWeight = 0.7;
    private static final Double LowerBound = 0.5, UpperBound = 0.8;
    private static final Integer checkTimes = 10;

    public synchronized boolean isSimilar(String file_1, String file_2) {
        double ruler = initial;
        for (int i = 0; i < checkTimes; i++) {

            Double d = matchPicture.match(file_1, file_2);
            if (d < LowerBound)
                ruler += errorWeight;
            else if (d > UpperBound)
                ruler += rightWeight;
            else
                ruler += ambiguousWeight;

            if (ruler >= target)
                return true;
            else if (ruler <= lowest)
                return false;
        }
        return false;
    }


}
