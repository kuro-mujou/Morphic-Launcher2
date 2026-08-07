# R8 rules for :app. Referenced by the `launcher.android.application` convention plugin, which turns on
# `isMinifyEnabled` and `isShrinkResources` for the release build type.
#
# ── Why this file is nearly empty, deliberately ────────────────────────────────────────────────────────────────
#
# Every library this launcher reflects through ships its own `consumer-rules.pro`, and AGP applies those
# automatically. Adding our own copies of them would be worse than useless: a stale `-keep` silently disables
# shrinking for a whole package long after the library stopped needing it, and nothing ever tells you.
#
# Specifically, and checked rather than assumed:
#   • kotlinx.serialization — the compiler plugin generates `$$serializer` classes and bakes the JSON key names in
#     as string constants, so obfuscating our property names cannot break a `SettingsSlice` blob. The library keeps
#     the generated serializers and the `Companion.serializer()` entry points.
#   • Room — `core:database`'s generated `_Impl` classes are referenced directly by the generated builder.
#   • Koin — this codebase uses the constructor DSL (`single { Foo(get()) }`), never `single<Foo>()` reflection, so
#     there is nothing for R8 to lose. Keep it that way; a reflective binding would need a keep rule here.
#   • Compose — ships its own rules, including the ones the runtime needs.
#
# One component R8 could plausibly drop but does not: `RotatingWallpaperService` (`data:wallpaper`) is instantiated
# by the platform, never by us. It is safe because it is declared in the merged manifest, which AGP feeds to R8 as a
# root. If a future service/receiver/provider is ever registered at runtime instead, it needs a `-keep` here.

# ── Readable release crashes ───────────────────────────────────────────────────────────────────────────────────
#
# Without these a release stack trace is line-number-free, which makes a release-only crash (or an ANR trace, which
# is why this build exists) far harder to read than it needs to be. The class and member names stay obfuscated; the
# mapping file that reverses them is written to `app/build/outputs/mapping/release/mapping.txt` on every build.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Coroutine names in stack traces. Cheap, and the difference between a legible async trace and a useless one.
-keepattributes *Annotation*
