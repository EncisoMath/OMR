plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
}
android {
 namespace = "com.encisomath.roqueomr"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.encisomath.roqueomr"
  minSdk = 26
  targetSdk = 34
  versionCode = 2
  versionName = "1.1.0"
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies {
 implementation("androidx.core:core:1.13.1")
 implementation("androidx.exifinterface:exifinterface:1.3.7")
 implementation("androidx.activity:activity:1.9.3")
 implementation("androidx.camera:camera-core:1.4.2")
 implementation("androidx.camera:camera-camera2:1.4.2")
 implementation("androidx.camera:camera-lifecycle:1.4.2")
 implementation("androidx.camera:camera-view:1.4.2")
}
