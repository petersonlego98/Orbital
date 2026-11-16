package orbital.diagnostics;

import java.lang.management.*;
import java.lang.reflect.*;

/**
 * Lightweight CPU and heap sample for the diagnostics overlay.
 *
 * <p>{@code com.sun.management} is read reflectively so the engine still works on a JRE that only
 * exposes the standard {@link OperatingSystemMXBean}. Load averages are reported as {@code -1}
 * when the platform does not provide them, instead of pretending to know.</p>
 */
public final class CpuMonitor{
    private final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

    private final Method processLoad;
    private final Method systemLoad;

    public CpuMonitor(){
        processLoad = method("getProcessCpuLoad");
        systemLoad = method("getCpuLoad");
    }

    public Sample sample(){
        Runtime runtime = Runtime.getRuntime();
        MemoryUsage heap = memory.getHeapMemoryUsage();
        return new Sample(
            Runtime.getRuntime().availableProcessors(),
            read(processLoad),
            read(systemLoad),
            heap.getUsed(),
            heap.getMax(),
            runtime.totalMemory() - runtime.freeMemory(),
            runtime.maxMemory());
    }

    private double read(Method method){
        if(method == null) return -1.0;
        try{
            Object value = method.invoke(os);
            if(!(value instanceof Number number)) return -1.0;
            double load = number.doubleValue();
            return load < 0.0 ? -1.0 : load;
        }catch(ReflectiveOperationException | RuntimeException unavailable){
            return -1.0;
        }
    }

    private Method method(String name){
        try{
            return os.getClass().getMethod(name);
        }catch(NoSuchMethodException absent){
            return null;
        }
    }

    public record Sample(int processors, double processLoad, double systemLoad,
                         long heapUsed, long heapMax, long runtimeUsed, long runtimeMax){
        /** @return process load in percent, or -1 when unavailable. */
        public double processLoadPercent(){
            return processLoad < 0.0 ? -1.0 : processLoad * 100.0;
        }

        /** @return system load in percent, or -1 when unavailable. */
        public double systemLoadPercent(){
            return systemLoad < 0.0 ? -1.0 : systemLoad * 100.0;
        }
    }
}
