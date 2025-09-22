plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")

    // ⬇️ Add these two
    id("org.sonarqube") version "4.4.1.3373"
    jacoco
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
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    publishing {
        singleVariant("release") { withSourcesJar() }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

group = "com.ciscod.android"
version = "0.1.0"

publishing {
    repositories {
        maven {
            name = "azureArtifacts"
            url = uri("https://pkgs.dev.azure.com/CISCODEAPPS/_packaging/android-packages/maven/v1")
            credentials {
                username = System.getenv("AZURE_ARTIFACTS_USERNAME") ?: "azdo"
                password = System.getenv("AZURE_ARTIFACTS_TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("authuiRelease") {
            groupId = "com.ciscod.android"
            artifactId = "authui"
            version = "0.1.0"
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("authui")
                description.set("Android authentication UI library")
            }
        }
    }
}

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

    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("org.mockito:mockito-core:5.19.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.0.0")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.fragment:fragment-testing:1.8.3")
    testImplementation("androidx.test.ext:junit:1.2.1")
}

/* ---------- Code coverage (Jacoco) ---------- */
jacoco {
    // Java 21 compatible
    toolVersion = "0.8.11"
}

// Report for JVM unit tests (Debug)
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val javaClasses = fileTree("$buildDir/intermediates/javac/debug/classes") {
        exclude(
            "**/R.class",
            "**/R\$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*\$ViewInjector*.*",
            "**/*_MembersInjector*.*",
            "**/*_Factory*.*",
            "**/*\$Companion*"
        )
    }

    val kotlinClasses = fileTree("$buildDir/tmp/kotlin-classes/debug") {
        exclude(
            "**/R.class",
            "**/R\$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*\$ViewInjector*.*",
            "**/*_MembersInjector*.*",
            "**/*_Factory*.*",
            "**/*\$Companion*"
        )
    }

    classDirectories.setFrom(javaClasses, kotlinClasses)

    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))

    // Pick up execution data robustly (AGP can place it in different spots)
    executionData.setFrom(
        fileTree(buildDir) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "jacoco/test.exec"
            )
        }
    )
}

/* ---------- SonarQube ---------- */
sonarqube {
    properties {
        // CHANGE these to match your SonarQube project
        property("sonar.projectKey", "pkg-android-auth:authui")
        property("sonar.projectName", "authui")
        property("sonar.projectVersion", version.toString())

        // Sources/tests
        property("sonar.sources", "src/main/java,src/main/kotlin")
        property("sonar.tests", "src/test/java,src/test/kotlin")
        property("sonar.exclusions", "**/R.class, **/R$*.class, **/BuildConfig.*, **/Manifest*.*, **/*Test*.*")

        // Binaries (both Java & Kotlin output for Debug)
        property("sonar.java.binaries",
            "build/intermediates/javac/debug/classes,build/tmp/kotlin-classes/debug"
        )

        // JUnit & Lint reports
        property("sonar.junit.reportPaths", "build/test-results/testDebugUnitTest")
        property("sonar.androidLint.reportPaths", "build/reports/lint-results-debug.xml")

        // Jacoco XML report
        property("sonar.coverage.jacoco.xmlReportPaths",
            "build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml"
        )
    }
}