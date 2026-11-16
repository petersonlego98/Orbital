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
 */
public final class SimdSoaBuffer{
    public static final int columns = 5;

    private ByteBuffer buffer;
    private long base;
    private int capacity;

    /** Grows only when needed; the address stays valid for the lifetime of this object. */
    public void ensure(int rows){
        if(capacity >= rows) return;

        capacity = Math.max(rows, Math.max(256, capacity + (capacity >> 1)));
        buffer = ByteBuffer.allocateDirect(columns * capacity * Float.BYTES)
            .order(ByteOrder.nativeOrder());
        base = SimdNative.directAddress(buffer);
        if(base == 0L){
            buffer = null;
            capacity = 0;
            throw new IllegalStateException("the native SIMD library cannot expose a direct buffer");
        }
    }

    public boolean usable(){
        return buffer != null && base != 0L;
    }

    public int capacity(){
        return capacity;
    }

    /** Copies SoA arrays into the direct buffer; called on the game thread before the parallel phase. */
    public void upload(float[] x, float[] y, float[] vx, float[] vy, float[] drag, int rows){
        ensure(rows);
        int stride = capacity;
        int bytes = Float.BYTES;
        for(int i = 0; i < rows; i++){
            //ByteBuffer absolute indices are in BYTES, not in floats
            buffer.putFloat(i * bytes, x[i]);
            buffer.putFloat((stride + i) * bytes, y[i]);
            buffer.putFloat((2 * stride + i) * bytes, vx[i]);
            buffer.putFloat((3 * stride + i) * bytes, vy[i]);
            buffer.putFloat((4 * stride + i) * bytes, drag[i]);
        }
    }

    /** Copies results back; called on the game thread, after every worker has joined. */
    public void download(float[] x, float[] y, float[] vx, float[] vy, int rows){
        int stride = capacity;
        int bytes = Float.BYTES;
        for(int i = 0; i < rows; i++){
            x[i] = buffer.getFloat(i * bytes);
            y[i] = buffer.getFloat((stride + i) * bytes);
            vx[i] = buffer.getFloat((2 * stride + i) * bytes);
            vy[i] = buffer.getFloat((3 * stride + i) * bytes);
        }
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