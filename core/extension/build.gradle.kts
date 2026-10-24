plugins {
    id("momoly.android.library")
}

android {
    namespace = "me.rerere.momoly.core.extension"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network"))
}
