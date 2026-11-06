package orbital.type;

import static orbital.Vars.tilesize;

/** Conservative 2D geometry shared by turret and support-module mounts. */
public final class DorsalGeometry{
    /** minimum gap between two mounted footprints, in world units */
    public static final float clearance = 1f;
    /** minimum gap between a mounted footprint and the hull sprite bounds, in world units */
    public static final float edgeMargin = 0.5f;

    /** A drawn module is never wider than this fraction of the hull's drawn width.
     *  Kept as an alias so the phase-3 validation band stays visible at the geometry layer. */
    public static final float supportMaxWidthFraction = DorsalVisual.maxWidthFraction;
    /** A drawn module is never narrower than this fraction of the hull's drawn width,
     *  unless the native sprite is already smaller (scale is capped at 1). */
    public static final float supportMinWidthFraction = DorsalVisual.minWidthFraction;

    private DorsalGeometry(){}

    public static float footprint(int size){
        return size * tilesize;
    }

    public static float pixelsPerWorldUnit(){
        return TurretSlot.pxTile / (float)tilesize;
    }

    /**
     * @return whether a square footprint centered at (x, y) stays inside the hull's dorsal silhouette.
     *
     * <p>The silhouette is derived from {@code spriteArea()}, which uses the measured,
     * untrimmed sprite area ({@code dorsalAreaOverride}). Atlas regions are trimmed and
     * would otherwise make placement stricter in-game than in headless tests, so they
     * are deliberately not used here.</p>
     */
    public static boolean fitsHull(UnitType type, float x, float y, float footprint){
        if(type == null || footprint <= 0f) return false;

        float halfHull = (float)Math.sqrt(type.spriteArea()) / pixelsPerWorldUnit() / 2f;
        float half = footprint / 2f;
        float limit = halfHull - edgeMargin;

        return Math.abs(x) + half <= limit && Math.abs(y) + half <= limit;
    }

    /** @return whether two axis-aligned square footprints overlap, including the safety clearance. */
    public static boolean overlaps(float ax, float ay, float aFootprint, float bx, float by, float bFootprint){
        float limit = (aFootprint + bFootprint) / 2f + clearance;
        return Math.abs(ax - bx) < limit && Math.abs(ay - by) < limit;
    }

    /**
     * Resolves the visual scale of one support module. This is a drawing concern only: it never
     * feeds back into {@link #footprint(int)}, {@link #fitsHull} or {@link #overlaps}, so a hull
     * keeps exactly the same compatibility list regardless of how small its modules look.
     *
     * <p>Since phase 4 the scale is derived from this hull's <b>measured</b> sprite size and
     * its dorsal position count, using the exact same rule as removable turrets
     * ({@link DorsalVisual}). It may still be overridden per anchor
     * ({@link SupportAnchor#visualScale}) and tuned per hull
     * ({@link UnitType#dorsalVisualTuning}). Values are always in {@code (0, 1]}.</p>
     *
     * @param regionPx the block sprite's largest side in atlas pixels
     */
    public static float supportVisualScale(UnitType type, SupportAnchor anchor, float regionPx){
        return supportVisualScale(type, anchor, sizeFromRegionPx(regionPx), regionPx);
    }

    /** Overload that uses the block's real size instead of inferring it from the sprite. */
    public static float supportVisualScale(UnitType type, SupportAnchor anchor, int blockSize, float regionPx){
        return DorsalVisual.supportScale(type, anchor, blockSize, regionPx);
    }

    /** @return the hull-derived drawing scale of a removable dorsal turret module. */
    public static float turretVisualScale(UnitType type, int turretSize, float regionPx){
        return DorsalVisual.turretScale(type, turretSize, regionPx);
    }

    /**
     * @return the square side, in world units, that a dorsal module actually occupies once drawn.
     *
     * <p>Since phase 4 the module sprite is scaled down from its native block size, so the module
     * on the hull is far smaller than {@link #footprint(int)} suggests: a 5x5 turret is drawn at
     * 10-13% of the hull, not at its 40-unit block footprint. Module-vs-module separation
     * ({@code WeaponsComp.overlapsTurretMounts}/{@code overlapsSupportMounts}) uses this drawn
     * size, so modules can sit on the hull body without being pushed into the silhouette corners.
     * Silhouette fitting and the dorsal budget keep using the full block footprint.</p>
     */
    public static float visualFootprint(UnitType type, int blockSize, float regionPx){
        if(type == null || regionPx <= 0f) return footprint(blockSize);
        return turretVisualScale(type, blockSize, regionPx) * regionPx / pixelsPerWorldUnit();
    }

    /** Fallback for headless runs: block sprites are {@code size * pxTile} pixels per side. */
    public static int sizeFromRegionPx(float regionPx){
        return Math.max(1, Math.round(regionPx / TurretSlot.pxTile));
    }
}
