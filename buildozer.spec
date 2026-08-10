[app]
title = PhotosSync
package.name = photossync
package.domain = org.photossync
version = 1.0

source.dir = .
source.include_exts = py,png,jpg

requirements = python3,kivy==2.2.1,requests,watchdog,google-auth-oauthlib

android.permissions = INTERNET,READ_EXTERNAL_STORAGE,WRITE_EXTERNAL_STORAGE,FOREGROUND_SERVICE,POST_NOTIFICATIONS
android.api = 33
android.minapi = 24
android.ndk = 25b
android.sdk = 24
android.accept_sdk_license = True
android.wakelock = True
android.arch = arm64-v8a

[buildozer]
log_level = 2
warn_on_root = 0
