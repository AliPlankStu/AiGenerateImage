plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace="com.aliplankstu.aigenerateimage"; compileSdk=35
 defaultConfig { applicationId="com.aliplankstu.aigenerateimage"; minSdk=26; targetSdk=35; versionCode=2; versionName="2.0.0-local" }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget="17" }
}
dependencies {
 implementation("androidx.activity:activity-ktx:1.9.3")
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.documentfile:documentfile:1.0.1")
 implementation("com.google.mediapipe:tasks-vision-image-generator:latest.release")
}
