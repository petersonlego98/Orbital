package orbital.desktop.vulkan.gl;

import java.util.*;
import java.util.function.LongSupplier;

/**
 * LRU cache of Vulkan pipeline handles keyed by immutable GL state snapshots.
 *
 * <p>Pipeline creation is the most expensive operation in a GL-over-Vulkan emulation, so state
 * changes must not create a new pipeline per draw. The factory is supplied by the native backend;
 * keeping it out of this class allows the cache and its eviction policy to be tested without a GPU.</p>
 */
public final class VulkanPipelineCache{
    private final LinkedHashMap<VulkanPipelineState, Long> pipelines;
    private final int maxSize;
    private long hits, misses, evictions;

    public VulkanPipelineCache(int maxSize){
        if(maxSize < 1) throw new IllegalArgumentException("maxSize must be >= 1");
        this.maxSize = maxSize;
        this.pipelines = new LinkedHashMap<>(16, 0.75f, true);
    }

    public long getOrCreate(VulkanPipelineState state, LongSupplier factory){
        Long existing = pipelines.get(state);
        if(existing != null){
            hits++;
            return existing;
        }
        misses++;
        long created = factory.getAsLong();
        if(pipelines.size() >= maxSize){
            Iterator<Map.Entry<VulkanPipelineState, Long>> it = pipelines.entrySet().iterator();
            it.next();
            it.remove();
            evictions++;
        }
        pipelines.put(state, created);
        return created;
    }

    public int size(){
        return pipelines.size();
    }

    public long hits(){
        return hits;
    }

    public long misses(){
        return misses;
    }

    public long evictions(){
        return evictions;
    }

    public void clear(){
        pipelines.clear();
    }
}
