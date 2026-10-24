plugins {
    id("momoly.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "me.rerere.momoly.data.backup"
}

dependencies {
    implementation(project(":core:diagnostics"))
    implementation(project(":core:model"))
    implementation(project(":data:chat"))
    implementation(project(":data:resources"))
    implementation(project(":data:settings"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
