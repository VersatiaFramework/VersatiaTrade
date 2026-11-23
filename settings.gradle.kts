rootProject.name = "VersatiaTrade"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        // The Versatia artifacts, VersatiaInteractions included, are consumed from the local Maven repository (`./gradlew publishToMavenLocal` in each checkout)
        mavenLocal { content { includeGroup("dev.versatia") } }
        mavenCentral()
        // Needed to resolve the Paper API that VersatiaAPI exports
        maven("https://repo.papermc.io/repository/maven-public/")
        // InvUI is published only in its own repository
        maven("https://repo.xenondevs.xyz/releases/") { content { includeGroup("xyz.xenondevs.invui") } }
        // The VaultUnlocked economy API
        maven("https://repo.codemc.io/repository/creatorfromhell/") { content { includeGroup("net.milkbowl.vault") } }
    }

    // Shared versions and the Versatia artifacts; versatiaVersion is set in gradle.properties
    versionCatalogs {
        create("libs") {
            from("dev.versatia:VersatiaCatalog:${providers.gradleProperty("versatiaVersion").get()}")
        }
    }
}
