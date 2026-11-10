#include <immintrin.h>

namespace{
    constexpr int particleSize = 9;
}

bool updateParticleBatchAvx2(float* data, int i, int head, float delta, float dragValue){
    // Keep the hint inside the active array and far enough ahead to overlap memory latency.
    constexpr int prefetchParticles = 16;
    const int prefetchIndex = i + prefetchParticles * particleSize;
    if(prefetchIndex < head){
        _mm_prefetch(reinterpret_cast<const char*>(data + prefetchIndex), _MM_HINT_T0);
        if(prefetchIndex + 16 < head){
            _mm_prefetch(reinterpret_cast<const char*>(data + prefetchIndex + 16), _MM_HINT_T0);
        }
    }

    const __m256i indices = _mm256_setr_epi32(
        i, i + particleSize, i + particleSize * 2, i + particleSize * 3,
        i + particleSize * 4, i + particleSize * 5, i + particleSize * 6, i + particleSize * 7);
    const __m256i lifetimeIndices = _mm256_add_epi32(indices, _mm256_set1_epi32(1));
    const __m256 ages = _mm256_add_ps(_mm256_i32gather_ps(data, indices, 4), _mm256_set1_ps(delta));
    const __m256 lifetimes = _mm256_i32gather_ps(data, lifetimeIndices, 4);
    if(_mm256_movemask_ps(_mm256_cmp_ps(ages, lifetimes, _CMP_LT_OQ)) != 0xFF) return false;

    const __m256i xIndices = _mm256_add_epi32(indices, _mm256_set1_epi32(2));
    const __m256i yIndices = _mm256_add_epi32(indices, _mm256_set1_epi32(3));
    const __m256i vxIndices = _mm256_add_epi32(indices, _mm256_set1_epi32(4));
    const __m256i vyIndices = _mm256_add_epi32(indices, _mm256_set1_epi32(5));

    const __m256 dx = _mm256_set1_ps(delta);
    const __m256 drag = _mm256_set1_ps(dragValue);
    const __m256 vx = _mm256_i32gather_ps(data, vxIndices, 4);
    const __m256 vy = _mm256_i32gather_ps(data, vyIndices, 4);
    const __m256 x = _mm256_add_ps(_mm256_i32gather_ps(data, xIndices, 4), _mm256_mul_ps(vx, dx));
    const __m256 y = _mm256_add_ps(_mm256_i32gather_ps(data, yIndices, 4), _mm256_mul_ps(vy, dx));

    alignas(32) float outAges[8], outX[8], outY[8], outVx[8], outVy[8];
    _mm256_store_ps(outAges, ages);
    _mm256_store_ps(outX, x);
    _mm256_store_ps(outY, y);
    _mm256_store_ps(outVx, _mm256_mul_ps(vx, drag));
    _mm256_store_ps(outVy, _mm256_mul_ps(vy, drag));
    for(int lane = 0; lane < 8; lane++){
        const int at = i + lane * particleSize;
        data[at] = outAges[lane];
        data[at + 2] = outX[lane];
        data[at + 3] = outY[lane];
        data[at + 4] = outVx[lane];
        data[at + 5] = outVy[lane];
    }

    _mm256_zeroupper();
    return true;
}
