package com.soyunomas.wifianalyzer;
import org.junit.Test;
import static org.junit.Assert.*;

public class DnsToolsTest {
    @Test public void validReverseIpv4(){assertEquals("4.3.2.1.in-addr.arpa",DnsTools.reverse("1.2.3.4"));}
    @Test(expected=IllegalArgumentException.class)
    public void invalidReverse(){DnsTools.reverse("300.2.3.4");}
    @Test public void recordTypes(){assertEquals(Integer.valueOf(257),DnsTools.types().get("CAA"));assertEquals(Integer.valueOf(33),DnsTools.types().get("SRV"));}
    @Test public void pointerNameCompression()throws Exception{
        byte[] p={3,97,98,99,5,108,111,99,97,108,0,(byte)192,0};
        assertEquals("abc.local",DnsTools.name(p,11));assertEquals(13,DnsTools.skipName(p,11));
    }
    @Test public void decodesIpv4()throws Exception{
        assertEquals("192.168.1.10",DnsTools.decode(new byte[]{(byte)192,(byte)168,1,10},0,4,1));
    }
}
