plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("org.sonarqube") version "5.1.0.4882" // SonarQube Gradle plugin
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
        debug {
            // keep debug so we can point Sonar to debug outputs
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Use the same JaCoCo version everywhere
    testOptions.unitTests.all {
        it.extensions.configure<JacocoTaskExtension> {
            isIncludeNoLocationClasses = true
        }
    }
}

group = "io.github.ciscode-ma"
version = "0.1.2"

publishing {
    repositories {
        // Publish to Sonatype (Maven Central)
        maven {
            name = "sonatype"
            url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("SONATYPE_USERNAME")
                password = System.getenv("SONATYPE_PASSWORD")
            }
        }
    }
    publications {
        create<MavenPublication>("authuiRelease") {
            groupId = "io.github.ciscode-ma"
            artifactId = "authui"
            version = "0.1.2"
            afterEvaluate { from(components["release"]) }
            pom {
                name.set("authui")
                description.set("Android authentication UI library")
                // (Optional) If you already had these in 0.1.1 via gradle.properties, you can omit here.
                // licenses { license { name.set("The Apache License, Version 2.0"); url.set("http://www.apache.org/licenses/LICENSE-2.0.txt") } }
                // scm { url.set("https://github.com/<your-org>/<your-repo>") }
                // developers { developer { id.set("ciscode"); name.set("CISCODE") } }
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

/* ---------- JaCoCo coverage for unit tests ---------- */
jacoco {
    toolVersion = "0.8.12"
}

val excludedClasses = listOf(
    // android/generated
    "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
    // Dagger/Hilt / MembersInjector etc.
    "**/*Dagger*.*", "**/*Hilt*.*", "**/*_MembersInjector*.*",
    "**/*_Factory*.*", "**/*_Provide*Factory*.*",
    // Kotlin synthetics/companions
    "**/*Companion*.*", "**/*Module*.*",
    // ViewBinding/DataBinding
    "**/*Binding.*", "**/*BindingImpl.*",
    // Jetpack Compose (if any)
    "**/*ComposableSingletons*.*"
    // NOTE: classic ButterKnife pattern left out; if you need it, escape $ like: "**/*\\$ViewInjector*.*"
)

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val javaDebug = fileTree("${buildDir}/intermediates/javac/debug/classes") {
        exclude(excludedClasses)
    }
    val kotlinDebug = fileTree("${buildDir}/tmp/kotlin-classes/debug") {
        exclude(excludedClasses)
    }

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

/* ---------- SonarQube configuration ---------- */
sonarqube {
    properties {
        // SonarCloud identifiers
        property("sonar.organization", "ciscode")
        property("sonar.projectKey", "CISCODEAPPS_pkg-android-auth")
        property("sonar.projectName", "pkg-android-auth")
        property("sonar.projectVersion", version.toString())
        property("sonar.host.url", "https://sonarcloud.io")

        // Sources / tests
        property("sonar.sources", "src/main/java")
        property("sonar.tests", "src/test/java") // removed src/test/kotlin (it doesn't exist)

        // Exclusions
        property("sonar.exclusions", "**/R.class, **/R$*.class, **/BuildConfig.*, **/Manifest*.*, **/*Test*.*")

        // Binaries (Debug)
        property(
            "sonar.java.binaries",
            "build/intermediates/javac/debug/classes,build/tmp/kotlin-classes/debug"
        )

        // Test & Lint reports
        property("sonar.junit.reportPaths", "build/test-results/testDebugUnitTest")
        property("sonar.androidLint.reportPaths", "build/reports/lint-results-debug.xml")

        // Jacoco XML report
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml"
        )
    }
}