package orbital.engine.simulation;

/** Result of applying one simulation snapshot on the owner thread. */
public record SimulationCommit(long frame, int units, int projectiles,
                               long captureNanos, long simulationNanos, long commitNanos,
                               boolean successful){
    public long totalNanos(){
        return captureNanos + simulationNanos + commitNanos;
    }

    public double totalMillis(){
        return totalNanos() / 1_000_000.0;
    }
}
