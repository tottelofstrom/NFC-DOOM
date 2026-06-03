// Root build script. Plugins are declared here (without applying them) so that
// the versions resolved from the version catalog (gradle/libs.versions.toml)
// are shared by every module. They are applied in :app.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
