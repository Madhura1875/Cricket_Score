package com.cricket.scoreboard.util;

public final class Fmt {

    private Fmt() {
    }

    /** 15 legal balls -> "2.3" */
    public static String overs(int legalBalls) {
        return (legalBalls / 6) + "." + (legalBalls % 6);
    }

    public static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
