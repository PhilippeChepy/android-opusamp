#ifndef H_RFFT
#define H_RFFT

#ifdef __cplusplus
extern "C" {
#endif

void rfft_init();
void rfft(int n,int isign,float *x);

#ifdef __cplusplus
}
#endif

#endif /* H_RFFT */
