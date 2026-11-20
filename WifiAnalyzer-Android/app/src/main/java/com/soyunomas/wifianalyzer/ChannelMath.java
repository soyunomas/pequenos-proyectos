package com.soyunomas.wifianalyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pure calculations, independent of Android services: suitable for JVM unit tests. */
public final class ChannelMath {
    private ChannelMath() {}

    public static final int BAND_ALL = 0;
    public static final int BAND_24 = 24;
    public static final int BAND_5 = 5;
    public static final int BAND_6 = 6;

    public static int band(int mhz) {
        if (mhz >= 5925 && mhz <= 7125) return BAND_6;
        if (mhz >= 4900 && mhz < 5925) return BAND_5;
        if (mhz >= 2400 && mhz < 2500) return BAND_24;
        return BAND_ALL;
    }

    public static int channel(int mhz) {
        if (mhz == 2484) return 14;
        if (mhz == 5935) return 2;
        int band = band(mhz);
        if (band == BAND_24) return (mhz - 2407) / 5;
        if (band == BAND_5) return (mhz - 5000) / 5;
        if (band == BAND_6) return (mhz - 5950) / 5;
        return 0;
    }

    public static int frequency(int band, int channel) {
        if (band == BAND_24) return channel == 14 ? 2484 : 2407 + channel * 5;
        if (band == BAND_5) return 5000 + channel * 5;
        if (band == BAND_6) return channel == 2 ? 5935 : 5950 + channel * 5;
        return 0;
    }

    public static String bandLabel(int band) {
        if (band == BAND_24) return "2,4 GHz";
        if (band == BAND_5) return "5 GHz";
        if (band == BAND_6) return "6 GHz";
        return "Desconocida";
    }

    public static int quality(int dbm) {
        return Math.max(0, Math.min(100, 2 * (dbm + 100)));
    }

    public static String qualityLabel(int dbm) {
        if (dbm >= -55) return "Excelente";
        if (dbm >= -67) return "Buena";
        if (dbm >= -75) return "Regular";
        return "Débil";
    }

    public static final class Observation {
        public final int mhz;
        public final int dbm;
        public final int width;
        public Observation(int mhz, int dbm, int width) {
            this.mhz = mhz;
            this.dbm = dbm;
            this.width = Math.max(20, width);
        }
    }

    public static final class Recommendation {
        public final int channel;
        public final int frequency;
        public final int score;
        public final int conflicts;
        Recommendation(int channel, int frequency, int score, int conflicts) {
            this.channel = channel;
            this.frequency = frequency;
            this.score = score;
            this.conflicts = conflicts;
        }
    }

    private static int[] candidates(int band) {
        if (band == BAND_24) return new int[]{1, 6, 11};
        if (band == BAND_5) return new int[]{36, 44, 52, 60, 100, 108, 116, 124, 132, 140, 149, 157};
        if (band == BAND_6) return new int[]{5, 21, 37, 53, 69, 85, 101, 117, 133, 149, 165, 181, 197, 213, 229};
        return new int[0];
    }

    /** Heuristic overlap scoring, not a prediction of achievable throughput. */
    public static List<Recommendation> recommendations(List<Observation> observations, int band) {
        List<Recommendation> ratings = new ArrayList<>();
        for (int channel : candidates(band)) {
            int mhz = frequency(band, channel);
            double interference = 0;
            int conflicts = 0;
            for (Observation n : observations) {
                if (band(n.mhz) != band) continue;
                double halfWidth = Math.max(20, n.width) / 2.0 + 10.0;
                double proximity = Math.max(0, 1 - Math.abs(n.mhz - mhz) / halfWidth);
                if (proximity <= 0) continue;
                conflicts++;
                // Strong networks contribute more, while remote beacons barely affect the score.
                double power = Math.pow(10, (Math.max(-100, Math.min(-30, n.dbm)) + 90) / 25.0);
                interference += proximity * power;
            }
            int score = Math.max(0, Math.min(100, (int)Math.round(100 / (1 + interference * 0.22))));
            ratings.add(new Recommendation(channel, mhz, score, conflicts));
        }
        Collections.sort(ratings, Comparator.comparingInt((Recommendation r) -> r.score)
                .reversed().thenComparingInt(r -> r.conflicts).thenComparingInt(r -> r.channel));
        return ratings;
    }
}
