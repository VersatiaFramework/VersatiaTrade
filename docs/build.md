# Build

A single Gradle project producing two JARs from the same classes:

| Task | File in `build/libs` | What it is |
|---|---|---|
| `shadowJar` | `VersatiaTrade-<version>.jar` | the plugin: these classes, InvUI relocated and the CommandAPI Kotlin DSL |
| `jar` | `VersatiaTrade-<version>-plain.jar` | these classes only; published as `dev.versatia:VersatiaTrade` |

`deployPlugin` copies the plugin JAR to `pluginsDir` (from `local.properties`) as
`VersatiaTrade.jar`, so a newer build replaces the deployed one.

## InvUI

InvUI 2 is a GUI library for one Minecraft version at a time (2.4.0-2.5.x support 26.3), published
only in its own repository, so `settings.gradle.kts` declares it with a content filter and the
version comes from VersatiaCatalog (`libs.invui`). Every plugin of the ecosystem that opens windows
shades its own copy, relocated under its own package (`dev.versatia.trade.libs.invui`):

- InvUI keeps one instance per copy of its classes, bound to the plugin whose class loader defines
  them, so the windows of this plugin are this plugin's and close when it stops.
- Plugins update InvUI independently of each other.
- Only `xyz.xenondevs.invui:invui` is shaded of InvUI; its annotation dependencies (JSpecify, JetBrains
  annotations) are not needed at runtime, and Kotlin, VersatiaAPI and Paper come from the server.

InvUI is compiled against the server's Mojang-mapped internals, so the plugin JAR's manifest sets
`paperweight-mappings-namespace: mojang`; without it Paper would treat the JAR as Spigot-mapped and
remap InvUI's references to the server at load time.

## The published artifact

Plugins listening to its events compile against the plain JAR with `compileOnly`; on the server the
classes come from this plugin through `softdepend:`. Its POM lists no dependencies: the public API
needs only VersatiaAPI, which every plugin already has.

## CommandAPI

The CommandAPI plugin is a soft dependency; its Kotlin DSL (`commandapi-kotlin-*`) ships in the
plugin JAR unrelocated, as in every Versatia plugin with commands (see the Commands page of the
Versatia documentation). Only the `command` package refers to CommandAPI, in an object carrying
`@ConditionalOnPlugin("CommandAPI")`.
