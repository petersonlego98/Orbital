package orbital.engine.threading;

import arc.*;

/**
 * Resolves how many worker threads the engine should start.
 *
 * <p>The persisted value is a request, not a promise: {@code 0} means automatic and reserves one
 * logical processor for the game thread. The engine never hard-codes a core count as a ceiling;
 * the slider simply grows with the machine, up to {@link #maxWorkers}.</p>
 *
 * <p>This class only reads Arc's {@code Settings}; spawning belongs to
 * {@link OrbitalJobSystem}. Keeping resolution separate makes the policy unit-testable without a
 * running game.</p>
 */
public final class OrbitalThreadConfig{
    /** Persisted preference key used by Settings &gt; Advanced Graphics. */
    public static final String key = "orbitalworkers";
    /** Value that asks the engine to size itself. */
    public static final int automatic = 0;
    /** Upper bound for an explicitly requested pool. */
    public static final int maxWorkers = 64;

    private OrbitalThreadConfig(){
    }

    public static int availableProcessors(){
        return Math.max(1, Runtime.getRuntime().availableProcessors());
    }

    /** Leaves one logical processor for the game thread, which owns input and the commit. */
    public static int automaticWorkers(){
        return Math.max(1, availableProcessors() - 1);
    }

    /** @return the worker count for a stored request; values below 1 mean automatic. */
    public static int resolve(int requested){
        if(requested <= automatic) return automaticWorkers();
        return Math.min(requested, maxWorkers);
    }

    /** Largest value the settings slider offers; grows with wide machines and keeps the 32+ goal. */
    public static int maxSelectable(){
        int doubled = availableProcessors() > maxWorkers / 2 ? maxWorkers : availableProcessors() * 2;
        return Math.max(32, Math.min(maxWorkers, doubled));
    }

    /** @return the persisted request, or {@link #automatic} when settings are not available. */
    public static int stored(){
        try{
            return Core.settings == null ? automatic : Core.settings.getInt(key, automatic);
        }catch(Throwable unavailable){
            return automatic;
        }
    }

    /** @return the worker count the next engine instance should start. */
    public static int configured(){
        return resolve(stored());
    }
}
