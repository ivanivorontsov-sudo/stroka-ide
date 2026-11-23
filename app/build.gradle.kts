plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

val abiList = (project.findProperty("abis") as String? ?: "arm64-v8a,x86_64").split(",").map { it.trim() }.filter { it.isNotEmpty() }
val ksPath: String? = System.getenv("STROKA_KEYSTORE")

val kotlinRuntimeForDevice by configurations.creating
// kotlinc-android embeds JNA classes. Keep the JNA Android AAR (including
// native dispatch libraries for Vosk) and remove only duplicate JNA bytecode
// from the compiler fat JAR before D8 packages the application.
val kotlinCompilerRaw by configurations.creating {
    isTransitive = false
}
val filteredKotlinCompiler by tasks.registering(Jar::class) {
    archiveFileName.set("kotlinc-android-filtered.jar")
    destinationDirectory.set(layout.buildDirectory.dir("generated/kotlinc"))
    from({ zipTree(kotlinCompilerRaw.singleFile) }) {
        exclude("com/sun/jna/**")
    }
}
val stageKotlinRuntime by tasks.registering(Copy::class) {
    from(kotlinRuntimeForDevice)
    include("kotlin-stdlib-2.4.0.jar")
    into(layout.projectDirectory.dir("src/main/assets/libs/kotlin"))
}

android {
    namespace = "org.ivanivorontsov.stroka"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.ivanivorontsov.stroka"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "1.8.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.clear()
            abiFilters += abiList
        }
    }

    signingConfigs {
        create("release") {
            if (ksPath != null && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("STROKA_STORE_PASSWORD")
                keyAlias = System.getenv("STROKA_KEY_ALIAS") ?: "stroka"
                keyPassword = System.getenv("STROKA_KEY_PASSWORD") ?: System.getenv("STROKA_STORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ksPath != null && file(ksPath).exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    packaging {
        resources {
            excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module", "plugin.properties", "about.html")
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        pyc {
            src = false
        }
    }
}

dependencies {
    add(kotlinRuntimeForDevice.name, "org.jetbrains.kotlin:kotlin-stdlib:2.4.0")
    add(kotlinCompilerRaw.name, "com.github.PranavPurwar:kotlinc-android:2.4.0")
    implementation(files(filteredKotlinCompiler.flatMap { it.archiveFile }))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("org.eclipse.jgit:org.eclipse.jgit:5.13.5.202508271544-r")
    implementation("com.android.tools.build:apksig:8.7.3")
    // Android-compatible ECJ batch compiler. The dependency is needed both at\n    // compile time (JavaCompile imports Main) and runtime (the app invokes ECJ).\n    implementation("org.eclipse.jdt:ecj:3.18.0")
    implementation("com.android.tools:r8:8.7.18")
    testImplementation("junit:junit:4.13.2")
    // Use a real JVM JSON implementation in local unit tests instead of the
    // Android framework stubs, whose methods return null in this test setup.
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}

tasks.named("preBuild").configure {
    dependsOn(stageKotlinRuntime, filteredKotlinCompiler)
}


// Guard the compiler dependency before Kotlin compilation. This catches
// repository/classpath regressions with a precise message instead of a pair
// of misleading "Unresolved reference: jdt/Main" diagnostics.
val verifyEcjCompileDependency by tasks.registering {
    doLast {
        val compileClasspath = configurations.getByName("debugCompileClasspath")
        val ecjJar = compileClasspath.files.firstOrNull {
            it.name == "ecj-3.18.0.jar" || it.name.startsWith("ecj-")
        } ?: throw GradleException(
            "ECJ is missing from debugCompileClasspath. Resolved files: " +
                compileClasspath.files.joinToString { it.name }
        )
        val mainClass = zipTree(ecjJar).matching {
            include("org/eclipse/jdt/internal/compiler/batch/Main.class")
        }.files
        check(mainClass.isNotEmpty()) {
            "Resolved ECJ artifact does not contain org/eclipse/jdt/internal/compiler/batch/Main.class: $ecjJar"
        }
        logger.lifecycle("ECJ compile dependency verified: $ecjJar")
    }
}

tasks.matching { it.name == "compileDebugKotlin" }.configureEach {
    dependsOn(verifyEcjCompileDependency)
}
