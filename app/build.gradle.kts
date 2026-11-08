plugins { id("com.android.application") }
android {
  namespace = "com.encisomath.omr"
  compileSdk = 35
  defaultConfig {
    applicationId = "com.encisomath.omr"
    minSdk = 29
    targetSdk = 35
    versionCode = 2
    versionName = "0.1.1"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  sourceSets.getByName("main").assets.srcDir("../web")
}
