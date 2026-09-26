# Keep Room entities and Gson models used for JSON (de)serialization via reflection.
-keep class com.simivr.app.data.entity.** { *; }
-keep class com.simivr.app.flow.model.** { *; }
-keep class com.simivr.app.sync.model.** { *; }
-keepattributes Signature,*Annotation*
-dontwarn org.conscrypt.**
