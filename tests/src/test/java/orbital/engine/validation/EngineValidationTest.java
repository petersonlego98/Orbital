package orbital.engine.validation;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the engine self-test harness. */
class EngineValidationTest{

    @Test
    void everyCasePasses(){
        EngineValidation.Report report = EngineValidation.run(4);

        assertTrue(report.allPassed(), "a validation case failed:\n" + report.summary());
        assertEquals(8, report.cases().size(), "every subsystem must be measured");

        for(String name : new String[]{"units.movement", "particles.vertices", "pipeline.transaction",
            "ai.decisions", "navigation.flow", "physics.spatial", "resources.mining", "world.regions"}){
            EngineValidation.CaseResult result = report.find(name);
            assertNotNull(result, "missing case " + name);
            assertTrue(result.passed(), name + " failed: " + result.detail());
            assertTrue(result.items() > 0, name + " did no work");
        }
    }

    @Test
    void aSingleWorkerRunStillPasses(){
        EngineValidation.Report report = EngineValidation.run(1);

        assertTrue(report.allPassed(), "the one-worker path is the reference, it must pass:\n" + report.summary());
        assertEquals(1, report.scaling().get(0).workers());
        assertEquals(1, report.requestedWorkers());
    }

    @Test
    void scalingStartsAtOneWorkerAndNeverExceedsTheRequest(){
        int max = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors()));
        EngineValidation.Report report = EngineValidation.run(max);

        assertFalse(report.scaling().isEmpty());
        assertEquals(1, report.scaling().get(0).workers(), "the baseline must be one worker");
        assertEquals(1.0, report.scaling().get(0).speedupVsOne(), 1e-9,
            "the single-worker point is the baseline itself");

        for(EngineValidation.ScalingPoint point : report.scaling()){
            assertTrue(point.workers() <= max, "planned " + point.workers() + " workers above the request");
            assertTrue(point.nanos() > 0, "a timing of zero means the case never ran");
        }
    }

    @Test
    void summaryReportsEveryCaseAndTheSimdBackend(){
        EngineValidation.Report report = EngineValidation.run(2);
        String text = report.summary();

        for(EngineValidation.CaseResult result : report.cases()){
            assertTrue(text.contains(result.name()), "the summary dropped the case " + result.name());
        }
        assertTrue(text.contains("simd:"), "the summary must state the SIMD backend in use");
        assertTrue(text.contains("scaling:"), "the summary must state the scaling plan");
        assertTrue(text.contains("ALL PASS"), "the summary must state the verdict");
    }
}