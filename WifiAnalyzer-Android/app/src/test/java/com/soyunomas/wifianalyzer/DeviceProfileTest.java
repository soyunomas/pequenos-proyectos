package com.soyunomas.wifianalyzer;

import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceProfileTest {
    @Test public void classifiesObservedServices(){
        assertEquals("Router",DeviceProfile.category("TCP 80",true,false));
        assertEquals("Este móvil",DeviceProfile.category("",false,true));
        assertEquals("Impresora",DeviceProfile.category("_ipp._tcp.",false,false));
        assertEquals("TV / multimedia",DeviceProfile.category("_googlecast._tcp.",false,false));
        assertEquals("Ordenador / NAS",DeviceProfile.category("NetBIOS PC",false,false));
        assertEquals("Dispositivo",DeviceProfile.category("TCP rechazado",false,false));
    }
    @Test public void hintsAreNotPresentedAsVerifiedMacVendors() {
        assertTrue(DeviceProfile.serviceHint("Samsung TV SSDP").contains("anunciado"));
        assertEquals("",DeviceProfile.serviceHint("TCP 443 responde"));
    }
}
