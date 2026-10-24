plugins {
    id("momoly.android.library")
}

android {
    namespace = "me.rerere.momoly.feature.regex"
}

dependencies {
    api(project(":core:model"))
}
