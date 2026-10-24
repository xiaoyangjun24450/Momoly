plugins {
    id("momoly.android.library")
}

android {
    namespace = "me.rerere.momoly.data.generation"
}

dependencies {
    api(project(":domain:generation"))
    api(project(":core:network"))
}
