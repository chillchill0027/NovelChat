# NovelChat Release ProGuard / R8 Rules

# Keep Gson models & generic type signatures
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-keep class com.novelchat.data.model.** { *; }
-keep class com.novelchat.data.remote.model.** { *; }
-keep class com.novelchat.domain.v2.state.** { *; }
-keep class com.novelchat.data.repo.** { *; }

# Keep resource anchor used to load bundled stories/shendiao_v1.json
-keep class com.novelchat.data.model.DefaultStoryResourceAnchor { *; }

# Keep Retrofit service interfaces
-keep interface com.novelchat.data.remote.api.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
