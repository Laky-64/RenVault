import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.test")
    alias(libs.plugins.baselineProfile)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

android {
    namespace = "io.lauren.renvault.baseline"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        minSdk = 28
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val secret = File(System.getProperty("user.home"), ".renvault-test-pw")
        if (secret.isFile) {
            testInstrumentationRunnerArguments["vaultPassword"] = secret.readText().trim()
        }
    }

    targetProjectPath = ":androidApp"

    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macroJunit4)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.test.runner)
    implementation(libs.junit)
}
