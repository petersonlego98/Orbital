#include <immintrin.h>
#include <cmath>

namespace{
    constexpr int particleSize = 9;
    constexpr float equalEpsilon = 1e-6f; //arc.math.Mathf.equal(float, float)
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

/** Integrates eight unit/projectile rows; zeroUnmoved applies Mathf.equal(px, nx). */
bool updateUnitBatchAvx2(float* x, float* y, float* vx, float* vy, float* drag,
                         int i, int count, float delta, bool zeroUnmoved){
    const __m256 dx = _mm256_set1_ps(delta);
    const __m256 one = _mm256_set1_ps(1.0f);
    const __m256 zero = _mm256_setzero_ps();
    const __m256 sign = _mm256_set1_ps(-0.0f);
    const __m256 eps = _mm256_set1_ps(equalEpsilon);

    __m256 px = _mm256_loadu_ps(x + i);
    __m256 py = _mm256_loadu_ps(y + i);
    __m256 sx = _mm256_loadu_ps(vx + i);
    __m256 sy = _mm256_loadu_ps(vy + i);
    __m256 d = _mm256_loadu_ps(drag + i);

    __m256 nx = _mm256_add_ps(px, _mm256_mul_ps(sx, dx));
    __m256 ny = _mm256_add_ps(py, _mm256_mul_ps(sy, dx));

    if(zeroUnmoved){
        __m256 stillX = _mm256_cmp_ps(_mm256_andnot_ps(sign, _mm256_sub_ps(px, nx)), eps, _CMP_LE_OQ);
        __m256 stillY = _mm256_cmp_ps(_mm256_andnot_ps(sign, _mm256_sub_ps(py, ny)), eps, _CMP_LE_OQ);
        sx = _mm256_blendv_ps(sx, zero, stillX);
        sy = _mm256_blendv_ps(sy, zero, stillY);
    }

    __m256 scale = _mm256_max_ps(zero, _mm256_sub_ps(one, _mm256_mul_ps(d, dx)));
    _mm256_storeu_ps(x + i, nx);
    _mm256_storeu_ps(y + i, ny);
    _mm256_storeu_ps(vx + i, _mm256_mul_ps(sx, scale));
    _mm256_storeu_ps(vy + i, _mm256_mul_ps(sy, scale));

    _mm256_zeroupper();
    return true;
}
