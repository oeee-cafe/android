# R8 keeps what the app needs without any rule of the app's own, which is why there are none.
#
# - What the manifest names -- the activity, the application, the messaging service, the file
#   provider -- is kept by the rules AAPT writes from it.
# - Nothing is found by name or by reflection: the bridge's messages are read with org.json
#   (BridgeMessage), and the site is heard through a web message listener (SiteBridge), not
#   addJavascriptInterface, so there is no @JavascriptInterface method for R8 to rename.
# - Firebase Messaging, Sentry, Credential Manager (which finds its Play services provider by
#   name) and Kotlin's coroutines each ship the rules they need in their own AARs.
# - Sentry's keep the line numbers and source file names its stack traces need, and the
#   Sentry Gradle plugin uploads the mapping that turns the rest back into names.
