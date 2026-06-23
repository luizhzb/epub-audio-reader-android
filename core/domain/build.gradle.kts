plugins { alias(libs.plugins.kotlin.jvm) }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.coroutines.core)
    implementation("javax.inject:javax.inject:1")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}
