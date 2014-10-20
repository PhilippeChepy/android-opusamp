#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <stdint.h>

#include <audio_engine/utils/log.h>
#include <audio_engine/processor/fft/simd_base.h>
#include <audio_engine/processor/fft/dft.h>

#define LOG_TAG "(jni)audio_processor:shibatch_fft.c"

typedef float REAL;
#define TYPE SIMDBase_TYPE_FLOAT

void rfft(int n,int isign,float *x) {
    static DFT *p = NULL;
    static float *buf = NULL;
    static int ipsize = 0;
    static int mode = 0;
    static int veclen = 0;
    int newipsize;

    if (n == 0) {
        if (buf) {
            SIMDBase_alignedFree (buf);
            buf = NULL;
        }
        if (p) {
            DFT_dispose(p, mode);
            p = NULL;
        }

        return;
    }

    int nn = n;
    n = 1<<n;
    newipsize = n;
    if (newipsize != ipsize) {
        ipsize = newipsize;

        if (buf) {
            SIMDBase_alignedFree (buf);
            buf = NULL;
        }

        if (p) {
            DFT_dispose(p, mode);
            p = NULL;
        }

        buf = SIMDBase_alignedMalloc (n * sizeof (float));

        mode = SIMDBase_chooseBestMode(TYPE);
        LOG_WARNING(LOG_TAG, "mode : %d, %s", mode, SIMDBase_getModeParamString(SIMDBase_PARAMID_MODE_NAME, mode));

        veclen = SIMDBase_getModeParamInt(SIMDBase_PARAMID_VECTOR_LEN, mode);
        int sizeOfVect = SIMDBase_getModeParamInt(SIMDBase_PARAMID_SIZE_OF_VECT, mode);
        LOG_WARNING(LOG_TAG, "n: %d, veclen: %d, sizeOfVect: %d\n", n, veclen, sizeOfVect);

        p = DFT_init(mode, n/veclen, DFT_FLAG_REAL);
    }

    // store in simd order
    int asize = n / veclen;
    int i, j;
    for(j=0;j<veclen;j++) {
        for (i = 0; i < asize; i++) {
            buf[i * veclen + j] = x[j * asize + i];
        }
    }

    DFT_execute(p, mode, x, isign);

#define THRES 1e-3
    for(j=0;j<veclen;j++) {
        for (i = 0; i < asize; i++) {
            x[j * asize + i] = buf[i * veclen + j];
        }
    }
}
