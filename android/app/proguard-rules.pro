# Release shrinking rules for PayChat.
# Most libraries already ship consumer rules, so keep this file intentionally small.

# Preserve Kotlin coroutine internals needed for clearer cancellation behavior in release.
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
