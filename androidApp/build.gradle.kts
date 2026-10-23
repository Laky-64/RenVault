import io.lauren.renvault.gradle.GoJniLibsTask
import io.lauren.renvault.gradle.RenVaultNativeExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.baselineProfile)
    id("renvault-native")
}

val goJniLibs = extensions.getByType<RenVaultNativeExtension>().androidJniLibs()

fun signingValue(env: String, property: String) =
    providers.environmentVariable(env).orElse(providers.gradleProperty(property))

val signingStore = signingValue("RENVAULT_SIGNING_STORE", "renvault.signing.store")
val signingStorePassword = signingValue("RENVAULT_SIGNING_STORE_PASSWORD", "renvault.signing.store_password")
val signingKeyAlias = signingValue("RENVAULT_SIGNING_KEY_ALIAS", "renvault.signing.key_alias")
val signingKeyPassword = signingValue("RENVAULT_SIGNING_KEY_PASSWORD", "renvault.signing.key_password")

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(goJniLibs, GoJniLibsTask::outputDirectory)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.profileinstaller)

    baselineProfile(project(":baselineProfile"))

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "io.lauren.renvault"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.lauren.renvault"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("renvault.version_code").get().toInt()
        versionName = providers.gradleProperty("renvault.version").get()
        ndk {
            abiFilters += setOf("arm64-v8a", "x86_64")
        }
    }
    signingConfigs {
        if (signingStore.isPresent) {
            create("release") {
                storeFile = file(signingStore.get())
                storePassword = signingStorePassword.get()
                keyAlias = signingKeyAlias.get()
                keyPassword = signingKeyPassword.get()
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}