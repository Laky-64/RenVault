//go:build darwin && !ios

#import <AppKit/AppKit.h>

void rv_secure_windows(int on) {
    dispatch_async(dispatch_get_main_queue(), ^{
        for (NSWindow *window in [NSApplication sharedApplication].windows) {
            window.sharingType = on ? NSWindowSharingNone : NSWindowSharingReadOnly;
        }
    });
}
