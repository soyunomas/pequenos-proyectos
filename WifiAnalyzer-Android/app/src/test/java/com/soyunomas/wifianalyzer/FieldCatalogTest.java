package com.soyunomas.wifianalyzer;
import org.junit.Test;
import java.util.HashSet;
import static org.junit.Assert.*;

public class FieldCatalogTest {
    @Test public void allTwentyNonRootToolsArePresentAndDistinct(){
        assertEquals(45,ToolCatalog.ALL.size());
        HashSet<Integer> ids=new HashSet<>();
        for(ToolCatalog.Tool tool:ToolCatalog.ALL) assertTrue(ids.add(tool.id));
        for(int id=26;id<=45;id++){
            ToolCatalog.Tool tool=ToolCatalog.get(id);
            assertEquals(id,tool.id);
            assertFalse(tool.title.trim().isEmpty());
            assertFalse(tool.description.trim().isEmpty());
        }
    }
    @Test public void noOldToolWasRenumbered(){
        assertEquals("Ping / alcanzabilidad",ToolCatalog.get(1).title);
        assertEquals("Informe técnico",ToolCatalog.get(25).title);
    }
}
