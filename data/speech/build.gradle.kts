plugins {
    id("momoly.android.library")
}

android {
    namespace = "me.rerere.momoly.data.speech"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:diagnostics"))
    implementation(project(":core:network"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.json)
}
