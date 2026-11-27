package com.soyunomas.wifianalyzer;

import java.util.List;

/**
 * Local, bounded interpolation of measured Wi-Fi RSSI.
 * Unknown areas have no estimate; a few points must NEVER colour an entire floor.
 * Coordinates are normalized to the plan bounds; aspect is width/height.
 * This is a visualization of observations, not a radio propagation prediction.
 */
public final class CoverageSurface {
    private CoverageSurface(){}

    public static final class Sample {
        public final float x, y, dbm;
        public Sample(float x,float y,float dbm){
            if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(dbm)||
                    x<0||x>1||y<0||y>1||dbm>0||dbm< -127)
                throw new IllegalArgumentException("Invalid coverage sample");
            this.x=x;this.y=y;this.dbm=dbm;
        }
    }

    public static final class Estimate {
        public final float dbm, confidence;
        Estimate(float dbm,float confidence){this.dbm=dbm;this.confidence=confidence;}
    }

    /**
     * @param aspect plan width / height, positive
     * @param radius distance as a fraction of the SHORTER plan side
     * @return null outside all measured neighbourhoods; no extrapolation
     */
    public static Estimate estimate(List<Sample> samples,float x,float y,float aspect,float radius) {
        if(samples==null||samples.isEmpty()||!Float.isFinite(x)||!Float.isFinite(y)||
                x<0||x>1||y<0||y>1||!Float.isFinite(aspect)||aspect<=0||
                !Float.isFinite(radius)||radius<=0)return null;
        final double sx=aspect>=1?aspect:1;
        final double sy=aspect>=1?1:1/aspect;
        double weighted=0,total=0;
        double minDistance=Double.MAX_VALUE;
        for(Sample s:samples){
            double dx=(x-s.x)*sx,dy=(y-s.y)*sy;
            double distance=Math.hypot(dx,dy);
            if(distance<0.00001)return new Estimate(s.dbm,1f);
            if(distance>=radius)continue;
            minDistance=Math.min(minDistance,distance);
            double scaled=distance/radius;
            double taper=1-scaled;
            double weight=(taper*taper)/(0.0001+distance*distance);
            weighted+=s.dbm*weight;
            total+=weight;
        }
        if(total==0)return null;
        float confidence=(float)Math.max(0,Math.min(1,1-minDistance/radius));
        return new Estimate((float)(weighted/total),confidence);
    }

    /** Continuous, perceptually ordered ramp: weak red → amber → teal → strong green. */
    public static int color(float dbm) {
        float v=Math.max(-90,Math.min(-40,dbm));
        if(v<=-78)return lerp(0xFFCE5460,0xFFE58F40,(v+90)/12);
        if(v<=-65)return lerp(0xFFE58F40,0xFFE2C654,(v+78)/13);
        if(v<=-53)return lerp(0xFFE2C654,0xFF67AE83,(v+65)/12);
        return lerp(0xFF67AE83,0xFF208B65,(v+53)/13);
    }
    private static int lerp(int a,int b,float f){
        float t=Math.max(0,Math.min(1,f));
        int ar=(a>>>16)&255,ag=(a>>>8)&255,ab=a&255;
        int br=(b>>>16)&255,bg=(b>>>8)&255,bb=b&255;
        return 0xFF000000|
            (Math.round(ar+(br-ar)*t)<<16)|
            (Math.round(ag+(bg-ag)*t)<<8)|
            Math.round(ab+(bb-ab)*t);
    }
}
