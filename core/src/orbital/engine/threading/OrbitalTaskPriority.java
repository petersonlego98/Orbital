package orbital.engine.threading;

/**
 * Scheduling hint for engine work.
 *
 * <p>{@code ForkJoinPool} has no native priority queue, so the priority is honoured where it can
 * be honoured honestly: tasks submitted together through an {@link OrbitalTaskGroup} are dispatched
 * highest-priority first, and the value is recorded in the metrics so a frame can be profiled by
 * priority. A lower priority never changes correctness - it only changes who gets the workers
 * first when more work is queued than there are cores.</p>
 */
public enum OrbitalTaskPriority{
    /** Critical path work, such as state that must be committed this frame. */
    CRITICAL(0),
    /** Work the player will notice immediately, such as combat or unit movement. */
    HIGH(1),
    /** Default for engine jobs. */
    NORMAL(2),
    /** Background quality-of-life work that may be spread over frames. */
    LOW(3),
    /** Work that only runs when the frame has spare capacity. */
    IDLE(4);

    /** Sort weight; lower runs first. */
    public final int weight;

    OrbitalTaskPriority(int weight){
        this.weight = weight;
    }
}
