package com.soyunomas.wifianalyzer;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Pure address-selection tests; physical Wi-Fi discovery requires device testing. */
public class LanScannerTest {
    @Test public void preferGatewayAndCommonAddresses() {
        List<String> sorted=LanScanner.prioritize(Arrays.asList(
            "192.168.1.120","192.168.1.60","192.168.1.1","192.168.1.20","192.168.1.254"),
            "192.168.1.120");
        assertEquals("192.168.1.120",sorted.get(0));
        assertEquals("192.168.1.1",sorted.get(1));
        assertEquals("192.168.1.254",sorted.get(2));
        assertEquals("192.168.1.20",sorted.get(3));
    }
    @Test public void rejectNonPrivateIpv4Ranges() {
        assertTrue(LanScanner.privateIpv4("192.168.10.1"));
        assertTrue(LanScanner.privateIpv4("172.16.0.2"));
        assertTrue(LanScanner.privateIpv4("172.31.255.9"));
        assertTrue(LanScanner.privateIpv4("10.0.0.10"));
        assertFalse(LanScanner.privateIpv4("172.32.0.1"));
        assertFalse(LanScanner.privateIpv4("8.8.8.8"));
        assertFalse(LanScanner.privateIpv4("127.0.0.1"));
    }
    @Test public void acceptsRfc6598AndRejectsOther100Addresses() {
        assertTrue(LanScanner.privateIpv4("100.64.0.1"));
        assertTrue(LanScanner.privateIpv4("100.70.7.254"));
        assertTrue(LanScanner.privateIpv4("100.127.255.255"));
        assertFalse(LanScanner.privateIpv4("100.63.255.255"));
        assertFalse(LanScanner.privateIpv4("100.128.0.1"));
        assertFalse(LanScanner.privateIpv4("100.1.2.3"));
        assertFalse(LanScanner.privateIpv4("100.64.300.2"));
    }
    @Test public void scansEntireSlash22AndNeverBeyond() {
        List<String> ips=LanScanner.candidateAddresses("100.70.5.77",22);
        assertEquals(1021,ips.size());
        assertTrue(ips.contains("100.70.4.1"));
        assertTrue(ips.contains("100.70.7.254"));
        assertTrue(ips.contains("100.70.6.10"));
        assertFalse(ips.contains("100.70.5.77"));
        assertFalse(ips.contains("100.70.8.1"));
        assertFalse(ips.contains("100.70.4.0"));
        assertFalse(ips.contains("100.70.7.255"));
    }
    @Test public void capsLargeNetworksAndRespectsNarrowerPrefixes() {
        assertEquals(253,LanScanner.candidateAddresses("192.168.10.24",24).size());
        assertEquals(509,LanScanner.candidateAddresses("100.85.2.27",23).size());
        assertEquals(1021,LanScanner.candidateAddresses("100.85.2.27",20).size());
        assertEquals(22,LanScanner.boundedPrefix(20));
        assertEquals(25,LanScanner.boundedPrefix(25));
        assertFalse(LanScanner.candidateAddresses("100.85.2.27",20).contains("100.85.4.1"));
    }
    @Test(expected=IllegalArgumentException.class)
    public void publicHundredNetworkNotScannableByDefault() {
        LanScanner.candidateAddresses("100.63.1.10",22);
    }

    @Test public void orderIpv4Unsigned() {
        assertTrue(LanScanner.toLong("192.168.1.100")>LanScanner.toLong("192.168.1.9"));
        assertEquals(3232235777L,LanScanner.toLong("192.168.1.1"));
    }
}
