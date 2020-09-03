package com.grain.grain.matching;

public class checkSimilarity extends Thread {
    private static final Double initial = 7.0, target = 10.0, lowest = 3.0;
    private static final Double errorWeight = -3.0, ambiguousWeight = 0.0, rightWeight = 0.7;
    private static final Double LowerBound = 0.5, UpperBound = 0.8;
    private static final Integer checkTimes = 10;
    private String file_1, file_2;

    checkSimilarity(String str_1, String str_2) {
        this.file_1 = str_1;
        this.file_2 = str_2;
    }

    public synchronized boolean isSimilar() {
        double ruler = initial;
        for (int i = 0; i < checkTimes; i++) {
            MatchUtils matchUtils = new MatchUtils(file_1, file_2);

//            if (d < LowerBound)
//                ruler += errorWeight;
//            else if (d > UpperBound)
//                ruler += rightWeight;
//            else
//                ruler += ambiguousWeight;

            if (ruler >= target)
                return true;
            else if (ruler <= lowest)
                return false;
        }
        return false;
    }


}
