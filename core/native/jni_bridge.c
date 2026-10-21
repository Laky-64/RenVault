#include "jni_bridge.h"

#include <string.h>

int32_t rv_jni_len(JNIEnv *env, jbyteArray array) {
	if (array == NULL) {
		return 0;
	}
	return (int32_t)(*env)->GetArrayLength(env, array);
}

void rv_jni_read(JNIEnv *env, jbyteArray array, uint8_t *dst, int32_t len) {
	if (array == NULL || dst == NULL || len <= 0) {
		return;
	}
	(*env)->GetByteArrayRegion(env, array, 0, len, (jbyte *)dst);
}

jbyteArray rv_jni_new(JNIEnv *env, const uint8_t *src, int32_t len) {
	jbyteArray array = (*env)->NewByteArray(env, len);
	if (array == NULL) {
		return NULL;
	}
	if (src != NULL && len > 0) {
		(*env)->SetByteArrayRegion(env, array, 0, len, (const jbyte *)src);
	}
	return array;
}
