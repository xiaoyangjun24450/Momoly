plugins {
    id("momoly.android.library")
}

android {
    namespace = "me.rerere.momoly.feature.translator"
}

dependencies {
    api(project(":core:network"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
}
