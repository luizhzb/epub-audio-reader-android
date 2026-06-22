plugins { alias(libs.plugins.kotlin.jvm) }

dependencies { implementation(libs.coroutines.core) }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}
