# ImplantDoom ProGuard / R8 rules.
#
# The app is intentionally minimal and does not currently enable minification
# (see app/build.gradle.kts -> buildTypes.release.isMinifyEnabled = false), so
# these rules are mostly documentation for when shrinking is turned on later.

# Keep the cartridge model + codec readable for crash reports.
-keepnames class com.implantdoom.cartridge.** { *; }
