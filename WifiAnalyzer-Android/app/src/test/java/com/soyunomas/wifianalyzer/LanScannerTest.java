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
    @Test public void orderIpv4Unsigned() {
        assertTrue(LanScanner.toLong("192.168.1.100")>LanScanner.toLong("192.168.1.9"));
        assertEquals(3232235777L,LanScanner.toLong("192.168.1.1"));
    }
}
