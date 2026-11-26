package com.soyunomas.wifianalyzer;
import org.junit.Test;
import static org.junit.Assert.*;

public class ToolHistoryTest {
    @Test public void reportKeepsBoundedEntries(){
        ToolHistory.clear();
        for(int i=0;i<35;i++)ToolHistory.add("Ping","host"+i,"OK");
        assertEquals(30,ToolHistory.count());
        assertFalse(ToolHistory.report().contains("host0\n"));
        assertTrue(ToolHistory.report().contains("host34"));
        ToolHistory.clear();
    }
}
