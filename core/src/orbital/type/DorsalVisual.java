package orbital.type;

/**
 * Drawing-only proportions for dorsal modules, derived from the measured size of every hull.
 *
 * <p>Phase 3 used a hand-tuned scale per hull. Phase 4 replaces that table with one rule that
 * works for <b>both</b> removable turrets and support modules. It is driven entirely by data
 * already present on the {@link UnitType}: the measured sprite area ({@code dorsalAreaOverride})
 * and the number of dorsal positions configured for that hull.</p>
 *
 * <p>The numbers come from {@code core/assets/sprites/sprites.aatls}: the smallest hulls
 * (mono, flare, alpha and the two duo variants) are 48 px wide and the largest (oct) is 400 px.
 * Module sprites range from the trimmed 28 px duo turret to a 160 px 5x5 turret, with the
 * support blocks at 32/64/96 px. A module drawn at native size can therefore cover up to 58%
 * of a small hull, which is what made it read as a building glued to the unit.</p>
 *
 * <p>The resolved scale is <b>always in (0, 1]</b> and is used only when drawing. It never
 * enters {@link DorsalGeometry#footprint(int)}, {@link DorsalGeometry#fitsHull},
 * {@link DorsalGeometry#overlaps}, the turret budget, the slot/anchor compatibility checks,
 * gameplay ranges or entity serialization. Every hull keeps exactly the same list of blocks
 * it can install.</p>
 */
public final class DorsalVisual{
    /** Measured side of the smallest hulls, in atlas pixels: mono/flare/alpha/mono-duo/flare-duo. */
    public static final float smallestHullPx = 48f;
    /** Measured side of the largest hull, in atlas pixels: oct. */
    public static final float largestHullPx = 400f;

    /** Target drawn width as a fraction of the hull width on the smallest hulls. */
    public static final float smallHullTargetFraction = 0.28f;
    /** Target drawn width as a fraction of the hull width on the largest hulls. */
    public static final float largeHullTargetFraction = 0.16f;
    /** Extra hull fraction allowed per extra block of module size, so heavier turrets still read as heavier. */
    public static final float sizeFractionStep = 0.02f;
    /** Fraction removed from the target per additional dorsal position on the hull. */
    public static final float crowdPenalty = 0.045f;
    /** The crowd factor is never allowed to fall below this value. */
    public static final float minCrowdFactor = 0.65f;

    /** A drawn module is never narrower than this fraction of the hull's drawn width. */
    public static final float minWidthFraction = 0.10f;
    /** A drawn module is never wider than this fraction of the hull's drawn width. */
    public static final float maxWidthFraction = 0.28f;

    private DorsalVisual(){}

    /** @return the measured, untrimmed side of this hull's sprite, in atlas pixels. */
    public static float hullPx(UnitType type){
        return type == null ? 0f : (float)Math.sqrt(type.spriteArea());
    }

    /** @return the number of dorsal positions configured on this hull (turret slots + support anchors). */
    public static int positions(UnitType type){
        return type == null ? 1 : Math.max(1, type.turretSlots.size + type.supportAnchors.size);
    }

    /**
     * @return the fraction of the hull width this module should occupy once drawn.
     *
     * <p>Small hulls get a relatively larger target (0.28) so a 1x1 pod stays recognizable;
     * capital hulls settle at 0.16 so a 5x5 turret does not swallow the silhouette. Larger
     * blocks earn a small extra allowance, while crowded hulls lose a little per extra
     * position. The result is clamped into the phase-3 band [0.10, 0.28].</p>
     */
    public static float targetFraction(UnitType type, int size){
        float hull = hullPx(type);
        float t = clamp((hull - smallestHullPx) / (largestHullPx - smallestHullPx), 0f, 1f);
        float base = smallHullTargetFraction + (largeHullTargetFraction - smallHullTargetFraction) * t;
        float tier = sizeFractionStep * Math.max(0, size - 1);
        float crowd = Math.max(minCrowdFactor, 1f - crowdPenalty * (positions(type) - 1));
        return clamp((base + tier) * crowd, minWidthFraction, maxWidthFraction);
    }

    /**
     * Unified resolver for turrets and support modules.
     *
     * @param modulePx the module's longest sprite side, in atlas pixels (trimmed regions included)
     * @return a drawing-only scale in {@code (0, 1]}
     */
    public static float scale(UnitType type, int size, float modulePx){
        if(type == null || modulePx <= 0f) return 1f;
        float hull = hullPx(type);
        if(hull <= 0f) return 1f;

        float tuning = type.dorsalVisualTuning > 0f ? type.dorsalVisualTuning : 1f;
        float raw = targetFraction(type, size) * hull / modulePx * tuning;
        float min = Math.min(1f, minWidthFraction * hull / modulePx);
        return clamp(raw, min, 1f);
    }

    /** @return the scale for one support module, honoring an optional manual per-anchor override. */
    public static float supportScale(UnitType type, SupportAnchor anchor, int size, float modulePx){
        float derived = scale(type, size, modulePx);
        if(anchor == null || anchor.visualScale <= 0f) return derived;

        float min = Math.min(1f, minWidthFraction * hullPx(type) / modulePx);
        return clamp(anchor.visualScale, min, 1f);
    }

    /** @return the scale for one removable dorsal turret module. */
    public static float turretScale(UnitType type, int size, float modulePx){
        return scale(type, size, modulePx);
    }

    private static float clamp(float value, float min, float max){
        return Math.max(min, Math.min(max, value));
    }
}
