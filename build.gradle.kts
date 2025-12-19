import java.io.File
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    // Provides `publishAggregationToCentralPortal`
    id("com.gradleup.nmcp.aggregation") version "1.2.0"

    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("signing")
    id("org.sonarqube") version "5.1.0.4882"
    jacoco
}

/* ---------- nmcp Aggregation (Central Portal) ---------- */
nmcpAggregation {
    centralPortal {
        username = System.getenv("CENTRAL_USERNAME")
        password = System.getenv("CENTRAL_PASSWORD")
        publishingType = "AUTOMATIC"
        // validationTimeout = java.time.Duration.ofMinutes(30)
        // publishingTimeout = java.time.Duration.ofMinutes(30)
    }
    publishAllProjectsProbablyBreakingProjectIsolation()
}

android {
    namespace = "com.example.loginui"
    compileSdk = 35

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug { /* keep for sonar */ }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.extensions.configure<JacocoTaskExtension> {
                isIncludeNoLocationClasses = true
            }
        }
    }
}

group = "io.github.ciscode-ma"
version = "0.1.11"

/* ---------- Publications (consumed by nmcp aggregation) ---------- */
publishing {
    publications {
        create<MavenPublication>("authuiRelease") {
            groupId = "io.github.ciscode-ma"
            artifactId = "authui"
            version = "0.1.11"

            // Include the Android AAR + metadata from the release variant
            afterEvaluate { from(components["release"]) }

            pom {
                name.set("authui")
                description.set("Android authentication UI library")
                url.set("https://github.com/CISCODEAPPS/pkg-android-auth")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                scm {
                    url.set("https://github.com/CISCODEAPPS/pkg-android-auth")
                    connection.set("scm:git:https://github.com/CISCODEAPPS/pkg-android-auth.git")
                    developerConnection.set("scm:git:ssh://git@github.com/CISCODEAPPS/pkg-android-auth.git")
                }
                developers {
                    developer {
                        id.set("ciscode")
                        name.set("CISCODE")
                    }
                }
            }
        }
    }
}

/* ---------- Signing (supports file-based or env-based key) ---------- */
val signingPassword: String? = System.getenv("SIGNING_PASSWORD")

// Preferred: provide SIGNING_KEY_FILE (path to ASCII-armored private key).
// Fallback: SIGNING_KEY (actual ASCII content as env).
val signingKey: String? =
    System.getenv("SIGNING_KEY_FILE")?.let { path ->
        val f = File(path)
        if (f.canRead()) f.readText(Charsets.UTF_8) else null
    } ?: System.getenv("SIGNING_KEY")

signing {
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    } else {
        logger.warn("No signing key provided (SIGNING_KEY_FILE or SIGNING_KEY). Central Portal will reject unsigned artifacts.")
    }
}

/* ---------- Dependencies ---------- */
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.microsoft.identity.client:msal:5.7.0") {
        exclude(group = "com.microsoft.device.display", module = "display-mask")
    }
    implementation("androidx.browser:browser:1.7.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.squareup.okhttp3:okhttp-urlconnection:4.12.0")

    // Unit test deps
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("org.mockito:mockito-core:5.19.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.0.0")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.fragment:fragment-testing:1.8.3")
    testImplementation("androidx.test.ext:junit:1.2.1")
}

/* ---------- JaCoCo ---------- */
jacoco { toolVersion = "0.8.12" }

val excludedClasses = listOf(
    "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    "**/*Dagger*.*", "**/*Hilt*.*", "**/*_MembersInjector*.*",
    "**/*_Factory*.*", "**/*_Provide*Factory*.*",
    "**/*Companion*.*", "**/*Module*.*",
    "**/*Binding.*", "**/*BindingImpl.*",
    "**/*ComposableSingletons*.*"
)

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    val javaDebug = fileTree("${buildDir}/intermediates/javac/debug/classes") { exclude(excludedClasses) }
    val kotlinDebug = fileTree("${buildDir}/tmp/kotlin-classes/debug") { exclude(excludedClasses) }

    classDirectories.setFrom(files(javaDebug, kotlinDebug))
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(buildDir) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "**/jacoco/*.exec",
                "**/*.ec"
            )
        }
    )
}

/* ---------- SonarQube ---------- */
sonarqube {
    properties {
        property("sonar.organization", "ciscode")
        property("sonar.projectKey", "CISCODEAPPS_pkg-android-auth")
        property("sonar.projectName", "pkg-android-auth")
        property("sonar.projectVersion", version.toString())
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.sources", "src/main/java")
        property("sonar.tests", "src/test/java")
        property("sonar.exclusions", "**/R.class, **/R$*.class, **/BuildConfig.*, **/Manifest*.*, **/*Test*.*")
        property("sonar.java.binaries", "build/intermediates/javac/debug/classes,build/tmp/kotlin-classes/debug")
        property("sonar.junit.reportPaths", "build/test-results/testDebugUnitTest")
        property("sonar.androidLint.reportPaths", "build/reports/lint-results-debug.xml")
        property("sonar.coverage.jacoco.xmlReportPaths", "build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml")
    }
}