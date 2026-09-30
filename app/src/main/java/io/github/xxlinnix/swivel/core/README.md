# core

Plain Kotlin with no `android.*` imports, so every rule in here is unit-tested on
the JVM without a phone or a controller. `CorePurityTest` fails the build if an
Android import sneaks in. The Android side (`data/`, `ui/`) turns framework objects
into the types defined here and back.
