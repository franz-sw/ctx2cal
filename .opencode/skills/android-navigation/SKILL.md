---
name: android-navigation
description: |
  Type-safe Navigation 3 for Android/KMP (Compose Multiplatform) - NavKey routes, NavBackStack, NavDisplay, EntryProvider DSL, feature entry builders, cross-feature callbacks, and wiring in :app. Use this skill whenever setting up navigation, defining routes, adding a new screen, navigating between features, or wiring NavDisplay. Trigger on phrases like "set up navigation", "add a route", "navigate between screens", "NavKey", "NavDisplay", "NavEntry", "entryProvider", "NavBackStack", "rememberNavBackStack", "BackStack", "EntryProviderScope", or "Navigation 3".
---

# Android / KMP Navigation (Navigation 3)

## Principles

- **Navigation 3 only.** Do **not** use Navigation 2 (`NavController`, `NavHost`, `NavGraphBuilder`, `androidx.navigation:navigation-compose`, `composable<T>`, `toRoute()`).
- **Type-safe navigation** with `@Serializable` **keys** implementing `NavKey` (KotlinX Serialization).
- **You own the back stack.** It is a `SnapshotStateList<NavKey>` (`NavBackStack`) — push keys to navigate forward, remove the last key to go back.
- **One entry builder per feature**, defined as an `EntryProviderScope` extension in the feature's `presentation` module.
- All feature entry builders are assembled in `:app` via the `entryProvider {}` DSL inside `NavDisplay`.
- Feature-to-feature navigation uses **callbacks**, keeping features decoupled. Features never import another feature's `NavKey`.

---

## Dependencies

### KMP / Compose Multiplatform (Android + iOS) — use this in `shared/`

> In `shared/` you must use the JetBrains KMP artifacts, **not** `androidx.navigation3:*` (Android-only, reflection-based).

In `libs.versions.toml`:

```toml
[versions]
navigation3 = "1.1.1"            # org.jetbrains.androidx.navigation3
lifecycleViewmodelNav3 = "2.10.0"
composeMultiplatformAdaptive = "1.3.0-beta02"
kotlinxSerialization = "1.9.0"

[libraries]
jetbrains-navigation3-ui = { module = "org.jetbrains.androidx.navigation3:navigation3-ui", version.ref = "navigation3" }
jetbrains-lifecycle-viewmodel-navigation3 = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-navigation3", version.ref = "lifecycleViewmodelNav3" }
jetbrains-material3-adaptive-navigation3 = { module = "org.jetbrains.compose.material3.adaptive:adaptive-navigation3", version.ref = "composeMultiplatformAdaptive" }
kotlinx-serialization-core = { module = "org.jetbrains.kotlinx:kotlinx-serialization-core", version.ref = "kotlinxSerialization" }
```

In `shared/module.yaml` (Amper) add:

```yaml
dependencies:
  - $libs.jetbrains-navigation3-ui
  - $libs.jetbrains-lifecycle-viewmodel-navigation3 # optional: ViewModel scoped to NavEntry
  - $libs.kotlinx-serialization-core
```

Only `navigation3-ui` is required — it transitively brings in `navigation3-common` (types like `NavKey`, `NavEntry`, `entryProvider`).

### Android-only (no `shared/`)

Use the Android artifacts instead: `androidx.navigation3:navigation3-ui` + `androidx.navigation3:navigation3-runtime` (e.g. `nav3Core = "1.2.2"`), plus `androidx.lifecycle:lifecycle-viewmodel-navigation3` and `androidx.compose.material3.adaptive:adaptive-navigation3` if needed.

---

## Route Keys (`NavKey`)

Define keys in the feature's `presentation` module (or an `api` submodule if you split api/impl). Every key is `@Serializable` and implements `NavKey`:

```kotlin
// feature:notes:presentation
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object NoteListRoute : NavKey
@Serializable data class NoteDetailRoute(val noteId: String) : NavKey
```

Use `data object` for screens with no parameters, `data class` for screens with arguments.

**Recommended for KMP:** group a feature's keys under a sealed interface so serialization is automatic:

```kotlin
@Serializable sealed interface NotesRoute : NavKey
@Serializable data object NoteListRoute : NotesRoute
@Serializable data class NoteDetailRoute(val noteId: String) : NotesRoute
```

Avoid passing complex objects via navigation — pass IDs and load data in the destination ViewModel.

---

## Feature Entry Builder

```kotlin
// feature:notes:presentation
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

fun EntryProviderScope<NavKey>.notesEntries(
    backStack: MutableList<NavKey>,
    onNavigateToEditor: (String) -> Unit // callback for cross-feature navigation
) {
    entry<NoteListRoute> {
        NoteListRoot(
            onNavigateToDetail = { backStack.add(NoteDetailRoute(it)) }
        )
    }
    entry<NoteDetailRoute> { key ->
        // `key` is the NoteDetailRoute instance — no toRoute() needed
        NoteDetailRoot(
            noteId = key.noteId,
            onNavigateToEditor = onNavigateToEditor
        )
    }
}
```

Rules:

- The builder is an extension on `EntryProviderScope<NavKey>` (not `NavGraphBuilder`).
- Use `entry<T> { }` for no-arg keys and `entry<T> { key -> }` when you need the key's arguments.
- The `backStack` is passed in for **intra-feature** navigation (`backStack.add(...)`). Cross-feature targets are exposed as lambda callbacks — never by importing another feature's key.
- `entry<T>(metadata = ...)` is available if you need custom transitions or dialog/bottom-sheet scene metadata.

---

## Wiring in `:app` / `App.kt`

```kotlin
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlinx.serialization.modules.subclassesOfSealed

// Register every NavKey subtype — REQUIRED on iOS / non-JVM.
private val navConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclassesOfSealed() // every @Serializable sealed interface : NavKey
            // or: subclass(NoteListRoute::class, NoteListRoute.serializer())
            //     subclass(NoteDetailRoute::class, NoteDetailRoute.serializer())
        }
    }
}

@Composable
fun App() {
    // Back stack survives config change + process death.
    val backStack = rememberNavBackStack(navConfig, NoteListRoute)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() }, // required for system back / swipe
        entryProvider = entryProvider {
            notesEntries(
                backStack = backStack,
                onNavigateToEditor = { backStack.add(EditorRoute(it)) }
            )
            editorEntries(backStack = backStack)
        }
    )
}
```

Cross-feature navigation is always expressed as a lambda callback — the feature module never imports another feature's route.

Key facts:

- `backStack.add(Key(...))` navigates forward. `backStack.removeLastOrNull()` navigates back.
- The default `onBack` is `{ backStack.removeLastOrNull() }` — always pass it so the system back button / back gesture works.
- To pop to a specific destination, trim the stack, e.g. `while (backStack.lastOrNull() !is NoteListRoute) backStack.removeLastOrNull()`.
- `entryProvider { }` throws `IllegalStateException` on an unknown key by default (override `fallback` only if you need a custom fallback screen).

---

## KMP Serialization (Required on iOS)

On Android you can use the reflection overload `rememberNavBackStack(NoteListRoute)` without a config. **On iOS / Web there is no reflection**, so:

- Always use `rememberNavBackStack(savedStateConfiguration, StartKey)`.
- Every key must be `@Serializable` and implement `NavKey`.
- Register all subtypes in the `SavedStateConfiguration` via `polymorphic(NavKey::class) { ... }` (see above) — omitting this compiles but crashes at runtime on restore.

Choose one of these patterns (per Kotlin Multiplatform docs):

| Pattern | When |
|---|---|
| **Single sealed hierarchy** (`@Serializable sealed interface Route : NavKey`) | All routes in one module — simplest, `subclassesOfSealed()` registers everything |
| **Aggregated sealed types** | Routes split across modules: each module exposes a sealed interface; `:app` registers each with `subclassesOfSealed()` |
| **Individual registration** | Routes can't be sealed-grouped: register each `subclass(Key::class, Key.serializer())` manually, or combine per-module `SerializersModule` instances (`moduleA + moduleB`) |

---

## ViewModel Scoping (Optional)

`NavDisplay` already caps each `NavEntry`'s `LifecycleOwner` to `RESUMED`/`STARTED`/`CREATED` based on back-stack position. To scope a `ViewModel` to the entry it belongs to, add the decorator:

```kotlin
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

NavDisplay(
    backStack = backStack,
    entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(), // keep: default save-state decorator
        rememberViewModelStoreNavEntryDecorator()
    ),
    // ...
)
```

Then use `koinViewModel()` inside the entry's content — it resolves to that entry's `ViewModelStoreOwner`.

---

## Navigation 2 → Navigation 3 Migration

| Navigation 2 (do not use) | Navigation 3 |
|---|---|
| `NavController.navigate(Key)` | `backStack.add(Key)` |
| `NavController.popBackStack()` | `backStack.removeLastOrNull()` |
| `NavHost(navController, startDestination) { ... }` | `NavDisplay(backStack, entryProvider = entryProvider { ... })` |
| `NavGraphBuilder.<feature>Graph(navController)` | `EntryProviderScope<NavKey>.<feature>Entries(backStack)` |
| `navigation<T>(...) { composable<T> { ... } }` | `entry<T> { ... }` (no nesting needed) |
| `composable<T> { backStackEntry -> backStackEntry.toRoute<T>() }` | `entry<T> { key -> }` — `key` is the typed route |
| `NavController` passed into composables | `backStack: MutableList<NavKey>` for intra-feature nav, lambdas for cross-feature nav |

---

## Naming Conventions

| Thing | Convention | Example |
|---|---|---|
| Nav key | `<Screen>Route` (implements `NavKey`) | `NoteListRoute`, `NoteDetailRoute` |
| Feature sealed base (optional) | `<Feature>Route` sealed interface | `NotesRoute` |
| Feature entry builder | `<feature>Entries()` on `EntryProviderScope<NavKey>` | `notesEntries(...)` |
| Back stack | `navBackStack` / `backStack` | `rememberNavBackStack(navConfig, NoteListRoute)` |

---

## Checklist: Adding Navigation to a New Feature

- [ ] Define `@Serializable` `NavKey` objects/data classes in `feature:presentation` (prefer a `@Serializable sealed interface <Feature>Route : NavKey`)
- [ ] Add feature entry builder (`EntryProviderScope<NavKey>.<feature>Entries(backStack, ...)`)
- [ ] Use `backStack.add(...)` for intra-feature navigation; expose cross-feature destinations as lambda callbacks (not direct key imports)
- [ ] Register the feature's keys in `navConfig`'s `SavedStateConfiguration` (`subclassesOfSealed()` or individual `subclass(...)`)
- [ ] Call the entry builder inside `entryProvider { }` and pass `onBack = { backStack.removeLastOrNull() }` to `NavDisplay` in `:app` / `App.kt`
- [ ] Add `navigation3-ui` (JetBrains KMP artifact) to `libs.versions.toml` + `shared/module.yaml` if not already present
