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
        maven("https://jitpack.io")
    }
}

rootProject.name = "ths-anime"

// Every folder at src/<lang>/<name> containing a build.gradle.kts is an extension.
file("src").listFiles()?.filter { it.isDirectory }?.sorted()?.forEach { langDir ->
    langDir.listFiles()?.filter { File(it, "build.gradle.kts").exists() }?.sorted()?.forEach { extDir ->
        val path = ":src:${langDir.name}:${extDir.name}"
        include(path)
        project(path).projectDir = extDir
    }
}
