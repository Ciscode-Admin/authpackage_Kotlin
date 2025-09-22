plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
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
}

group = "com.ciscod.android"
version = "0.1.0"

/** JaCoCo setup for Android unit tests */
jacoco {
    toolVersion = "0.8.12"
}

// Create a coverage report from the Android unit test task
// Output: build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val debugTree = fileTree(mapOf(
        "dir" to "$buildDir/tmp/kotlin-classes/debug",
        "includes" to listOf("**/*.class"),
        "excludes" to listOf(
            // common noise filters; tweak as you wish
            "**/*\$*",
            "**/R.class", "**/R\$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*"
        )
    ))

    classDirectories.setFrom(debugTree)

    sourceDirectories.setFrom(files(
        "src/main/java",
        "src/main/kotlin"
    ))

    executionData.setFrom(fileTree(buildDir).include(
        "jacoco/testDebugUnitTest.exec",
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
    ))
}

/** Sonar config — works for SonarQube or SonarCloud */
sonarqube {
    properties {
        // Read from Gradle/system properties first, then env (pipeline passes -Dsonar.*; env is fallback)
        val hostUrl = (findProperty("sonar.host.url") as String?)
            ?: System.getenv("SONAR_HOST_URL")
        val token = (findProperty("sonar.login") as String?)
            ?: System.getenv("SONAR_TOKEN")
        val projectKey = (findProperty("sonar.projectKey") as String?)
            ?: System.getenv("SONAR_PROJECT_KEY")
        val organization = (findProperty("sonar.organization") as String?)
            ?: System.getenv("SONAR_ORG") // SonarCloud only

        if (!hostUrl.isNullOrBlank()) property("sonar.host.url", hostUrl)
        if (!token.isNullOrBlank()) property("sonar.login", token)
        if (!projectKey.isNullOrBlank()) property("sonar.projectKey", projectKey)
        if (!organization.isNullOrBlank()) property("sonar.organization", organization)

        // Basic metadata
        property("sonar.projectName", "pkg-android-auth")
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.language", "kotlin")

        // What to analyze
        property("sonar.sources", "src/main/java,src/main/kotlin")
        property("sonar.tests", "src/test/java,src/test/kotlin")

        // Test reports (JUnit)
        property("sonar.junit.reportPaths", "build/test-results/testDebugUnitTest")

        // Coverage (JaCoCo XML)
        property("sonar.coverage.jacoco.xmlReportPaths",
            "build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml")

        // Keep the noise down
        property("sonar.exclusions",
            "**/R.class,**/R$*.class,**/BuildConfig.*,**/Manifest*.*,**/*Test*.*,**/*.png,**/*.jpg,**/*.json")
        property("sonar.test.exclusions", "**/androidTest/**")
    }
}

publishing {
    repositories {
        maven {
            name = "azureArtifacts"
            // Org-scoped feed URL
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