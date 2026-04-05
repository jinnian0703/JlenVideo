pluginManagement {
    repositories {
        google()
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

rootProject.name = "JlenVideo"
include(":app")
include(":core:model")
include(":core:common")
include(":core:design")
include(":core:data")
include(":feature:player")
include(":feature:browse")
include(":feature:detail")
include(":feature:common")
include(":feature:shell")
include(":feature:state")
