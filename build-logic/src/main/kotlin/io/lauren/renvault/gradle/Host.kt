package io.lauren.renvault.gradle

import org.gradle.internal.os.OperatingSystem

val hostGoos: String
    get() = with(OperatingSystem.current()) {
        when {
            isWindows -> "windows"
            isMacOsX -> "darwin"
            else -> "linux"
        }
    }

val hostGoarch: String
    get() = when (val arch = System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "arm64"
        "amd64", "x86_64" -> "amd64"
        else -> arch
    }

val hostLibraryName: String
    get() = when (hostGoos) {
        "windows" -> "renvault.dll"
        "darwin" -> "librenvault.dylib"
        else -> "librenvault.so"
    }

val ndkHostTag: String
    get() = when (hostGoos) {
        "windows" -> "windows-x86_64"
        "darwin" -> "darwin-x86_64"
        else -> "linux-x86_64"
    }

val jniHeaderDirectory: String
    get() = when (hostGoos) {
        "windows" -> "win32"
        "darwin" -> "darwin"
        else -> "linux"
    }
