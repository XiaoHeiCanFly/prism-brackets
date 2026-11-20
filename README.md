# Prism Brackets

![Prism Brackets 插件图标](src/main/resources/META-INF/pluginIcon.svg)

Prism Brackets 是一款 JetBrains IDE 插件。它按嵌套层级为成对括号着色，帮助你在多层表达式和代码块中快速找到对应的括号。

## 功能

- **彩虹括号**：同一对括号使用相同颜色，不同嵌套层级轮换用色。
- **作用域高亮**：根据光标所在位置标记当前括号包围的范围，默认关闭。
- **语义着色**：可为变量、类型和方法着色，也可单独启用关键字和字符串着色。
- **缩进线高亮**：按缩进深度着色，便于阅读多层代码块。

支持 Java、Kotlin、Python、JavaScript、TypeScript、JSX、TSX、Vue、React、YAML、Rust、Go 和 Dart。语言支持取决于 IDE 及已启用的语言插件。

## 安装与设置

1. 按下方步骤构建插件，获取 `build/distributions/` 中的 ZIP 安装包。
2. 在 IDE 中打开 **Settings → Plugins**，点击齿轮菜单，选择 **Install Plugin from Disk…**。
3. 选择 ZIP 文件，按 IDE 提示完成安装。

在 **Settings → Tools → Prism Brackets** 中调整功能开关、颜色数量和作用域填充不透明度。在 **Settings → Editor → Color Scheme → Prism Brackets** 中自定义配色。

## 从源码构建

需要 Java 21 工具链，以及兼容 IntelliJ 平台构建号 `261` 或更高版本的 IntelliJ IDEA。本项目通过 [build.gradle.kts](build.gradle.kts) 中的 `local(...)` 使用本机 IDE，请先将该路径改为自己的 IntelliJ IDEA 安装路径。

```sh
./gradlew test buildPlugin
```

当前版本生成的安装包为 `build/distributions/prism-brackets-0.2.0.zip`。

## 开源协议

项目代码采用 [Apache License 2.0](LICENSE)。Gradle Wrapper 等第三方组件适用各自的许可证。

Copyright © 2026 XiaoHeiCanFly
