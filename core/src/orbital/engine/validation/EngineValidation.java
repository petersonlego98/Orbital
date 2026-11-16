package orbital.engine.validation;

import orbital.engine.OrbitalEngine;
import orbital.engine.ai.AIDecisionKernel;
import orbital.engine.memory.*;
import orbital.engine.navigation.FlowField;
import orbital.engine.particles.ParticleVertexBuilder;
import orbital.engine.physics.SpatialHash;
import orbital.engine.resources.MiningKernel;
import orbital.engine.world.RegionNoise;
import orbital.engine.simulation.*;
import orbital.engine.simd.SimdOperations;
import orbital.engine.threading.*;
import orbital.engine.units.UnitMovementKernel;

import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * The engine's self-test: it runs the same kernels sequentially and in parallel and proves the two
 * agree, then measures how the engine scales from one worker up.
 *
 * <p>This is the harness behind "the engine is correct and it scales". It needs no game, no map and
 * no renderer, so it runs in the headless test suite, from the test-mode panel and from a command
 * line flag. Every case compares against a reference that runs on the calling thread, so a worker
 * that reads or writes the wrong slot fails the case instead of silently producing wrong output.</p>
 *
 * <p>Timings are evidence, not a contract: they depend on the machine and on its load. The
 * correctness checks are the contract. Beware of reading too much into the speedups - a kernel that
 * costs a few nanoseconds per row cannot pay for a pool handoff, whatever the core count says. That
 * is a legitimate result, and it is what the worker-count threshold is chosen from.</p>
 */
public final class EngineValidation{
    public static final int defaultItems = 8_192;
    /** Passes per timing; the best one is reported, which filters out scheduler noise. */
    private static final int timedPasses = 3;
    private static final float delta = 1f / 60f;
    private static final long seed = 20261116L;

    private EngineValidation(){
    }

    /** One correctness case: the same work, once on the calling thread, once on the pool. */
    public record CaseResult(String name, boolean passed, int items,
                             long sequentialNanos, long parallelNanos, String detail){
        public double speedup(){
            return parallelNanos <= 0 ? 0.0 : (double)sequentialNanos / parallelNanos;
        }

        public double sequentialMillis(){
            return sequentialNanos / 1_000_000.0;
        }

        public double parallelMillis(){
            return parallelNanos / 1_000_000.0;
        }
    }

    /** One scaling measurement: a fixed workload through a pool of {@code workers}. */
    public record ScalingPoint(int workers, long nanos, double speedupVsOne, int items){
        public double millis(){
            return nanos / 1_000_000.0;
        }
    }

    public record Report(List<CaseResult> cases, List<ScalingPoint> scaling, boolean allPassed,
                         int requestedWorkers, boolean parallelCapable, String backend){
        /** @return the case with that name, or null when the run did not produce it. */
        public CaseResult find(String name){
            for(CaseResult result : cases){
                if(result.name().equals(name)) return result;
            }
            return null;
        }

        /** Multi-line, log-friendly rendering of everything the run produced. */
        public String summary(){
            StringBuilder out = new StringBuilder(512);
            out.append("Orbital engine validation\n");
            out.append("capability: ").append(parallelCapable ? "parallel" : "sequential only")
               .append(" | workers requested: ").append(requestedWorkers)
               .append(" | simd: ").append(backend).append('\n');

            for(CaseResult result : cases){
                out.append(String.format(Locale.ROOT,
                    "  %-20s %-4s %6d rows | sequential %8.3f ms | parallel %8.3f ms | %5.2fx | %s%n",
                    result.name(), result.passed() ? "PASS" : "FAIL", result.items(),
                    result.sequentialMillis(), result.parallelMillis(), result.speedup(), result.detail()));
            }

            out.append("  scaling:");
            for(ScalingPoint point : scaling){
                out.append(String.format(Locale.ROOT, " %d=%6.3f ms (%.2fx)",
                    point.workers(), point.millis(), point.speedupVsOne()));
            }
            out.append('\n');
            out.append("  result: ").append(allPassed ? "ALL PASS" : "FAILED");

            return out.toString();
        }
    }

    /** Runs every case with as many workers as this machine offers. */
    public static Report run(){
        return run(OrbitalEngine.parallelCapable()
            ? Math.max(1, Runtime.getRuntime().availableProcessors() - 1)
            : 1);
    }

    /**
     * @param maxWorkers the largest pool any case may use; scaling still starts at one worker, so a
     *                   single-worker run measures the sequential reference only.
     */
    public static Report run(final int maxWorkers){
        int workers = Math.max(1, maxWorkers);

        UnitData seed = units(defaultItems);
        float[] drag = drag(seed.count);
        List<CaseResult> cases = new ArrayList<>();

        cases.add(compareUnitMovement(seed, drag, workers));
        cases.add(compareParticleVertices());
        cases.add(runPipelineTransaction(seed, drag, workers));
        cases.add(compareAIDecisions(seed, workers));
        cases.add(compareNavigationFlow(workers));
        cases.add(compareSpatialHash(seed, workers));
        cases.add(compareMining(seed, workers));
        cases.add(compareWorldRegions(workers));

        ArrayList<ScalingPoint> scaling = new ArrayList<>();
        for(int count : workerPlan(workers)){
            scaling.add(measureScaling(seed, drag, count));
        }
        resolveSpeedups(scaling);

        boolean allPassed = true;
        for(CaseResult result : cases){
            allPassed &= result.passed();
        }

        return new Report(Collections.unmodifiableList(cases), Collections.unmodifiableList(scaling),
            allPassed, workers, OrbitalEngine.parallelCapable(), SimdOperations.capabilities().describe());
    }

    //region cases

    private static CaseResult compareUnitMovement(UnitData seed, float[] drag, int workers){
        UnitData sequential = applySequential(seed, drag);
        UnitData parallel = applyParallel(seed, drag, workers);
        boolean passed = equal(sequential, parallel);

        return new CaseResult("units.movement", passed, seed.count,
            bestSequential(seed, drag), bestParallel(seed, drag, workers),
            passed ? "sequential and parallel rows agree" : "ROWS DIVERGE");
    }

    private static CaseResult compareParticleVertices(){
        int particles = defaultItems;
        float[] data = particles(particles);
        float[] sequential = new float[particles * ParticleVertexBuilder.vertexStride];
        float[] parallel = new float[particles * ParticleVertexBuilder.vertexStride];
        float[] warmup = new float[particles * ParticleVertexBuilder.vertexStride];

        //warm up the kernel on the calling thread, outside the timed window
        for(int i = 0; i < particles; i++){
            ParticleVertexBuilder.writeVertex(data, sequential, i);
        }
        System.arraycopy(sequential, 0, warmup, 0, sequential.length);
        Arrays.fill(sequential, 0f);

        long start = System.nanoTime();
        for(int i = 0; i < particles; i++){
            ParticleVertexBuilder.writeVertex(data, sequential, i);
        }
        long sequentialNanos = System.nanoTime() - start;

        //warm up the shared engine as well, otherwise the first pass pays for the pool start
        ParticleVertexBuilder.build(data, particles, warmup);

        start = System.nanoTime();
        int length = ParticleVertexBuilder.build(data, particles, parallel);
        long parallelNanos = System.nanoTime() - start;

        boolean passed = length == particles * ParticleVertexBuilder.vertexStride
            && Arrays.equals(sequential, parallel);

        return new CaseResult("particles.vertices", passed, particles, sequentialNanos, parallelNanos,
            passed ? "sequential and parallel buffer agree" : "BUFFER DIVERGES");
    }

    private static CaseResult runPipelineTransaction(UnitData seed, float[] drag, int workers){
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(workers, OrbitalJobSystem.defaultBatchSize)){
            SimulationPipeline pipeline = new SimulationPipeline(jobs);
            OrbitalSimulation simulation = new OrbitalSimulation(pipeline);

            UnitData reference = applySequential(seed, drag);

            //warm up: the first transaction on a fresh pool pays for worker threads and JIT, and the
            //timed pass below must measure the steady state, not the start-up cost
            runTransaction(simulation, seed, drag, null);

            AtomicIntegerArray committed = new AtomicIntegerArray(seed.count);
            long start = System.nanoTime();
            SimulationCommit commit = runTransaction(simulation, seed, drag, committed);
            long elapsed = System.nanoTime() - start;

            boolean everyRowOnce = commit.units() == seed.count;
            for(int i = 0; i < seed.count && everyRowOnce; i++){
                everyRowOnce = committed.get(i) == 1;
            }

            //the phase writes the snapshot, never the source buffer: that is the whole contract
            boolean passed = commit.successful() && everyRowOnce
                && equal(reference, simulation.snapshot().units());

            return new CaseResult("pipeline.transaction", passed, seed.count,
                bestSequentialTransaction(seed, drag), elapsed,
                passed ? "capture, simulate and commit applied every row once"
                       : "TRANSACTION BROKEN (rows: " + commit.units() + "/" + seed.count
                         + ", every row once: " + everyRowOnce + ")");
        }
    }

    /**
     * The fair baseline for the transaction case: everything the game would pay without the pool -
     * the copy into SoA, the per-row kernel and the per-row commit - measured on the calling
     * thread. Comparing the pipeline against a bare kernel loop would flatter it.
     */
    private static long bestSequentialTransaction(UnitData seed, float[] drag){
        long best = Long.MAX_VALUE;
        for(int pass = 0; pass < timedPasses; pass++){
            UnitData work = copy(seed);
            int committed = 0;
            long start = System.nanoTime();
            for(int i = 0; i < work.count; i++){
                UnitMovementKernel.integrate(work, drag, i, delta);
                committed++;
            }
            best = Math.min(best, System.nanoTime() - start);
            if(committed != work.count) throw new IllegalStateException("reference commit lost rows");
        }
        return best;
    }

    private static SimulationCommit runTransaction(OrbitalSimulation simulation, UnitData seed,
                                                   float[] drag, AtomicIntegerArray committed){
        simulation.capture(seed, new ProjectileData());
        simulation.simulateUnits((snapshot, index) ->
            UnitMovementKernel.integrate(snapshot.units(), drag, index, delta));
        return simulation.commit((units, index) -> {
            if(committed != null) committed.incrementAndGet(index);
        }, null);
    }

    private static CaseResult compareAIDecisions(UnitData seed, int workers){
        AIData source = new AIData(seed.count);
        java.util.Random random = new java.util.Random(20261119L);
        for(int i = 0; i < seed.count; i++){
            source.add(i, seed.x[i], seed.y[i],
                (random.nextFloat() - 0.5f) * 2000f,
                (random.nextFloat() - 0.5f) * 2000f, 0);
        }

        AIData sequential = new AIData();
        sequential.copyFrom(source);
        long start = System.nanoTime();
        for(int i = 0; i < sequential.count; i++){
            AIDecisionKernel.decide(sequential, i);
        }
        long sequentialNanos = System.nanoTime() - start;

        AIData parallel = new AIData();
        parallel.copyFrom(source);
        long parallelNanos;
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(workers, OrbitalJobSystem.defaultBatchSize)){
            start = System.nanoTime();
            jobs.parallelFor("validation.ai", 0, parallel.count,
                index -> AIDecisionKernel.decide(parallel, index));
            parallelNanos = System.nanoTime() - start;
        }

        boolean passed = aiEqual(sequential, parallel);
        return new CaseResult("ai.decisions", passed, source.count, sequentialNanos, parallelNanos,
            passed ? "sequential and parallel steering agree" : "DECISIONS DIVERGE");
    }

    private static CaseResult compareNavigationFlow(int workers){
        int size = 128;
        FlowField expected = navigationField(size);
        FlowField actual = navigationField(size);

        long start = System.nanoTime();
        expected.computeFlowSequential();
        long sequentialNanos = System.nanoTime() - start;

        long parallelNanos;
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(workers, OrbitalJobSystem.defaultBatchSize)){
            start = System.nanoTime();
            actual.computeFlow(jobs);
            parallelNanos = System.nanoTime() - start;
        }

        boolean passed = true;
        for(int y = 0; y < size && passed; y++){
            for(int x = 0; x < size; x++){
                if(expected.distanceAt(x, y) != actual.distanceAt(x, y)
                    || expected.flowXAt(x, y) != actual.flowXAt(x, y)
                    || expected.flowYAt(x, y) != actual.flowYAt(x, y)){
                    passed = false;
                    break;
                }
            }
        }

        return new CaseResult("navigation.flow", passed, size * size, sequentialNanos, parallelNanos,
            passed ? "sequential and parallel fields agree" : "FLOW FIELD DIVERGES");
    }

    private static FlowField navigationField(int size){
        FlowField field = new FlowField(size, size);
        //a deterministic wall with a gap, so blocked cells and paths are both exercised
        for(int y = 0; y < size; y++){
            if(y < size / 3 || y > 2 * size / 3){
                field.setBlocked(size / 2, y, true);
            }
        }
        field.computeDistance(size - 1, size - 1);
        return field;
    }

    private static CaseResult compareSpatialHash(UnitData seed, int workers){
        SpatialHash sequential = new SpatialHash(40_000f, 40_000f, 128f);
        long start = System.nanoTime();
        sequential.build(seed, null);
        long sequentialNanos = System.nanoTime() - start;

        SpatialHash parallel = new SpatialHash(40_000f, 40_000f, 128f);
        long parallelNanos;
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(workers, OrbitalJobSystem.defaultBatchSize)){
            start = System.nanoTime();
            parallel.build(seed, jobs);
            parallelNanos = System.nanoTime() - start;
        }

        boolean passed = sequential.itemCount() == seed.count
            && parallel.itemCount() == seed.count && hashEqual(sequential, parallel);
        return new CaseResult("physics.spatial", passed, seed.count, sequentialNanos, parallelNanos,
            passed ? "both hashes hold the same buckets" : "BUCKETS DIVERGE");
    }

    private static boolean hashEqual(SpatialHash left, SpatialHash right){
        for(int y = 0; y < left.rows(); y++){
            for(int x = 0; x < left.cols(); x++){
                int count = left.countInCell(x, y);
                if(count != right.countInCell(x, y)) return false;
                for(int slot = 0; slot < count; slot++){
                    if(left.itemAt(x, y, slot) != right.itemAt(x, y, slot)) return false;
                }
            }
        }
        return true;
    }

    private static CaseResult compareMining(UnitData seed, int workers){
        ResourceData source = new ResourceData(seed.count);
        float[] efficiency = new float[seed.count];
        for(int i = 0; i < seed.count; i++){
            source.add(i, seed.x[i], seed.y[i], 1f + i % 7, i % 50, 100f);
            efficiency[i] = 0.5f + (i % 13) * 0.1f;
        }

        ResourceData sequential = new ResourceData();
        copyResources(source, sequential);
        long start = System.nanoTime();
        for(int i = 0; i < sequential.count; i++){
            MiningKernel.compute(sequential, efficiency, i, 0.75f);
        }
        long sequentialNanos = System.nanoTime() - start;

        ResourceData parallel = new ResourceData();
        copyResources(source, parallel);
        long parallelNanos;
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(workers, OrbitalJobSystem.defaultBatchSize)){
            start = System.nanoTime();
            jobs.parallelFor("resources.mining", 0, parallel.count,
                i -> MiningKernel.compute(parallel, efficiency, i, 0.75f));
            parallelNanos = System.nanoTime() - start;
        }

        boolean passed = Arrays.equals(sequential.extracted, parallel.extracted)
            && Arrays.equals(sequential.amount, parallel.amount);
        return new CaseResult("resources.mining", passed, seed.count, sequentialNanos, parallelNanos,
            passed ? "sequential and parallel proposals agree" : "MINING DIVERGES");
    }

    private static void copyResources(ResourceData from, ResourceData to){
        to.ensureCapacity(from.count);
        System.arraycopy(from.rate, 0, to.rate, 0, from.count);
        System.arraycopy(from.amount, 0, to.amount, 0, from.count);
        System.arraycopy(from.capacity, 0, to.capacity, 0, from.count);
        System.arraycopy(from.extracted, 0, to.extracted, 0, from.count);
        to.count = from.count;
    }

    private static CaseResult compareWorldRegions(int workers){
        int size = 128;
        float[] expected = new float[size * size];
        float[] actual = new float[size * size];

        long start = System.nanoTime();
        RegionNoise.fill(expected, size, size, 0, 0, size, size, 77123L, 0, size);
        long sequentialNanos = System.nanoTime() - start;

        long parallelNanos;
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(workers, OrbitalJobSystem.defaultBatchSize)){
            start = System.nanoTime();
            int chunks = (size + 7) / 8;
            jobs.parallelFor("world.region", 0, chunks,
                chunk -> {
                    int first = chunk * 8;
                    RegionNoise.fill(actual, size, size,
                        0, 0, size, size, 77123L, first, Math.min(size, first + 8));
                });
            parallelNanos = System.nanoTime() - start;
        }

        boolean passed = Arrays.equals(expected, actual);
        return new CaseResult("world.regions", passed, size * size, sequentialNanos, parallelNanos,
            passed ? "region tiles match the full grid" : "REGIONS DIVERGE");
    }

    private static boolean aiEqual(AIData left, AIData right){
        if(left.count != right.count) return false;
        for(int i = 0; i < left.count; i++){
            if(left.desiredX[i] != right.desiredX[i] || left.desiredY[i] != right.desiredY[i]
                || left.distance[i] != right.distance[i]) return false;
        }
        return true;
    }

    //endregion

    private static ScalingPoint measureScaling(UnitData seed, float[] drag, int workers){
        long nanos = workers == 1 ? bestSequential(seed, drag) : bestParallel(seed, drag, workers);
        return new ScalingPoint(workers, nanos, 0.0, seed.count);
    }

    /**
     * Replaces every speedup with the ratio against the single-worker measurement, which is the only
     * honest baseline: the same work with a pool that does nothing but hand out ranges.
     */
    private static void resolveSpeedups(List<ScalingPoint> scaling){
        if(scaling.isEmpty()) return;

        long base = scaling.get(0).nanos();
        for(int i = 0; i < scaling.size(); i++){
            ScalingPoint point = scaling.get(i);
            double speedup = base <= 0 || point.nanos() <= 0 ? 0.0 : (double)base / point.nanos();
            scaling.set(i, new ScalingPoint(point.workers(), point.nanos(), speedup, point.items()));
        }
    }

    private static UnitData applySequential(UnitData seed, float[] drag){
        UnitData work = copy(seed);
        for(int i = 0; i < work.count; i++){
            UnitMovementKernel.integrate(work, drag, i, delta);
        }
        return work;
    }

    private static UnitData applyParallel(UnitData seed, float[] drag, int workers){
        UnitData work = copy(seed);
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(workers, OrbitalJobSystem.defaultBatchSize)){
            jobs.parallelFor("validation.units", 0, work.count,
                index -> UnitMovementKernel.integrate(work, drag, index, delta));
        }
        return work;
    }

    /**
     * Times the kernel on the calling thread. The buffer copy and the warm-up pass stay outside the
     * timed window, so both columns of the report measure the same thing: the work itself.
     */
    private static long bestSequential(UnitData seed, float[] drag){
        UnitData work = copy(seed);

        long start = System.nanoTime();
        for(int i = 0; i < work.count; i++){
            UnitMovementKernel.integrate(work, drag, i, delta);
        }
        work.copyFrom(seed);

        long best = Long.MAX_VALUE;
        for(int pass = 0; pass < timedPasses; pass++){
            start = System.nanoTime();
            for(int i = 0; i < work.count; i++){
                UnitMovementKernel.integrate(work, drag, i, delta);
            }
            best = Math.min(best, System.nanoTime() - start);
            work.copyFrom(seed);
        }
        return best;
    }

    /**
     * Times the same kernel through a pool of {@code workers}. Worker start-up and the JIT of the
     * kernel are paid by an untimed warm-up pass; each timed pass restores the buffer from the seed
     * so every pass does identical work.
     */
    private static long bestParallel(UnitData seed, float[] drag, int workers){
        long best = Long.MAX_VALUE;
        try(OrbitalJobSystem jobs = new OrbitalJobSystem(workers, OrbitalJobSystem.defaultBatchSize)){
            UnitData work = copy(seed);

            jobs.parallelFor("validation.warmup", 0, work.count,
                index -> UnitMovementKernel.integrate(work, drag, index, delta));
            work.copyFrom(seed);

            for(int pass = 0; pass < timedPasses; pass++){
                long start = System.nanoTime();
                jobs.parallelFor("validation.units", 0, work.count,
                    index -> UnitMovementKernel.integrate(work, drag, index, delta));
                best = Math.min(best, System.nanoTime() - start);
                work.copyFrom(seed);
            }
        }
        return best;
    }

    private static int[] workerPlan(int maxWorkers){
        int[] candidates = {1, 2, 4, 8, 16, 32, 64};
        ArrayList<Integer> plan = new ArrayList<>();
        for(int candidate : candidates){
            if(candidate > maxWorkers) break;
            if(!plan.contains(candidate)) plan.add(candidate);
        }
        if(plan.isEmpty()) plan.add(1);

        int[] result = new int[plan.size()];
        for(int i = 0; i < result.length; i++){
            result[i] = plan.get(i);
        }
        return result;
    }

    //region synthetic data

    private static UnitData units(int count){
        UnitData units = new UnitData();
        Random random = new Random(seed);
        for(int i = 0; i < count; i++){
            units.add(i,
                (random.nextFloat() - 0.5f) * 40_000f,
                (random.nextFloat() - 0.5f) * 40_000f,
                (random.nextFloat() - 0.5f) * 20f,
                (random.nextFloat() - 0.5f) * 20f,
                0f, 100f, 0);
        }
        return units;
    }

    private static float[] drag(int count){
        Random random = new Random(seed + 1L);
        float[] drag = new float[count];
        for(int i = 0; i < count; i++){
            drag[i] = i % 64 == 0 ? random.nextFloat() * 8f : random.nextFloat() * 0.6f;
        }
        return drag;
    }

    private static float[] particles(int count){
        Random random = new Random(seed + 2L);
        float[] data = new float[count * ParticleVertexBuilder.particleStride];
        for(int i = 0; i < count; i++){
            int offset = i * ParticleVertexBuilder.particleStride;
            data[offset] = random.nextFloat();
            data[offset + 1] = 1f + random.nextFloat();
            data[offset + 2] = random.nextFloat() * 100f;
            data[offset + 3] = random.nextFloat() * 100f;
            data[offset + 4] = 0f;
            data[offset + 5] = 0f;
            data[offset + 6] = random.nextFloat() * 4f;
            data[offset + 7] = random.nextFloat() * 4f;
            data[offset + 8] = random.nextFloat();
        }
        return data;
    }

    private static UnitData copy(UnitData source){
        UnitData copy = new UnitData();
        copy.copyFrom(source);
        return copy;
    }

    private static boolean equal(UnitData left, UnitData right){
        if(left.count != right.count) return false;
        for(int i = 0; i < left.count; i++){
            if(left.x[i] != right.x[i] || left.y[i] != right.y[i]
                || left.velocityX[i] != right.velocityX[i] || left.velocityY[i] != right.velocityY[i]){
                return false;
            }
        }
        return true;
    }

    //endregion
}