package com.soyunomas.wifianalyzer;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Regression: a handful of strong Wi-Fi samples must not paint an entire floor green. */
public class CoverageSurfaceTest {
    @Test public void threeMeasurementsNeverFillUnmeasuredPartOfFloor() {
        List<CoverageSurface.Sample> s=Arrays.asList(
            new CoverageSurface.Sample(.15f,.15f,-48),
            new CoverageSurface.Sample(.30f,.30f,-49),
            new CoverageSurface.Sample(.45f,.40f,-47));
        assertNull(CoverageSurface.estimate(s,.90f,.90f,1f,.15f));
        assertNull(CoverageSurface.estimate(s,.93f,.10f,1f,.15f));
        assertNotNull(CoverageSurface.estimate(s,.15f,.15f,1f,.15f));
    }

    @Test public void actualReadingAtSampleLocationIsNotAltered() {
        List<CoverageSurface.Sample> s=Arrays.asList(
            new CoverageSurface.Sample(.20f,.30f,-49),
            new CoverageSurface.Sample(.30f,.30f,-82));
        CoverageSurface.Estimate measured=CoverageSurface.estimate(s,.20f,.30f,1f,.18f);
        assertNotNull(measured);
        assertEquals(-49f,measured.dbm,.001);
        assertEquals(1f,measured.confidence,.001);
    }

    @Test public void interpolatesOnlyWhereTwoLocalObservationsOverlap() {
        List<CoverageSurface.Sample> s=Arrays.asList(
            new CoverageSurface.Sample(.25f,.40f,-45),
            new CoverageSurface.Sample(.45f,.40f,-83));
        CoverageSurface.Estimate midpoint=CoverageSurface.estimate(s,.35f,.40f,1f,.18f);
        assertNotNull(midpoint);
        assertTrue(midpoint.dbm < -46);
        assertTrue(midpoint.dbm > -82);
        assertNull(CoverageSurface.estimate(s,.70f,.40f,1f,.18f));
    }

    @Test public void identicalReadingsDontInventSignalDifferences() {
        List<CoverageSurface.Sample> s=Arrays.asList(
            new CoverageSurface.Sample(.2f,.2f,-52),
            new CoverageSurface.Sample(.35f,.38f,-52),
            new CoverageSurface.Sample(.60f,.70f,-52));
        for(float x:new float[]{.2f,.3f,.6f}) {
            CoverageSurface.Estimate v=CoverageSurface.estimate(s,x,x,1f,.18f);
            if(v!=null)assertEquals(-52f,v.dbm,.001);
        }
        assertNull(CoverageSurface.estimate(s,.95f,.95f,1f,.18f));
    }

    @Test public void aspectRatioUsesPhysicalPlanProportions() {
        List<CoverageSurface.Sample> s=Arrays.asList(new CoverageSurface.Sample(.5f,.5f,-60));
        assertNull(CoverageSurface.estimate(s,.65f,.5f,3.0f,.20f));
        assertNotNull(CoverageSurface.estimate(s,.52f,.5f,3.0f,.20f));
    }

    @Test public void confidenceDecreasesTowardMeasurementBoundary() {
        List<CoverageSurface.Sample> s=Arrays.asList(new CoverageSurface.Sample(.4f,.4f,-60));
        CoverageSurface.Estimate close=CoverageSurface.estimate(s,.42f,.4f,1f,.20f);
        CoverageSurface.Estimate edge=CoverageSurface.estimate(s,.59f,.4f,1f,.20f);
        assertNotNull(close);assertNotNull(edge);
        assertTrue(close.confidence>edge.confidence);
        assertNull(CoverageSurface.estimate(s,.61f,.4f,1f,.20f));
    }

    @Test public void colourScaleHasSmoothBoundedEndpoints(){
        assertNotEquals(CoverageSurface.color(-50),CoverageSurface.color(-51));
        assertEquals(CoverageSurface.color(-110),CoverageSurface.color(-90));
        assertEquals(CoverageSurface.color(-40),CoverageSurface.color(-30));
    }
}
