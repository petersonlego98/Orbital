package orbital.type;

import orbital.world.Block;

/**
 * A manually placed dorsal anchor for one support-module category.
 * Offsets are hull-local world units; one block tile is {@code Vars.tilesize} world units.
 *
 * <p>{@link #visualScale} affects the drawn sprite only. All placement rules keep using
 * {@link #maxSize} and the original block footprint, so compatibility is unaffected.</p>
 */
public class SupportAnchor{
    public final SupportModuleCategory category;
    /** maximum block size this anchor can hold, in block tiles */
    public final int maxSize;
    public final float offsetX, offsetY;
    public String name;
    /** Visual-only absolute scale override for this anchor. {@code <= 0} uses the hull-derived
     *  scale from {@link DorsalVisual}, clamped into the same width band. */
    public float visualScale = -1f;

    public SupportAnchor(SupportModuleCategory category, int maxSize, float offsetX, float offsetY){
        this.category = category;
        this.maxSize = maxSize;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.name = category == null ? null : category.name();
    }

    /** Fluent setter for a per-anchor visual size, allowing one hull to mix module sizes. */
    public SupportAnchor scaled(float scale){
        this.visualScale = scale;
        return this;
    }

    public boolean accepts(Block block){
        return block != null && maxSize >= block.size && SupportModuleCategory.of(block) == category;
    }
}
