#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <stdint.h>

#include <audio_engine/utils/log.h>
#include <audio_engine/utils/memory.h>
#include <audio_engine/processor/fft/simd_base.h>
#include <audio_engine/processor/fft/dft.h>

#define LOG_TAG "(jni)audio_processor:shibatch_fft.c"

typedef float REAL;
#define TYPE SIMDBase_TYPE_FLOAT

void rfft(int n,int isign,float *x) {
    size_t i, j;

    static int mode;
    static int veclen;
    static int sizeOfVect;
    static DFT *p;
    static int old_n;
    static REAL *sx;

    if (n == 0) {
        if (sx) {
            /* mem */ SIMDBase_alignedFree (sx);
            sx = NULL;
        }
        if (p) {
            DFT_dispose(p, mode);
            p = NULL;
        }

        return;
    }

    n = 1 << n;

    if (n != old_n) {
        mode = SIMDBase_chooseBestMode(TYPE);

        veclen = SIMDBase_getModeParamInt(SIMDBase_PARAMID_VECTOR_LEN, mode);
        sizeOfVect = SIMDBase_getModeParamInt(SIMDBase_PARAMID_SIZE_OF_VECT, mode);
        LOG_WARNING(LOG_TAG, "mode : %d, %s", mode, SIMDBase_getModeParamString(SIMDBase_PARAMID_MODE_NAME, mode));
        LOG_WARNING(LOG_TAG, "n: %d, veclen: %d, sizeOfVect: %d\n", n, veclen, sizeOfVect);

        p = DFT_init(mode, n / veclen, DFT_FLAG_ALT_REAL);

        /* mem */ sx = SIMDBase_alignedMalloc(sizeOfVect*n);
        old_n = n;
    }

    n = n / veclen;

    //memory_zero(sx, sizeOfVect*n);
    /*for(j = 0 ; j < veclen ; j++) {
        for(i = 0 ; i < n ; i++) {
            sx[i * veclen + j] = x[j * n + i];
        }
    }*/

    DFT_execute(p, mode, x, isign);

    //memory_zero(x, n);
    /*for(j = 0 ; j < veclen ; j++) {
        for(i = 0 ; i < n ; i++) {
            x[j * n + i] = sx[i * veclen + j];
        }
    }*/
}
