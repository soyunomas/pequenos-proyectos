package com.soyunomas.horariolectivo;

import org.junit.Test;
import static org.junit.Assert.*;

public class SubjectCodeTest {
    @Test public void acceptsOneTwoAndThreeCharacters(){
        assertTrue(SubjectCode.isValid("A"));
        assertTrue(SubjectCode.isValid("SR"));
        assertTrue(SubjectCode.isValid("APW"));
        assertTrue(SubjectCode.isValid("1A"));
    }
    @Test public void rejectsEmptyAndMoreThanThreeCharacters(){
        assertFalse(SubjectCode.isValid(""));
        assertFalse(SubjectCode.isValid("APW1"));
        assertFalse(SubjectCode.isValid("A-"));
    }
    @Test public void normalizesWhitespaceAndCase(){assertEquals("AB",SubjectCode.normalize(" ab "));}
}
