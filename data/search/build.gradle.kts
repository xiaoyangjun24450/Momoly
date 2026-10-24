plugins {
    id("momoly.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.rerere.momoly.data.search"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.jsoup)
}
