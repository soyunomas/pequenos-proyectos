package com.soyunomas.wifianalyzer;
import org.junit.Test;
import java.util.HashSet;
import static org.junit.Assert.*;

public class PortCatalogTest {
    @Test public void commonPortsAndWebSchemes(){
        assertEquals("HTTP posible",PortCatalog.service(80));
        assertEquals("SSH",PortCatalog.service(22));
        assertEquals("https://192.168.1.20:8443/",PortCatalog.url("192.168.1.20",8443));
        assertEquals("http://10.1.2.3:80/",PortCatalog.url("10.1.2.3",80));
        assertFalse(PortCatalog.web(22));
    }
    @Test public void quickIsSubsetOfExtended(){
        assertTrue(PortCatalog.extended().containsAll(PortCatalog.quick()));
        assertEquals(PortCatalog.quick().size(),new HashSet<>(PortCatalog.quick()).size());
    }
    @Test public void parsesOnlyValidPorts(){
        assertEquals(65535,PortCatalog.parse("65535"));
        for(String bad:new String[]{"0","65536","-1","80:90","22 ","","abc"}){
            try{PortCatalog.parse(bad);fail("Accepted: "+bad);}
            catch(IllegalArgumentException expected){}
        }
    }
    @Test(expected=IllegalArgumentException.class)
    public void refusesPublicWebTargets(){PortCatalog.url("8.8.8.8",80);}
}
