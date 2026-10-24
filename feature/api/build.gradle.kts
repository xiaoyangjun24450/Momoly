plugins {
    id("momoly.android.library")
}

android {
    namespace = "me.rerere.momoly.feature.api"
}

dependencies {
    api(project(":core:network"))

}
