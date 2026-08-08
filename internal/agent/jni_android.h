#ifndef RENVAULT_JNI_ANDROID_H
#define RENVAULT_JNI_ANDROID_H

#include <jni.h>

char *renvault_advertise_start(const void *advert, int size);
char *renvault_advertise_stop(void);
char *renvault_scan_start(void);
char *renvault_copy_jstring(JNIEnv *env, jstring value);

#endif
