package com.soyunomas.wifianalyzer;

import org.junit.Test;
import java.util.HashSet;
import static org.junit.Assert.*;

public class ToolCatalogTest {
    @Test public void allToolsUniqueAndClassified(){
        assertEquals(25,ToolCatalog.ALL.size());
        HashSet<Integer> ids=new HashSet<>();
        for(ToolCatalog.Tool t:ToolCatalog.ALL){
            assertTrue(ids.add(t.id));
            assertFalse(t.title.isEmpty());
            assertFalse(t.group.isEmpty());
            assertNotNull(t.hint);
        }
    }
    @Test public void numberedToolsAreAddressable(){
        for(int i=1;i<=25;i++)assertEquals(i,ToolCatalog.get(i).id);
    }
    @Test(expected=IllegalArgumentException.class)
    public void invalidTool(){ToolCatalog.get(26);}
}
