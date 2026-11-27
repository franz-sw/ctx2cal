# R8 / ProGuard rules for the androidApp module.
#
# How shrinking works with the Kotlin Toolchain (Amper):
# - Release builds enable R8 automatically (minify + shrink + resource shrink),
#   equivalent to Gradle's isMinifyEnabled = true / isShrinkResources = true
#   with proguard-android-optimize.txt. There is no YAML toggle in module.yaml.
# - Debug builds are NOT shrunk or obfuscated, so `kotlin run` stays debuggable.
# - This file is picked up automatically because it sits in the module folder
#   as proguard-rules.pro. A copy elsewhere (e.g. a root-level proguard.txt)
#   is ignored — keep rules only in this file.
# - Every library ships its own consumer rules which R8 always merges. The
#   effective configuration of the last release run can be inspected in
#   build/tasks/_androidApp_buildAndroidRelease/**/outputs/mapping/release/configuration.txt
#   (mapping.txt / seeds.txt / usage.txt live next to it).
#
# Why this file has no active rules:
# Each dependency was verified against its shipped rules, and R8 reachability
# covers the rest. Broad keeps would only bloat the DEX and slow startup, so
# add rules here only for code that reflection/instantiation-by-name loads.
#
# Consumer rules that already protect this app (do NOT duplicate):
# - kotlinx-serialization (NavKey routes + CalendarEventDraft): keeps
#   @Serializable Companion/serializer()/INSTANCE via its META-INF rules.
#   Verified: seeds.txt lists ExtractRoute / ModelSetup / ModelTest and
#   CalendarEventDraft serializers.
# - ML Kit GenAI (com.google.mlkit:genai-prompt): its AAR proguard.txt keeps
#   all fields of generated proto classes (zzbaa) that are read via
#   reflection. This used to be hand-copied into a root proguard.txt; the
#   AAR rule is merged automatically, so that copy was removed.
# - kotlinx-coroutines: ServiceLoader + volatile-field keeps.
# - kotlin-reflect (pulled in by LiteRT-LM): keeps kotlin.Metadata and
#   runtime-visible annotations.
# - Gson (pulled in by LiteRT-LM for its tool JSON): keeps @SerializedName
#   fields and adapter constructors for referenced classes.
# - Compose / AndroidX: their proguard.txt files are "safe to shrink"
#   markers; the Android defaults keep manifest components, Views, enums,
#   and native-method classes (LiteRT JNI entry points included).
# - Koin 4: resolution is compile-time (reified singleOf / factoryOf /
#   viewModelOf), so definitions and ViewModels need no keeps — see
#   https://insert-koin.io/docs/reference/koin-android/r8-proguard. The app
#   uses no Fragments/WorkManager, and Navigation3 persists routes via
#   kotlinx-serialization (covered above), so no Parcelable keeps either.
# - LiteRT-LM: ships no consumer rules and needs none — Engine, Conversation
#   and config classes are only called directly, and native methods are kept
#   by the default native-method rule.
#
# Maintenance:
# - If you add a library that loads app classes by name (reflection,
#   ServiceLoader, Gson databinding, @Parcelize saved-state, …), add the
#   narrowest possible rule here and rebuild release to confirm nothing
#   breaks. Prefer `-keepclassmembers` over `-keep`, and add
#   `,allowobfuscation` / `,allowshrinking` where obfuscation of the kept
#   class is still acceptable.
# - Never add `-dontobfuscate`, `-dontshrink`, or `-dontoptimize` globally;
#   they only bloat the release build.
# - Incidentally, mapping.txt in the mapping output dir is what deobfuscates
#   release stack traces (Android Studio applies it automatically).
