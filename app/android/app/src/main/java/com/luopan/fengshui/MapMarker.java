package com.luopan.fengshui;

/**
 * D-005: single 3D pick marker (client-side model). Single-point overwrite, no
 * compass linkage (D-006) — {@code headingAtPick} is the heading at pick time only.
 */
public final class MapMarker {
    public final double lng;
    public final double lat;
    /** null when the DEM had no elevation; never 0 as a sentinel (D-005). */
    public final Double elevM;
    public final float headingAtPick;
    public final String src;

    public MapMarker(double lng, double lat, Double elevM, float headingAtPick, String src) {
        this.lng = lng;
        this.lat = lat;
        this.elevM = elevM;
        this.headingAtPick = headingAtPick;
        this.src = src;
    }

    @Override
    public String toString() {
        return "MapMarker{lng=" + lng + ", lat=" + lat + ", elevM=" + elevM
                + ", headingAtPick=" + headingAtPick + ", src=" + src + "}";
    }
}
