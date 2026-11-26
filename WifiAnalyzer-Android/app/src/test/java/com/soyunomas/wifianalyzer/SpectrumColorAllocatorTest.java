package com.soyunomas.wifianalyzer;

import org.junit.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

/** Regression for repeated colours within a 2.4 GHz spectrum. */
public class SpectrumColorAllocatorTest {
    @Test public void visibleNetworksHaveUniqueColoursEvenBeyondPalette() {
        SpectrumColorAllocator allocator=new SpectrumColorAllocator();
        HashSet<Integer> colors=new HashSet<>();
        for(int i=0;i<80;i++){
            String bssid=String.format(Locale.ROOT,"02:11:22:%02X:%02X:%02X",
                (i>>16)&255,(i>>8)&255,i&255);
            SpectrumColorAllocator.Style style=allocator.style(bssid);
            assertTrue("Duplicate colour at network "+i,colors.add(style.color));
            assertEquals(i+1,style.number);
        }
    }
    @Test public void sameBssidRetainsColourBetweenScans() {
        SpectrumColorAllocator allocator=new SpectrumColorAllocator();
        SpectrumColorAllocator.Style home=allocator.style("AA:BB:CC:DD:EE:01");
        allocator.style("AA:BB:CC:DD:EE:02");
        allocator.style("AA:BB:CC:DD:EE:03");
        SpectrumColorAllocator.Style after=allocator.style("aa:bb:cc:dd:ee:01");
        assertEquals(home.color,after.color);
        assertEquals(home.number,after.number);
        assertEquals(home.linePattern,after.linePattern);
    }
    @Test public void restoresAssignedOrderWhenActivityIsRecreated() {
        SpectrumColorAllocator initial=new SpectrumColorAllocator();
        initial.style("02:01:02:03:04:05");
        initial.style("02:01:02:03:04:06");
        initial.style("02:01:02:03:04:07");
        List<String> keys=initial.assignedIdentifiers();
        SpectrumColorAllocator restored=new SpectrumColorAllocator();
        restored.restore(keys);
        for(String key:keys){
            assertEquals(initial.style(key).color,restored.style(key).color);
            assertEquals(initial.style(key).number,restored.style(key).number);
        }
    }
    @Test public void identifiersIgnoreMacCaseAndSpaces(){
        SpectrumColorAllocator allocator=new SpectrumColorAllocator();
        assertSame(allocator.style(" 00:11:22:33:44:55 "),allocator.style("00:11:22:33:44:55"));
        assertEquals(1,allocator.assignedIdentifiers().size());
    }
}
