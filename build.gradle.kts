plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish") // keep for Azure Artifacts publication
    id("com.vanniktech.maven.publish") version "0.34.0" // for Maven Central

    // Sonar + coverage
    id("org.sonarqube") version "5.1.0.4882"
    jacoco
}

import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.api.tasks.testing.Test

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
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
        debug { /* keep */ }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }

    publishing {
        // Keep sources jar for Azure + Central
        singleVariant("release") { withSourcesJar() }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                "--add-opens=java.base/sun.util.calendar=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.reflect=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.reflect=ALL-UNNAMED",
                "--add-exports=java.base/sun.nio.ch=ALL-UNNAMED"
            )
        }
    }
}

// ==== Coordinates for Maven Central ====
group = "io.github.ciscode-ma"
version = "0.1.0"

// Vanniktech config for Central (creates sources/javadoc jars & signs)
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates("io.github.ciscode-ma", "authui", "0.1.0")
    pom {
        name.set("authui")
        description.set("Android authentication UI library (AAR).")
        inceptionYear.set("2025")
        url.set("https://github.com/CISCODE-MA/authpackage_Kotlin")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("ciscode-ma")
                name.set("CISCODE-MA")
                url.set("https://github.com/CISCODE-MA")
            }
        }
        scm {
            url.set("https://github.com/CISCODE-MA/authpackage_Kotlin")
            connection.set("scm:git:https://github.com/CISCODE-MA/authpackage_Kotlin.git")
            developerConnection.set("scm:git:ssh://git@github.com/CISCODE-MA/authpackage_Kotlin.git")
        }
    }
}

// ==== Keep your Azure Artifacts publishing as-is ====
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
            groupId = "com.ciscod.android"      // stays for Azure feed path
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

/* ---------- JaCoCo coverage for unit tests ---------- */
jacoco { toolVersion = "0.8.12" }

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        xml.outputLocation.set(
            layout.buildDirectory.file("reports/jacoco/jacocoTestReport/jacocoTestReport.xml")
        )
    }

    val excluded = listOf(
        "**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*",
        "**/*Dagger*.*", "**/*Hilt*.*", "**/*_MembersInjector*.*",
        "**/*_Factory*.*", "**/*_Provide*Factory*.*",
        "**/*Companion*.*", "**/*Module*.*",
        "**/*Binding.*", "**/*BindingImpl.*", "**/*ComposableSingletons*.*"
    )

    val javaDebug = fileTree("${buildDir}/intermediates/javac/debug/classes") { exclude(excluded) }
    val kotlinDebug = fileTree("${buildDir}/tmp/kotlin-classes/debug") { exclude(excluded) }

    classDirectories.setFrom(files(javaDebug, kotlinDebug))
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(buildDir) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "**/jacoco/*.exec", "**/*.ec"
            )
        }
    )
}

tasks.named("sonarqube") { dependsOn("jacocoTestReport") }

/* ---------- SonarQube / SonarCloud ---------- */
sonarqube {
    properties {
        property("sonar.organization", "ciscode")
        property("sonar.projectKey", "CISCODEAPPS_pkg-android-auth")
        property("sonar.projectName", "pkg-android-auth")
        property("sonar.projectVersion", version.toString())
        property("sonar.host.url", "https://sonarcloud.io")

        val sourceDirs = listOf("src/main/java", "src/main/kotlin").filter { file(it).exists() }
        val testDirs   = listOf("src/test/java", "src/test/kotlin").filter { file(it).exists() }

        property("sonar.sources", sourceDirs.joinToString(","))
        property("sonar.tests",   testDirs.joinToString(","))

        property("sonar.exclusions",
            "**/R.class, **/R$*.class, **/BuildConfig.*, **/Manifest*.*, **/*Test*.*"
        )

        property("sonar.java.binaries",
            "build/intermediates/javac/debug/classes,build/tmp/kotlin-classes/debug"
        )

        property("sonar.junit.reportPaths", "build/test-results/testDebugUnitTest")

        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            "build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml"
        )
    }
}