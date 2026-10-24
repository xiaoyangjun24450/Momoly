plugins {
    id("momoly.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.rerere.momoly.feature.worldbook"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
}
