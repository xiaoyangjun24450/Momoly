plugins {
    id("momoly.android.library")
}

android {
    namespace = "me.rerere.momoly.feature.api"
}

dependencies {
    api(project(":core:network"))
    testImplementation(libs.kotlinx.coroutines.core)

}
