import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.util.Properties

fun loadAppEnv(rootDir: File): Properties {
  val properties = Properties()

  fun loadIfPresent(filename: String) {
    val candidate = rootDir.resolve(filename)
    if (candidate.exists()) {
      candidate.inputStream().use(properties::load)
    }
  }

  loadIfPresent(".env.example")
  loadIfPresent(".env")
  return properties
}

fun loadVersionProperties(rootDir: File): Properties {
  val properties = Properties()
  val versionFile = rootDir.resolve("version.properties")
  check(versionFile.exists()) { "Missing ${versionFile.path}" }
  versionFile.inputStream().use(properties::load)
  return properties
}

fun escapeBuildConfigValue(value: String): String = value
  .replace("\\", "\\\\")
  .replace("\"", "\\\"")

val appEnv = loadAppEnv(rootDir)
val versionProperties = loadVersionProperties(rootDir)

fun envString(name: String, defaultValue: String = ""): String =
  escapeBuildConfigValue(appEnv.getProperty(name, defaultValue))

fun envInt(name: String, defaultValue: Int): Int =
  appEnv.getProperty(name)?.toIntOrNull() ?: defaultValue

fun signingValue(name: String): String? =
  System.getenv(name) ?: appEnv.getProperty(name)

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.oneimage.android"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.oneimage.android"
    minSdk = 24
    targetSdk = 36
    versionCode = versionProperties.getProperty("VERSION_CODE").toInt()
    versionName = versionProperties.getProperty("VERSION_NAME")

    // Only client-safe keys should be exposed through BuildConfig.
    // Server-only values such as REST_API_ID stay in .env but are intentionally not compiled into the APK.
    buildConfigField("String", "ONEIMAGE_API_BASE_URL", "\"${envString("ONEIMAGE_API_BASE_URL", "https://genstudio.web.app/")}\"")
    buildConfigField("String", "ONEIMAGE_WEB_APP_URL", "\"${envString("ONEIMAGE_WEB_APP_URL", "https://genstudio.web.app/")}\"")
    buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", "\"${envString("ADMOB_REWARDED_AD_UNIT_ID", "ca-app-pub-3940256099942544/5224354917")}\"")
    buildConfigField("int", "REWARDED_AD_CREDIT_AMOUNT", envInt("REWARDED_AD_CREDIT_AMOUNT", 10).toString())
    buildConfigField("int", "ANDROID_STARTER_CREDITS", envInt("ANDROID_STARTER_CREDITS", 5).toString())
    manifestPlaceholders["adMobAppId"] = envString("ADMOB_APP_ID", "ca-app-pub-3940256099942544~3347511713")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = signingValue("KEYSTORE_PATH") ?: "${rootDir}/app/baa.keystore"
      storeFile = file(keystorePath)
      storePassword = signingValue("STORE_PASSWORD")
      keyAlias = signingValue("KEY_ALIAS") ?: "baa"
      keyPassword = signingValue("KEY_PASSWORD") ?: storePassword
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      ndk {
        debugSymbolLevel = "SYMBOL_TABLE"
      }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

googleServices {
  missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN
}


// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.messaging)
  implementation(libs.play.services.ads)
  implementation(libs.play.services.auth)
  implementation(libs.google.webrtc)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.retrofit.converter.serialization)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

abstract class PrintVersionTask : DefaultTask() {
  @get:InputFile
  abstract val versionFile: RegularFileProperty

  @TaskAction
  fun printVersion() {
    val properties = Properties()
    versionFile.get().asFile.inputStream().use(properties::load)
    println("VERSION_NAME=${properties.getProperty("VERSION_NAME")}")
    println("VERSION_CODE=${properties.getProperty("VERSION_CODE")}")
  }
}

abstract class BumpVersionTask : DefaultTask() {
  @get:Input
  abstract val versionPart: Property<String>

  @get:Internal
  abstract val versionFile: RegularFileProperty

  @TaskAction
  fun bumpVersion() {
    val file = versionFile.get().asFile
    val properties = Properties()
    file.inputStream().use(properties::load)
    val currentCode = properties.getProperty("VERSION_CODE").toInt()
    val currentName = properties.getProperty("VERSION_NAME")
    val parts = currentName.split(".").map(String::toInt).toMutableList()

    while (parts.size < 3) {
      parts.add(0)
    }

    when (versionPart.get()) {
      "major" -> {
        parts[0] += 1
        parts[1] = 0
        parts[2] = 0
      }
      "minor" -> {
        parts[1] += 1
        parts[2] = 0
      }
      "patch" -> parts[2] += 1
      else -> error("Unsupported version part: ${versionPart.get()}")
    }

    file.writeText(
      """
      VERSION_CODE=${currentCode + 1}
      VERSION_NAME=${parts.joinToString(".")}
      """.trimIndent() + "\n",
    )
  }
}

tasks.register<PrintVersionTask>("printVersion") {
  group = "versioning"
  description = "Prints the Android versionName and versionCode used for builds."
  versionFile.set(rootProject.layout.projectDirectory.file("version.properties"))
}

tasks.register<BumpVersionTask>("bumpPatchVersion") {
  group = "versioning"
  description = "Increments versionCode and bumps the patch version."
  versionFile.set(rootProject.layout.projectDirectory.file("version.properties"))
  versionPart.set("patch")
  outputs.upToDateWhen { false }
}

tasks.register<BumpVersionTask>("bumpMinorVersion") {
  group = "versioning"
  description = "Increments versionCode and bumps the minor version."
  versionFile.set(rootProject.layout.projectDirectory.file("version.properties"))
  versionPart.set("minor")
  outputs.upToDateWhen { false }
}

tasks.register<BumpVersionTask>("bumpMajorVersion") {
  group = "versioning"
  description = "Increments versionCode and bumps the major version."
  versionFile.set(rootProject.layout.projectDirectory.file("version.properties"))
  versionPart.set("major")
  outputs.upToDateWhen { false }
}
