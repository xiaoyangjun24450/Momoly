plugins {
    id("momoly.android.library")
}

android {
    namespace = "me.rerere.momoly.feature.settings"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network"))
    api(libs.kotlinx.coroutines.core)

}
