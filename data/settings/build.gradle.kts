plugins {
    id("momoly.android.library")
}

android {
    namespace = "me.rerere.momoly.data.settings"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:network"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.json)

    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}
