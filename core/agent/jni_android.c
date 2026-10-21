#include "jni_android.h"

#include <stdlib.h>
#include <string.h>

static JavaVM *renvault_vm;
static jclass renvault_advertiser;
static jclass renvault_scanner;

static JNIEnv *renvault_attach(int *attached) {
	JNIEnv *env = NULL;
	*attached = 0;
	if (renvault_vm == NULL) {
		return NULL;
	}
	if ((*renvault_vm)->GetEnv(renvault_vm, (void **)&env, JNI_VERSION_1_6) == JNI_OK) {
		return env;
	}
	if ((*renvault_vm)->AttachCurrentThread(renvault_vm, &env, NULL) != JNI_OK) {
		return NULL;
	}
	*attached = 1;
	return env;
}

static void renvault_detach(int attached) {
	if (attached && renvault_vm != NULL) {
		(*renvault_vm)->DetachCurrentThread(renvault_vm);
	}
}

char *renvault_copy_jstring(JNIEnv *env, jstring value) {
	if (value == NULL) {
		return NULL;
	}
	const char *chars = (*env)->GetStringUTFChars(env, value, NULL);
	if (chars == NULL) {
		return NULL;
	}
	char *out = strdup(chars);
	(*env)->ReleaseStringUTFChars(env, value, chars);
	return out;
}

void Java_com_wails_app_RenVaultAdvertiser_nativeRegister(JNIEnv *env, jclass clazz) {
	(*env)->GetJavaVM(env, &renvault_vm);
	renvault_advertiser = (jclass)(*env)->NewGlobalRef(env, clazz);
}

void Java_com_wails_app_RenVaultScanner_nativeRegister(JNIEnv *env, jclass clazz) {
	(*env)->GetJavaVM(env, &renvault_vm);
	renvault_scanner = (jclass)(*env)->NewGlobalRef(env, clazz);
}

static char *renvault_call(jclass target, const char *name, const char *sig, jobject arg) {
	int attached = 0;
	JNIEnv *env = renvault_attach(&attached);
	if (env == NULL || target == NULL) {
		return strdup("the android bridge is not registered");
	}
	jmethodID method = (*env)->GetStaticMethodID(env, target, name, sig);
	if (method == NULL) {
		(*env)->ExceptionClear(env);
		renvault_detach(attached);
		return strdup("the android bridge method is missing");
	}
	jstring result = arg == NULL
		? (jstring)(*env)->CallStaticObjectMethod(env, target, method)
		: (jstring)(*env)->CallStaticObjectMethod(env, target, method, arg);
	if ((*env)->ExceptionCheck(env)) {
		(*env)->ExceptionClear(env);
		renvault_detach(attached);
		return strdup("the android bridge threw an exception");
	}
	char *out = renvault_copy_jstring(env, result);
	if (result != NULL) {
		(*env)->DeleteLocalRef(env, result);
	}
	renvault_detach(attached);
	return out;
}

char *renvault_advertise_start(const void *advert, int size) {
	int attached = 0;
	JNIEnv *env = renvault_attach(&attached);
	if (env == NULL || renvault_advertiser == NULL) {
		return strdup("the android bridge is not registered");
	}
	jbyteArray array = (*env)->NewByteArray(env, size);
	if (array == NULL) {
		renvault_detach(attached);
		return strdup("cannot allocate the advert");
	}
	(*env)->SetByteArrayRegion(env, array, 0, size, (const jbyte *)advert);
	char *out = renvault_call(renvault_advertiser, "start", "([B)Ljava/lang/String;", array);
	(*env)->DeleteLocalRef(env, array);
	renvault_detach(attached);
	return out;
}

char *renvault_advertise_stop(void) {
	return renvault_call(renvault_advertiser, "stop", "()Ljava/lang/String;", NULL);
}

char *renvault_scan_start(void) {
	return renvault_call(renvault_scanner, "start", "()Ljava/lang/String;", NULL);
}
