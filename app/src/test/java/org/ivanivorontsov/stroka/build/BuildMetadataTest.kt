package org.ivanivorontsov.stroka.build

import org.junit.Assert.assertTrue
import org.junit.Test

class BuildMetadataTest {
    @Test
    fun manifestCarriesSelectedVersionAndActivityClass() {
        val xml = RealApkBuilder.manifestXml(
            pkg = "org.example.sample",
            versionName = "2.3.1",
            versionCode = 42,
            activityClass = "MainActivity"
        )
        assertTrue(xml.contains("android:versionCode=\"42\""))
        assertTrue(xml.contains("android:versionName=\"2.3.1\""))
        assertTrue(xml.contains("android:name=\".MainActivity\""))
    }

    @Test
    fun manifestEscapesPackageAndActivityNames() {
        val xml = RealApkBuilder.manifestXml(
            pkg = "org.example.sample",
            versionName = "1.0",
            versionCode = 1,
            activityClass = "org.example.sample.Launcher"
        )
        assertTrue(xml.contains("android:name=\"org.example.sample.Launcher\""))
    }
}
