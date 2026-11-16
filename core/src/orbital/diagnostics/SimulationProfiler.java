package orbital.diagnostics;

import java.util.*;

/**
 * Frame-level simulation timings for capture, parallel simulation and commit.
 *
 * <p>Phase totals answer "where did the frame go"; the ring of completed frames answers "is the
 * commit occasionally spiking", which an average hides. The ring is synchronized because capture
 * and commit happen on the game thread while diagnostics may read from another one.</p>
 */
public final class SimulationProfiler{
    private static final int historySize = 256;

    private final long[] frameNanos = new long[historySize];
    private int next;
    private int filled;

    private long pendingCapture;
    private long pendingSimulation;
    private long captureNanos;
    private long simulationNanos;
    private long commitNanos;
    private long frames;

    public synchronized void recordCapture(long nanos){
        pendingCapture = Math.max(0L, nanos);
        captureNanos += pendingCapture;
    }

    public synchronized void recordSimulation(long nanos){
        long elapsed = Math.max(0L, nanos);
        pendingSimulation += elapsed;
        simulationNanos += elapsed;
    }

    public synchronized void recordCommit(long nanos){
        commitNanos += Math.max(0L, nanos);
        long total = pendingCapture + pendingSimulation + Math.max(0L, nanos);
        frameNanos[next] = total;
        next = (next + 1) % historySize;
        filled = Math.min(historySize, filled + 1);
        frames++;
        pendingCapture = 0L;
        pendingSimulation = 0L;
    }

    public synchronized Snapshot snapshot(){
        long[] copy = Arrays.copyOf(frameNanos, filled);
        Arrays.sort(copy);
        long total = 0L;
        for(long value : copy) total += value;

        return new Snapshot(
            frames,
            captureNanos,
            simulationNanos,
            commitNanos,
            filled == 0 ? 0L : copy[copy.length - 1],
            filled == 0 ? 0L : copy[(int)(copy.length * 0.95)],
            filled == 0 ? 0.0 : total / 1_000_000.0 / copy.length,
            captureNanos + simulationNanos + commitNanos);
    }

    public synchronized void reset(){
        Arrays.fill(frameNanos, 0L);
        next = 0;
        filled = 0;
        pendingCapture = 0L;
        pendingSimulation = 0L;
        captureNanos = 0L;
        simulationNanos = 0L;
        commitNanos = 0L;
        frames = 0L;
    }

    public record Snapshot(long frames, long captureNanos, long simulationNanos, long commitNanos,
                           long maxFrameNanos, long p95FrameNanos, double averageFrameMillis,
                           long totalNanos){
        public double captureMillis(){
            return captureNanos / 1_000_000.0;
        }

        public double simulationMillis(){
            return simulationNanos / 1_000_000.0;
        }

        public double commitMillis(){
            return commitNanos / 1_000_000.0;
        }

        public double maxFrameMillis(){
            return maxFrameNanos / 1_000_000.0;
        }

        public double p95FrameMillis(){
            return p95FrameNanos / 1_000_000.0;
        }
    }
}
