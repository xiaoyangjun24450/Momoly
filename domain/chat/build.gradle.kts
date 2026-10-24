plugins {
    id("momoly.android.library")
}

android {
    namespace = "me.rerere.momoly.domain.chat"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":domain:generation"))
    api(libs.androidx.paging.runtime)
    api(libs.kotlinx.coroutines.core)

}
