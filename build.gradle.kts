plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.prismbrackets"
version = "0.2.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 纯平台 API 实现，不依赖任何语言插件类；用本地已安装的 IDEA Ultimate
        // 做编译/runIde 目标，避免重复下载。
        local("/Users/king/Applications/IntelliJ IDEA.app")
    }
    // 纯逻辑单元测试（扫描器/作用域/缩进算法）不需要 IDE 测试框架
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    // 无图形环境下 buildSearchableOptions 拉不起 IDE 实例；禁用不影响插件功能
    buildSearchableOptions = false
    pluginConfiguration {
        id = "com.prismbrackets.free"
        name = "Prism Brackets"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "261"
            untilBuild = provider { null }
        }
        description = """
            <p>Prismatic color highlighting for brackets, variables, keywords and indent guides in IntelliJ-based IDEs.</p>
            <ul>
              <li>Multi-color brackets for all bracket types (depth-based)</li>
              <li>Scope highlighting (off by default)</li>
              <li>Colorful variables / types / methods (semantic highlighting)</li>
              <li>Indent guide highlighting</li>
              <li>Java, Kotlin, Python, JS/TS/JSX/TSX, Vue, React, YAML, Rust, Go, Dart</li>
            </ul>
        """.trimIndent()
    }
}
