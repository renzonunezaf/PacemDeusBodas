// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // FCM push notifications: requiere google-services.json en app/
    alias(libs.plugins.google.services) apply false
}
