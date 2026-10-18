plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
}
android {
 namespace = "de.muneasy.muniverse"
 compileSdk = 35
 defaultConfig {
  applicationId = "de.muneasy.muniverse"
  minSdk = 30
  targetSdk = 35
  versionCode = 1
  versionName = "0.1.0-alpha"
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 kotlinOptions { jvmTarget = "17" }
}
