#include <jni.h>
#include <algorithm>
#include <cstdint>
#include <cstring>
#include <intrin.h>
#include <immintrin.h>

namespace{
    constexpr int particleSize = 9;
    constexpr float globalDrag = 0.05f;

    bool supportsSse42(){
        int regs[4]{};
        __cpuid(regs, 1);
        return (regs[2] & (1 << 20)) != 0;
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

    bool updateSse42(float* data, int i, int head, float delta, float dragValue){
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

extern "C" JNIEXPORT jint JNICALL Java_orbital_graphics_ParticleRenderer_updateNative(
    JNIEnv* env, jclass, jfloatArray array, jint count, jfloat delta){
    if(array == nullptr || count < 0) return -1;
    const jsize length = env->GetArrayLength(array);
    if(count > length / particleSize) return -1;

    static const int simdMode = supportsAvx2() ? 2 : (supportsSse42() ? 1 : 0);
    jfloat* data = env->GetFloatArrayElements(array, nullptr);
    if(data == nullptr) return -1;

    int head = count * particleSize;
    const float dragValue = std::max(1.0f - globalDrag * delta, 0.0f);
    const bool avx2 = simdMode == 2;
    const bool sse42 = simdMode == 1;

    for(int i = 0; i < head; i += particleSize){
        const int remaining = head - i;
        if(avx2 && remaining >= 8 * particleSize && updateParticleBatchAvx2(data, i, head, delta, dragValue)){
            i += 7 * particleSize;
        }else if(sse42 && remaining >= 4 * particleSize && updateSse42(data, i, head, delta, dragValue)){
            i += 3 * particleSize;
        }else{
            updateScalar(data, i, head, delta, dragValue);
        }
    }

    env->ReleaseFloatArrayElements(array, data, 0);
    return head / particleSize;
}
