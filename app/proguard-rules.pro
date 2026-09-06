# Room and Firebase keep their own consumer rules. Add app specific rules here.

# Model classes are reflected over by Firestore.
-keep class com.paychat.paychat.data.remote.dto.** { *; }
-keepclassmembers class com.paychat.paychat.data.remote.dto.** { <init>(); }
