package com.soyunomas.wifianalyzer;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class ToolkitCoreTest {
    @Test public void calculatesTypicalIpv4Subnet() {
        SubnetTools.Subnet s=SubnetTools.calculate("192.168.10.44/24");
        assertEquals("192.168.10.0",s.network);
        assertEquals("192.168.10.255",s.broadcast);
        assertEquals("255.255.255.0",s.mask);
        assertEquals(254,s.usableHosts);
    }
    @Test public void handlesPointToPointAndHostRoutes() {
        assertEquals(2,SubnetTools.calculate("10.0.0.4/31").usableHosts);
        assertEquals(1,SubnetTools.calculate("10.0.0.4/32").usableHosts);
        assertEquals(4294967294L,SubnetTools.calculate("0.0.0.0/0").usableHosts);
    }
    @Test(expected=IllegalArgumentException.class)
    public void rejectsInvalidIp(){SubnetTools.calculate("256.2.3.4/24");}
    @Test(expected=IllegalArgumentException.class)
    public void rejectsInvalidCidr(){SubnetTools.calculate("192.168.1.1/33");}
    @Test public void classifiesTypicalSecurity() {
        assertEquals(0,SecurityAdvisor.evaluate("Abierta").score);
        assertEquals(75,SecurityAdvisor.evaluate("WPA2").score);
        assertEquals(100,SecurityAdvisor.evaluate("WPA3").score);
        assertEquals(80,SecurityAdvisor.evaluate("WPA2/WPA3").score);
    }
    @Test public void deduplicatesHistoricalScanTimestamps(){
        SurveyHistory s=new SurveyHistory();
        NetworkEntry n=new NetworkEntry("Mi red","aa:bb:cc:dd:ee:ff","WPA2","Wi-Fi 5",5180,-66,80,false);
        assertTrue(s.add(n,100,1000));
        assertFalse(s.add(n,100,2000));
        assertFalse(s.add(n,90,3000));
        assertTrue(s.add(n,101,4000));
        assertEquals(2,s.count());
        assertEquals(-66,s.avg(n.bssid));
    }
    @Test public void jsonRedactsIdentifiers(){
        NetworkEntry n=new NetworkEntry("Casa","aa:bb:cc:dd:ee:ff","WPA2","Wi-Fi 5",5180,-60,80,false);
        String s=JsonExport.render(Collections.singletonList(n),true);
        assertTrue(s.contains("redactado"));
        assertFalse(s.contains("Casa"));
        assertFalse(s.contains("aa:bb:cc:dd:ee:ff"));
    }
}
