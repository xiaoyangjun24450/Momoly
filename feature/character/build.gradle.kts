plugins {
    id("momoly.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.rerere.momoly.feature.character"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:diagnostics"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.json)
}
