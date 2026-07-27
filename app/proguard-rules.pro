# ============================================================================
# ProGuard / R8 rules for Embedded Systems Career Guide
#
# WHY THIS FILE MATTERS: build.gradle.kts sets isMinifyEnabled = true for
# release. This app deserialises reflectively in ~47 places -- Gson (LLM JSON
# and the bundled assets) and Firebase (Firestore toObject + Realtime DB) --
# and no model declares @SerializedName. R8 renames backing fields by default,
# so without the rules below a release build silently produces objects with
# all-default values: blank assessment questions, empty quizzes, and every
# challenge participant scoring 0.
#
# Rule of thumb when adding models: anything crossing a JSON or Firebase
# boundary must have its fields kept.
# ============================================================================

# ---------------------------------------------------------------------------
# Attributes required by reflective (de)serialisation
# ---------------------------------------------------------------------------
# Signature is mandatory for Gson TypeToken generics (List<Question> etc.) --
# without it every parameterised type erases and Gson cannot resolve the type.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep readable release stack traces (paired with the mapping file).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile


# ---------------------------------------------------------------------------
# Application models -- Gson AND Firebase both bind these by field name
# ---------------------------------------------------------------------------
# models/: Question, AssessmentReport, QuestionAnswer, LearningStage,
#          UserProgress, StageProgress, LearningStageItem, and every class in
#          models/challenge/ (ChallengeConfig, ParticipantStatus,
#          EvaluationResult, UniversalRanking, RankingEntry, ...).
-keep class com.example.embeddedsystemscareerguide.models.** { *; }

# services/: the DTOs live alongside the services rather than in models/ --
# FirestoreManager (PersonalizedStage, StageContent, QuizResult, DailyTip,
# AnalyticsReport, UserPerformanceData, ...), GeminiChallengeService
# (Challenge1ProblemResponse, EvaluationResponse, CodeBlockResponse, ...),
# GeminiQuizService (QuizQuestion), GeminiChatService (ChatMessage).
# Fields only: class and method names may still be obfuscated.
-keepclassmembers class com.example.embeddedsystemscareerguide.services.** {
    <fields>;
    <init>();
}

# Nested/inner data holders declared inside UI classes (e.g.
# ChatFragment.ChatMessage, UserProgressSyncService.UserProgress).
-keepclassmembers class com.example.embeddedsystemscareerguide.**$* {
    <fields>;
    <init>();
}

# Kotlin data classes expose values through synthetic accessors; keep the
# companion objects that hold the const/factory members.
-keepclassmembers class com.example.embeddedsystemscareerguide.** {
    public static ** Companion;
}


# ---------------------------------------------------------------------------
# Gson
# ---------------------------------------------------------------------------
# Official Gson R8 rules: TypeToken subclasses are anonymous classes whose
# generic supertype must survive, or fromJson() resolves to Object.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Honour @SerializedName if any is added later.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Gson uses Unsafe to instantiate classes without a no-arg constructor.
-dontwarn sun.misc.**


# ---------------------------------------------------------------------------
# Firebase (Auth, Firestore, Realtime Database)
# ---------------------------------------------------------------------------
# Firestore toObject() and RTDB getValue() bind by property name and require a
# no-arg constructor plus getters/setters.
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.PropertyName <fields>;
}
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <methods>;
    @com.google.firebase.database.PropertyName <fields>;
}

# @IgnoreExtraProperties / @Exclude annotated types (ChallengeModels.kt).
-keep @com.google.firebase.database.IgnoreExtraProperties class * { *; }
-keep @com.google.firebase.firestore.IgnoreExtraProperties class * { *; }

-keepnames class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**


# ---------------------------------------------------------------------------
# OkHttp / Okio
# ---------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**


# ---------------------------------------------------------------------------
# Kotlin coroutines
# ---------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**


# ---------------------------------------------------------------------------
# androidx.security (EncryptedSharedPreferences) -- Tink uses reflection
# ---------------------------------------------------------------------------
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**


# ---------------------------------------------------------------------------
# Android framework glue
# ---------------------------------------------------------------------------
# Custom Views inflated from XML are constructed reflectively.
-keepclasseswithmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Parcelable CREATOR fields and enum valueOf/values() are accessed reflectively.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# ---------------------------------------------------------------------------
# Release log stripping
# ---------------------------------------------------------------------------
# 461 Log calls ship in release, several logging user identifiers. Strip debug
# and verbose; keep e/w so crash reports stay diagnosable.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
