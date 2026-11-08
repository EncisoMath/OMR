plugins { id("com.android.application") }
android {
  namespace = "com.encisomath.omr"
  compileSdk = 35
  defaultConfig {
    applicationId = "com.encisomath.omr"
    minSdk = 29
    targetSdk = 35
    versionCode = 3
    versionName = "0.1.2"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  sourceSets.getByName("main").assets.srcDir("../web")
}
dependencies {
  implementation("androidx.activity:activity:1.9.3")
  implementation("androidx.camera:camera-core:1.4.2")
  implementation("androidx.camera:camera-camera2:1.4.2")
  implementation("androidx.camera:camera-lifecycle:1.4.2")
  implementation("androidx.camera:camera-view:1.4.2")
  implementation("androidx.exifinterface:exifinterface:1.3.7")
}
