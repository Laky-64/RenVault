//go:build darwin && !ios

#import <Foundation/Foundation.h>
#import <LocalAuthentication/LocalAuthentication.h>
#import <Security/Security.h>

int rv_touch_available(void) {
    LAContext *context = [[LAContext alloc] init];
    return [context canEvaluatePolicy:LAPolicyDeviceOwnerAuthenticationWithBiometrics error:nil] ? 1 : 0;
}

int rv_touch_verify(const char *reason, int credential) {
    LAContext *context = [[LAContext alloc] init];
    LAPolicy policy = credential ? LAPolicyDeviceOwnerAuthentication : LAPolicyDeviceOwnerAuthenticationWithBiometrics;
    dispatch_semaphore_t done = dispatch_semaphore_create(0);
    __block int result = 2;
    [context evaluatePolicy:policy
            localizedReason:[NSString stringWithUTF8String:reason]
                      reply:^(BOOL success, NSError *error) {
        if (success) {
            result = 0;
        } else if (error.code == LAErrorUserCancel || error.code == LAErrorSystemCancel ||
                   error.code == LAErrorAppCancel || error.code == LAErrorUserFallback) {
            result = 1;
        }
        dispatch_semaphore_signal(done);
    }];
    dispatch_semaphore_wait(done, DISPATCH_TIME_FOREVER);
    return result;
}

static NSDictionary *rv_query(const char *service, const char *account) {
    return @{
        (__bridge id)kSecClass: (__bridge id)kSecClassGenericPassword,
        (__bridge id)kSecAttrService: [NSString stringWithUTF8String:service],
        (__bridge id)kSecAttrAccount: [NSString stringWithUTF8String:account],
    };
}

int rv_keychain_put(const char *service, const char *account, const void *data, int length) {
    NSDictionary *query = rv_query(service, account);
    SecItemDelete((__bridge CFDictionaryRef)query);
    NSMutableDictionary *item = [query mutableCopy];
    item[(__bridge id)kSecValueData] = [NSData dataWithBytes:data length:(NSUInteger)length];
    item[(__bridge id)kSecAttrAccessible] = (__bridge id)kSecAttrAccessibleWhenUnlockedThisDeviceOnly;
    return (int)SecItemAdd((__bridge CFDictionaryRef)item, NULL);
}

int rv_keychain_get(const char *service, const char *account, void *out, int capacity) {
    NSMutableDictionary *query = [rv_query(service, account) mutableCopy];
    query[(__bridge id)kSecReturnData] = @YES;
    query[(__bridge id)kSecMatchLimit] = (__bridge id)kSecMatchLimitOne;
    CFTypeRef found = NULL;
    OSStatus status = SecItemCopyMatching((__bridge CFDictionaryRef)query, &found);
    if (status != errSecSuccess || found == NULL) {
        return -1;
    }
    NSData *data = (__bridge_transfer NSData *)found;
    if ((int)data.length > capacity) {
        return -1;
    }
    memcpy(out, data.bytes, data.length);
    return (int)data.length;
}
