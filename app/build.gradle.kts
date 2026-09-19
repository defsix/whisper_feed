plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

allprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
}

android {
    namespace = "com.saulhdev.feeder"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.zero76.whisper"
        minSdk = 26
        targetSdk = 37
        // Whisper's own first release, not Neo Feed's ninth. The package
        // io.zero76.whisper has never been published, so there is no version
        // history to preserve and nothing to keep monotonic against — 1.9.0
        // would have claimed eight earlier releases that do not exist.
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        multiDexEnabled = true
    }

    signingConfigs {
        /**
         * The test-signing key, held in the repository on purpose.
         *
         * Without this block AGP falls back to `~/.android/debug.keystore`,
         * which is generated per machine — so a debug or preview APK built
         * here and one built on a laptop carry different signatures, and
         * Android refuses to install one over the other. A tester handed a
         * second build would have to uninstall the first and lose their feeds.
         * `app/debug.keystore` was already committed, inherited from upstream,
         * and simply never wired up; that is what it is for.
         *
         * Committing a key is normally indefensible. This one is the standard
         * Android debug key — `android`/`androiddebugkey`, the same credentials
         * every SDK install uses — so it is already public knowledge and grants
         * nobody anything they did not have. Every Android project's debug key
         * is effectively shared.
         *
         * It must never sign a release. It is public, so anyone could then
         * publish an update to a real app. The release build type deliberately
         * declares no signingConfig for exactly that reason: `assembleRelease`
         * produces an unsigned APK, and signing it is a separate, local step
         * with a key that never comes near this repository.
         */
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".dev"
            signingConfig = signingConfigs.getByName("debug")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        /**
         * The shippable build, made installable for testing.
         *
         * Minified and resource-shrunk exactly as release is — so it exercises
         * R8, which is the only thing that can break the reflective Moshi and
         * Google Reader models, and which a debug build never runs — but signed
         * with the debug key and carrying the same `.dev` application id as
         * debug, so it installs straight over a debug build already on the
         * phone instead of arriving as a second app.
         *
         * A debug APK is 31 MB of unminified dex; this is about 9. That is the
         * difference between a build that can be handed over and one that
         * cannot.
         *
         * Not a release: the debug signing key is public and every phone
         * already trusts it. Never distribute one of these.
         */
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".dev"
            signingConfig = signingConfigs.getByName("debug")
            // The google-gsa module has only debug and release; without this
            // Gradle cannot decide which of them a "preview" app should use.
            matchingFallbacks += listOf("release")
        }
        all {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    sourceSets {
        getByName("main") {
            java.directories.add("src/main/java")
            aidl.directories.add("src/main/aidl")
            assets.directories.add("src/main/assets")
            res.directories.add("src/main/res")
        }
    }

    compileOptions {
        sourceCompatibility(libs.versions.jvmVersion.get())
        targetCompatibility(libs.versions.jvmVersion.get())
    }

    buildFeatures {
        compose = true
        // dataBinding and viewBinding were both on and neither was used: no
        // layout file has a <layout> root, and nothing in the source names a
        // generated *Binding class. Each one adds a code-generation pass to
        // every build for classes nobody references.
        buildConfig = true
        aidl = true
    }

    packaging {
        resources.pickFirsts.add("rome-utils-2.1.0.jar")
    }

    lint {
        // The report is clean, so it can be a gate rather than a document.
        // With this off, a lint error changed nothing about the build and was
        // only ever found by somebody reading the report on purpose — which is
        // how an API-28 call in a minSdk-26 app, a crash on every Android 8
        // phone, sat in it unnoticed.
        abortOnError = true
        checkReleaseBuilds = true
        disable += listOf(
            "MissingTranslation",
            "ExtraTranslation",
            // Every site is a context.getString inside a coroutine or a
            // callback — a snackbar being shown, a subscription being written
            // — not a value read during composition. The check cannot tell
            // those apart and flags all of them, and seven standing errors
            // hide the ones that matter: an API-28 call on a minSdk-26 app sat
            // in this report behind them for a day.
            "LocalContextGetResourceValueCall",
            // Verified guarded: the receiver is registered with
            // RECEIVER_EXPORTED above TIRAMISU and without the flag below it,
            // which is correct. Lint reads only the else branch.
            "UnspecifiedRegisterReceiverFlag",
        )
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val versionName = output.versionName.get()
            output.outputFileName.set("Whisper_${versionName}_${variant.name}.apk")
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvmVersion.get().toInt())
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(":google-gsa"))

    implementation(libs.symbol.processing.api)
    implementation(libs.stdlib)
    implementation(libs.serialization.json)

    //Core
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.multidex)
    implementation(libs.swiperefreshlayout)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datetime)
    implementation(libs.material)
    implementation(libs.browser)
    implementation(libs.materialkolor)
    implementation(libs.collections.immutable)

    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.common.java8)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.viewmodel.ktx)

    //Compose
    api(platform(libs.compose.bom))
    implementation(libs.compose.animation)
    implementation(libs.compose.foundation)
    implementation(libs.compose.foundation.layout)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.navigationsuite)
    implementation(libs.compose.adaptive)
    implementation(libs.compose.adaptive.layout)
    implementation(libs.compose.adaptive.navigation)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.navigation.compose)

    //Room
    implementation(libs.room.ktx)
    implementation(libs.room.paging)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    //Security
    implementation(libs.security.crypto)
    implementation(libs.documentfile)

    //Squareup
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.retrofit) { exclude(module = "okhttp") }
    implementation(libs.retrofit.converter.gson)

    //Coil
    implementation(libs.coil)
    implementation(libs.coil.compose)

    //Koin
    api(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.startup)
    implementation(libs.koin.annotations)
    ksp(libs.koin.compiler)
    testImplementation(libs.koin.test)

    //Libs
    implementation(libs.threetenabp)
    implementation(libs.rome) { exclude(module = "rome-utils") }
    implementation(libs.rome.modules)
    implementation(libs.simple.storage)
    implementation(libs.readability4j)
    implementation(libs.tagsoup)
    implementation(libs.jsoup)
    implementation(libs.slf4j)

    // Test
    testImplementation(libs.test.runner)
    testImplementation(libs.test.rules)
    testImplementation(libs.test.ext)
}