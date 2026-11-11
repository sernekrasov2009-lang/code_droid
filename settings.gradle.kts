pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "code_droid"

include(":app")
include(":core:model")
include(":core:data")
include(":core:llm")
include(":core:agent")
include(":core:workspace")
include(":core:security")
include(":core:ui")
include(":feature:chat")
include(":feature:workspace")
include(":feature:agent")
include(":feature:settings")
include(":feature:onboarding")
