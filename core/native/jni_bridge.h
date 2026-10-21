#ifndef RENVAULT_JNI_BRIDGE_H
#define RENVAULT_JNI_BRIDGE_H

#include <jni.h>
#include <stdint.h>

int32_t rv_jni_len(JNIEnv *env, jbyteArray array);
void rv_jni_read(JNIEnv *env, jbyteArray array, uint8_t *dst, int32_t len);
jbyteArray rv_jni_new(JNIEnv *env, const uint8_t *src, int32_t len);

#endif
