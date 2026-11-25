package com.soyunomas.wifianalyzer;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResponseInspectorTest {
    @Test public void escapesControlBytes(){
        byte[] data=new byte[]{72,105,10,27,0,13,9,(byte)255};
        String result=ResponseInspector.printable(data);
        assertEquals("Hi\n\\x1B\\x00\\r\\t\\xFF",result);
    }
    @Test public void rejectsUnknownTargetsWithoutConnecting(){
        String result=ResponseInspector.inspect(null,"8.8.8.8",80,ResponseInspector.BANNER,null);
        assertTrue(result.contains("Objetivo no permitido"));
    }
}
