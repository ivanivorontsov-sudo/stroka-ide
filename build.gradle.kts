plugins {
    id("com.android.application") version "8.7.3" apply false
    // Keep the Gradle plugin metadata reader aligned with the bundled
    // kotlinc-android 2.4.x compiler used by the on-device Kotlin toolchain.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("com.chaquo.python") version "16.1.0" apply false
}
