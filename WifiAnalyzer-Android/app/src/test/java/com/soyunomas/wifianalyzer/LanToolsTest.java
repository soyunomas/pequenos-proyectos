package com.soyunomas.wifianalyzer;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class LanToolsTest {
    @Test public void ouiHandlesAssignedAndRandomAddresses(){
        Map<String,String> list=new HashMap<>();
        list.put("001122","Fabricante A");
        list.put("0011223","Fabricante B");
        list.put("001122334","Fabricante C");
        assertEquals("Fabricante C",OuiDatabase.lookup(list,"00:11:22:33:44:55"));
        assertEquals("Fabricante B",OuiDatabase.lookup(list,"00:11:22:3F:44:55"));
        assertEquals("Fabricante A",OuiDatabase.lookup(list,"00:11:22:F3:44:55"));
        assertEquals("MAC local o aleatoria",OuiDatabase.lookup(list,"02:11:22:33:44:55"));
        assertEquals("MAC no disponible",OuiDatabase.lookup(list,""));
        assertEquals("MAC no válida",OuiDatabase.lookup(list,"invalid"));
    }

    @Test public void privateNetworksRestrictedToRFC1918(){
        assertTrue(LanScanner.privateIpv4("192.168.0.1"));
        assertTrue(LanScanner.privateIpv4("10.0.10.22"));
        assertTrue(LanScanner.privateIpv4("172.20.1.3"));
        assertFalse(LanScanner.privateIpv4("172.32.1.3"));
        assertFalse(LanScanner.privateIpv4("8.8.8.8"));
    }
    @Test public void convertsIpv4ToUnsignedLong(){
        assertEquals(3232235777L,LanScanner.toLong("192.168.1.1"));
    }
}
