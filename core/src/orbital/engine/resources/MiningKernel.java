package orbital.engine.resources;

import orbital.engine.memory.ResourceData;

/**
 * One independent mining calculation.
 *
 * <p>Each row extracts at most what is left in its deposit and never touches the world. The result
 * is a proposal ({@code extracted}) that the game thread may apply to an authoritative tile; this is
 * the same snapshot/parallel/commit contract as the other subsystems.</p>
 */
public final class MiningKernel{
    private MiningKernel(){
    }

    public static void compute(ResourceData data, float[] efficiency, int index, float delta){
        float available = data.capacity[index] - data.amount[index];
        if(available <= 0f){
            data.extracted[index] = 0f;
            return;
        }

        float extracted = Math.min(available, data.rate[index] * efficiency[index] * delta);
        data.extracted[index] = extracted;
        data.amount[index] += extracted;
    }
}