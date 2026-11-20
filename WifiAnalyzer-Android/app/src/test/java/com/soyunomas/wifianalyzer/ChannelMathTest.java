package com.soyunomas.wifianalyzer;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class ChannelMathTest {
    @Test public void frequenciesHaveCorrectBandsAndChannels() {
        assertEquals(1, ChannelMath.channel(2412));
        assertEquals(11, ChannelMath.channel(2462));
        assertEquals(14, ChannelMath.channel(2484));
        assertEquals(36, ChannelMath.channel(5180));
        assertEquals(5, ChannelMath.channel(5975));
        assertEquals(2, ChannelMath.channel(5935));
        assertEquals(ChannelMath.BAND_6, ChannelMath.band(6055));
    }

    @Test public void noNeighboursMeansAllChannelsScoreMaximum() {
        List<ChannelMath.Recommendation> result =
                ChannelMath.recommendations(Collections.emptyList(), ChannelMath.BAND_24);
        assertEquals(3, result.size());
        assertEquals(100, result.get(0).score);
        assertEquals(1, result.get(0).channel);
    }

    @Test public void busyChannelIsPenalized() {
        List<ChannelMath.Recommendation> result = ChannelMath.recommendations(
                Arrays.asList(new ChannelMath.Observation(2412, -40, 20),
                              new ChannelMath.Observation(2417, -55, 20)), ChannelMath.BAND_24);
        assertEquals(6, result.get(0).channel); // 6 and 11 tie; stable ordering picks 6
        assertTrue(result.get(0).score > result.get(result.size() - 1).score);
    }

    @Test public void strengthIsClamped() {
        assertEquals(100, ChannelMath.quality(-25));
        assertEquals(0, ChannelMath.quality(-110));
        assertEquals(60, ChannelMath.quality(-70));
    }

    @Test public void csvEscapesQuotesAndFormulas() {
        NetworkEntry item = new NetworkEntry("=2+\"3", "aa:bb", "WPA2", "Wi-Fi 5", 5180, -61, 80, false);
        String result = CsvExport.render(Collections.singletonList(item));
        assertTrue(result.contains("\"'=2+\"\"3\""));
        assertTrue(result.startsWith("SSID,BSSID"));
    }
}
