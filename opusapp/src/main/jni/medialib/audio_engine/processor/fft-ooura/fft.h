#ifndef H_FFT
#define H_FFT

#ifdef __cplusplus
extern "C" {
#endif

#include <audio_engine/utils/real.h>

void cdft(int n, int isgn, real_t *a, int *ip, real_t *w);
void rdft(int n, int isgn, real_t *a, int *ip, real_t *w);
void ddct(int n, int isgn, real_t *a, int *ip, real_t *w);
void ddst(int n, int isgn, real_t *a, int *ip, real_t *w);
void dfct(int n, real_t *a, real_t *t, int *ip, real_t *w);
void dfst(int n, real_t *a, real_t *t, int *ip, real_t *w);

void rfft(int n,int isign,real_t x[]);

#ifdef __cplusplus
}
#endif

#endif /* H_FFT */
