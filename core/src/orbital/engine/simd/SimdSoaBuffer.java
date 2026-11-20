package orbital.engine.simd;

import java.nio.*;

/**
 * One direct buffer holding five parallel float columns (x, y, vx, vy, drag).
 *
 * <p>JNI's {@code GetFloatArrayElements} is not safe to call from several workers on the same Java
 * array: each call may hand out a private copy and the last release overwrites the others. A direct
 * buffer has a stable native address, so workers can integrate disjoint slices of it concurrently
 * and the engine only uploads/downloads at the frame boundaries. This is the Java {@code ByteBuffer}
 * interop the architecture asks for, used where it genuinely solves a correctness problem.</p>
 *
 * <p>Transfers use bulk {@link FloatBuffer} copies. The old element-by-element
 * {@code putFloat/getFloat} loop issued 9 calls per row (5 up, 4 down), which on a 2048-row
 * parallel step cost more than the native arithmetic it fed.</p>
 */
public final class SimdSoaBuffer{
    public static final int columns = 5;

    private ByteBuffer buffer;
    private long base;
    private int capacity;
    /** One slice view per column, rebuilt only when the buffer grows. */
    private FloatBuffer[] views;

    /** Grows only when needed; the address stays valid for the lifetime of this object. */
    public void ensure(int rows){
        if(capacity >= rows) return;

        int nextCapacity = Math.max(rows, Math.max(256, capacity + (capacity >> 1)));
        ByteBuffer allocated = ByteBuffer.allocateDirect(columns * nextCapacity * Float.BYTES)
            .order(ByteOrder.nativeOrder());
        long address = SimdNative.directAddress(allocated);
        if(address == 0L){
            throw new IllegalStateException("the native SIMD library cannot expose a direct buffer");
        }

        //commit only after every fallible step succeeded, so a failed grow leaves the old
        //buffer and its capacity consistent
        buffer = allocated;
        base = address;
        capacity = nextCapacity;
        views = new FloatBuffer[columns];
        for(int column = 0; column < columns; column++){
            ByteBuffer slice = buffer.duplicate().order(ByteOrder.nativeOrder());
            slice.position(column * capacity * Float.BYTES);
            views[column] = slice.slice().order(ByteOrder.nativeOrder()).asFloatBuffer();
        }
    }

    /**
     * Ensures capacity and reports whether the native path can be used. Unlike {@link #usable()},
     * this is safe to call in a condition: the buffer is created here instead of only inside
     * {@link #upload}, which used to leave the native branch permanently unreachable.
     */
    public boolean prepare(int rows){
        try{
            ensure(rows);
        }catch(IllegalStateException unavailable){
            return false;
        }
        return usable();
    }

    public boolean usable(){
        return buffer != null && base != 0L && views != null;
    }

    public int capacity(){
        return capacity;
    }

    /** Copies SoA arrays into the direct buffer; called on the game thread before the parallel phase. */
    public void upload(float[] x, float[] y, float[] vx, float[] vy, float[] drag, int rows){
        ensure(rows);
        put(0, x, rows);
        put(1, y, rows);
        put(2, vx, rows);
        put(3, vy, rows);
        put(4, drag, rows);
    }

    /** Copies results back; called on the game thread, after every worker has joined. */
    public void download(float[] x, float[] y, float[] vx, float[] vy, int rows){
        get(0, x, rows);
        get(1, y, rows);
        get(2, vx, rows);
        get(3, vy, rows);
    }

    private void put(int column, float[] source, int rows){
        FloatBuffer view = views[column];
        view.clear();
        view.put(source, 0, rows);
    }

    private void get(int column, float[] target, int rows){
        FloatBuffer view = views[column];
        view.clear();
        view.get(target, 0, rows);
    }

    public long xAddress(){
        return base;
    }

    public long yAddress(){
        return base + (long)capacity * Float.BYTES;
    }

    public long vxAddress(){
        return base + 2L * capacity * Float.BYTES;
    }

    public long vyAddress(){
        return base + 3L * capacity * Float.BYTES;
    }

    public long dragAddress(){
        return base + 4L * capacity * Float.BYTES;
    }
}
