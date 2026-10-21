plugins {
    `kotlin-dsl`
}

group = "io.lauren.renvault.gradle"

gradlePlugin {
    plugins {
        register("renvaultNative") {
            id = "renvault-native"
            implementationClass = "io.lauren.renvault.gradle.RenVaultNativePlugin"
        }
    }
}
