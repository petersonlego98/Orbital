#include <jni.h>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <intrin.h>
#include <immintrin.h>

namespace{
    constexpr int particleSize = 9;
    constexpr float globalDrag = 0.05f;

    //the vector fallback only uses SSE1 intrinsics (add/mul/compare/movemask/prefetch), so the
    //baseline SSE2 of every x86-64 CPU is the honest requirement - checking SSE4.2 used to
    //refuse the vector path on older chips for no reason
    bool supportsSse2(){
        int regs[4]{};
        __cpuid(regs, 1);
        return (regs[3] & (1 << 26)) != 0;
    }

    bool supportsAvx2(){
        int regs[4]{};
        __cpuid(regs, 1);
        const bool osxsave = (regs[2] & (1 << 27)) != 0;
        const bool avx = (regs[2] & (1 << 28)) != 0;
        if(!osxsave || !avx) return false;

        const unsigned __int64 xcr0 = _xgetbv(0);
        if((xcr0 & 0x6) != 0x6) return false;

        __cpuidex(regs, 7, 0);
        return (regs[1] & (1 << 5)) != 0;
    }

    bool updateSse2(float* data, int i, int head, float delta, float dragValue){
        constexpr int prefetchParticles = 8;
        const int prefetchIndex = i + prefetchParticles * particleSize;
        if(prefetchIndex < head){
            _mm_prefetch(reinterpret_cast<const char*>(data + prefetchIndex), _MM_HINT_T0);
            if(prefetchIndex + 16 < head){
                _mm_prefetch(reinterpret_cast<const char*>(data + prefetchIndex + 16), _MM_HINT_T0);
            }
        }

        const int i1 = i + particleSize;
        const int i2 = i1 + particleSize;
        const int i3 = i2 + particleSize;

        const __m128 ages = _mm_add_ps(
            _mm_setr_ps(data[i], data[i1], data[i2], data[i3]), _mm_set1_ps(delta));
        const __m128 lifetimes = _mm_setr_ps(data[i + 1], data[i1 + 1], data[i2 + 1], data[i3 + 1]);
        if(_mm_movemask_ps(_mm_cmplt_ps(ages, lifetimes)) != 0xF) return false;

        const __m128 dx = _mm_set1_ps(delta);
        const __m128 drag = _mm_set1_ps(dragValue);
        const __m128 x = _mm_add_ps(
            _mm_setr_ps(data[i + 2], data[i1 + 2], data[i2 + 2], data[i3 + 2]),
            _mm_mul_ps(_mm_setr_ps(data[i + 4], data[i1 + 4], data[i2 + 4], data[i3 + 4]), dx));
        const __m128 y = _mm_add_ps(
            _mm_setr_ps(data[i + 3], data[i1 + 3], data[i2 + 3], data[i3 + 3]),
            _mm_mul_ps(_mm_setr_ps(data[i + 5], data[i1 + 5], data[i2 + 5], data[i3 + 5]), dx));
        const __m128 vx = _mm_mul_ps(_mm_setr_ps(data[i + 4], data[i1 + 4], data[i2 + 4], data[i3 + 4]), drag);
        const __m128 vy = _mm_mul_ps(_mm_setr_ps(data[i + 5], data[i1 + 5], data[i2 + 5], data[i3 + 5]), drag);

        float outAges[4], outX[4], outY[4], outVx[4], outVy[4];
        _mm_storeu_ps(outAges, ages);
        _mm_storeu_ps(outX, x);
        _mm_storeu_ps(outY, y);
        _mm_storeu_ps(outVx, vx);
        _mm_storeu_ps(outVy, vy);
        const int indices[4] = {i, i1, i2, i3};
        for(int lane = 0; lane < 4; lane++){
            const int at = indices[lane];
            data[at] = outAges[lane];
            data[at + 2] = outX[lane];
            data[at + 3] = outY[lane];
            data[at + 4] = outVx[lane];
            data[at + 5] = outVy[lane];
        }
        return true;
    }

    void updateScalar(float* data, int& i, int& head, float delta, float dragValue){
        data[i] += delta;
        if(data[i] >= data[i + 1]){
            if(head > particleSize){
                std::memmove(data + i, data + head - particleSize, particleSize * sizeof(float));
            }
            head -= particleSize;
            i -= particleSize;
        }else{
            data[i + 2] += data[i + 4] * delta;
            data[i + 3] += data[i + 5] * delta;
            data[i + 4] *= dragValue;
            data[i + 5] *= dragValue;
        }
    }
}

extern bool updateParticleBatchAvx2(float* data, int i, int head, float delta, float dragValue);
extern bool updateUnitBatchAvx2(float* x, float* y, float* vx, float* vy, float* drag,
                                int i, int count, float delta, bool zeroUnmoved);

namespace{
    constexpr float equalEpsilon = 1e-6f; //arc.math.Mathf.equal(float, float)

    //SSE2 batch of four units/projectiles. Returns false only when the caller must fall back.
    bool updateUnitBatchSse2(float* x, float* y, float* vx, float* vy, float* drag,
                             int i, int count, float delta, bool zeroUnmoved){
        const int i1 = i + 1, i2 = i + 2, i3 = i + 3;
        const __m128 dx = _mm_set1_ps(delta);
        const __m128 one = _mm_set1_ps(1.0f);
        const __m128 zero = _mm_setzero_ps();

        __m128 px = _mm_setr_ps(x[i], x[i1], x[i2], x[i3]);
        __m128 py = _mm_setr_ps(y[i], y[i1], y[i2], y[i3]);
        __m128 sx = _mm_setr_ps(vx[i], vx[i1], vx[i2], vx[i3]);
        __m128 sy = _mm_setr_ps(vy[i], vy[i1], vy[i2], vy[i3]);
        __m128 d = _mm_setr_ps(drag[i], drag[i1], drag[i2], drag[i3]);

        __m128 nx = _mm_add_ps(px, _mm_mul_ps(sx, dx));
        __m128 ny = _mm_add_ps(py, _mm_mul_ps(sy, dx));

        if(zeroUnmoved){
            //Mathf.equal: fabs(px - nx) <= 1e-6f
            const __m128 sign = _mm_set1_ps(-0.0f);
            const __m128 eps = _mm_set1_ps(equalEpsilon);
            __m128 stillX = _mm_cmple_ps(_mm_andnot_ps(sign, _mm_sub_ps(px, nx)), eps);
            __m128 stillY = _mm_cmple_ps(_mm_andnot_ps(sign, _mm_sub_ps(py, ny)), eps);
            sx = _mm_blendv_ps(sx, zero, stillX);
            sy = _mm_blendv_ps(sy, zero, stillY);
        }

        __m128 scale = _mm_max_ps(zero, _mm_sub_ps(one, _mm_mul_ps(d, dx)));
        float ox[4], oy[4], ovx[4], ovy[4];
        _mm_storeu_ps(ox, nx);
        _mm_storeu_ps(oy, ny);
        _mm_storeu_ps(ovx, _mm_mul_ps(sx, scale));
        _mm_storeu_ps(ovy, _mm_mul_ps(sy, scale));

        const int lanes[4] = {i, i1, i2, i3};
        for(int lane = 0; lane < 4; lane++){
            x[lanes[lane]] = ox[lane];
            y[lanes[lane]] = oy[lane];
            vx[lanes[lane]] = ovx[lane];
            vy[lanes[lane]] = ovy[lane];
        }
        return true;
    }

    void updateUnitScalar(float* x, float* y, float* vx, float* vy, float* drag,
                          int i, float delta, bool zeroUnmoved){
        float px = x[i], py = y[i];
        float sx = vx[i], sy = vy[i];
        float nx = px + sx * delta, ny = py + sy * delta;
        if(zeroUnmoved){
            if(std::fabs(px - nx) <= equalEpsilon) sx = 0.0f;
            if(std::fabs(py - ny) <= equalEpsilon) sy = 0.0f;
        }
        float scale = std::max(1.0f - drag[i] * delta, 0.0f);
        x[i] = nx;
        y[i] = ny;
        vx[i] = sx * scale;
        vy[i] = sy * scale;
    }

    //Works on raw pointers, so workers may integrate disjoint slices of one shared direct buffer
    //without JNI array pinning/copying (which is not safe from several threads at once).
    void integrateUnitsRange(float* x, float* y, float* vx, float* vy, float* drag,
                             int offset, int count, float delta, bool zero){
        static const int simdMode = supportsAvx2() ? 2 : (supportsSse2() ? 1 : 0);
        const int end = offset + count;
        int i = offset;
        while(i < end){
            const int remaining = end - i;
            if(simdMode == 2 && remaining >= 8
               && updateUnitBatchAvx2(x, y, vx, vy, drag, i, end, delta, zero)){
                i += 8;
            }else if(simdMode >= 1 && remaining >= 4
                    && updateUnitBatchSse2(x, y, vx, vy, drag, i, end, delta, zero)){
                i += 4;
            }else{
                updateUnitScalar(x, y, vx, vy, drag, i, delta, zero);
                i++;
            }
        }
    }
}

extern "C" JNIEXPORT jint JNICALL Java_orbital_engine_simd_SimdNative_updateParticlesNative(
    JNIEnv* env, jclass, jfloatArray array, jint count, jfloat delta){
    if(array == nullptr || count < 0) return -1;
    const jsize length = env->GetArrayLength(array);
    if(count > length / particleSize) return -1;

    static const int simdMode = supportsAvx2() ? 2 : (supportsSse2() ? 1 : 0);
    jfloat* data = env->GetFloatArrayElements(array, nullptr);
    if(data == nullptr) return -1;

    int head = count * particleSize;
    const float dragValue = std::max(1.0f - globalDrag * delta, 0.0f);
    const bool avx2 = simdMode == 2;
    //an AVX2 machine can run the SSE batch too: when one lane expires the AVX2 batch aborts,
    //and the four lanes below it may still be alive - without this they all fell back to the
    //scalar loop one by one
    const bool sse2 = simdMode >= 1;

    for(int i = 0; i < head; i += particleSize){
        const int remaining = head - i;
        if(avx2 && remaining >= 8 * particleSize && updateParticleBatchAvx2(data, i, head, delta, dragValue)){
            i += 7 * particleSize;
        }else if(sse2 && remaining >= 4 * particleSize && updateSse2(data, i, head, delta, dragValue)){
            i += 3 * particleSize;
        }else{
            updateScalar(data, i, head, delta, dragValue);
        }
    }

    env->ReleaseFloatArrayElements(array, data, 0);
    return head / particleSize;
}

extern "C" JNIEXPORT jint JNICALL Java_orbital_engine_simd_SimdNative_backendLevelNative(
    JNIEnv*, jclass){
    // 0 = scalar, 1 = SSE2, 2 = AVX2; Java chooses a backend from this single answer so the
    // detection cannot drift between the JNI loader and the kernels
    static const int level = supportsAvx2() ? 2 : (supportsSse2() ? 1 : 0);
    return level;
}

/**
 * Integrates unit/projectile velocity over SoA arrays in one native call.
 * zeroUnmoved=true applies Mathf.equal(px, nx) and zeroes the axis; projectiles pass false.
 */
extern "C" JNIEXPORT jint JNICALL Java_orbital_engine_simd_SimdNative_updateUnitsNative(
    JNIEnv* env, jclass, jfloatArray xArr, jfloatArray yArr, jfloatArray vxArr, jfloatArray vyArr,
    jfloatArray dragArr, jint offset, jint count, jfloat delta, jboolean zeroUnmoved){
    if(count < 0 || offset < 0 || xArr == nullptr || yArr == nullptr || vxArr == nullptr
       || vyArr == nullptr || dragArr == nullptr) return -1;
    const jsize xLen = env->GetArrayLength(xArr);
    if(xLen - offset < count || env->GetArrayLength(yArr) - offset < count
       || env->GetArrayLength(vxArr) - offset < count || env->GetArrayLength(vyArr) - offset < count
       || env->GetArrayLength(dragArr) - offset < count) return -1;

    jfloat* x = env->GetFloatArrayElements(xArr, nullptr);
    jfloat* y = env->GetFloatArrayElements(yArr, nullptr);
    jfloat* vx = env->GetFloatArrayElements(vxArr, nullptr);
    jfloat* vy = env->GetFloatArrayElements(vyArr, nullptr);
    jfloat* drag = env->GetFloatArrayElements(dragArr, nullptr);
    if(x == nullptr || y == nullptr || vx == nullptr || vy == nullptr || drag == nullptr){
        if(x != nullptr) env->ReleaseFloatArrayElements(xArr, x, JNI_ABORT);
        if(y != nullptr) env->ReleaseFloatArrayElements(yArr, y, JNI_ABORT);
        if(vx != nullptr) env->ReleaseFloatArrayElements(vxArr, vx, JNI_ABORT);
        if(vy != nullptr) env->ReleaseFloatArrayElements(vyArr, vy, JNI_ABORT);
        if(drag != nullptr) env->ReleaseFloatArrayElements(dragArr, drag, JNI_ABORT);
        return -1;
    }

    integrateUnitsRange(x, y, vx, vy, drag, offset, count, delta, zeroUnmoved == JNI_TRUE);

    env->ReleaseFloatArrayElements(xArr, x, 0);
    env->ReleaseFloatArrayElements(yArr, y, 0);
    env->ReleaseFloatArrayElements(vxArr, vx, 0);
    env->ReleaseFloatArrayElements(vyArr, vy, 0);
    env->ReleaseFloatArrayElements(dragArr, drag, 0);
    return count;
}

/** Address of a direct ByteBuffer, so several workers can share one native SoA allocation. */
extern "C" JNIEXPORT jlong JNICALL Java_orbital_engine_simd_SimdNative_directAddressNative(
    JNIEnv* env, jclass, jobject buffer){
    if(buffer == nullptr) return 0;
    void* address = env->GetDirectBufferAddress(buffer);
    return reinterpret_cast<jlong>(address);
}

/** Integrates a disjoint slice of direct buffers; no JNI array pinning, safe across workers. */
extern "C" JNIEXPORT jint JNICALL Java_orbital_engine_simd_SimdNative_updateUnitsDirectNative(
    JNIEnv*, jclass, jlong ax, jlong ay, jlong avx, jlong avy, jlong adrag,
    jint offset, jint count, jfloat delta, jboolean zeroUnmoved){
    if(count < 0 || offset < 0 || ax == 0 || ay == 0 || avx == 0 || avy == 0 || adrag == 0) return -1;

    integrateUnitsRange(
        reinterpret_cast<float*>(ax), reinterpret_cast<float*>(ay),
        reinterpret_cast<float*>(avx), reinterpret_cast<float*>(avy),
        reinterpret_cast<float*>(adrag),
        offset, count, delta, zeroUnmoved == JNI_TRUE);
    return count;
}
