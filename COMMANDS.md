# Salt Auto Tagger Mod 常用命令

本文档整理 `salt-auto-tagger-mod` 后续开发、构建、检查产物和排错时常用的命令。

## 进入项目目录

```powershell
cd "E:\Code\Salt Auto Tagger\salt-auto-tagger-mod"
```

后续命令默认都在该目录下执行。

## 环境要求

- Java 21
- Gradle Wrapper 8.14
- Windows / PowerShell 或 CMD

项目自带 Gradle Wrapper，因此一般不需要全局安装 Gradle。

## Java 检查与设置

检查当前 shell 是否能找到 Java：

```powershell
java -version
```

如果当前 shell 没有配置 `JAVA_HOME`，可以使用项目脚本临时切换到本机 Java 21：

```bat
use-java21.cmd
```

当前脚本固定使用：

```text
D:\Java\jdk-21
```

如果你的 Java 21 安装路径不同，需要同步调整 `use-java21.cmd` 和 `build-local.cmd` 中的 `JAVA_HOME`。

## 推荐构建命令

推荐优先使用：

```bat
build-local.cmd
```

这个脚本会临时设置：

```text
JAVA_HOME=D:\Java\jdk-21
```

然后执行插件打包任务：

```bat
gradlew.bat plugin
```

## Gradle 常用命令

直接打包插件 zip：

```powershell
.\gradlew.bat plugin
```

生成普通 jar：

```powershell
.\gradlew.bat jar
```

清理构建目录：

```powershell
.\gradlew.bat clean
```

如果直接运行 `.\gradlew.bat plugin` 报错：

```text
JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

优先改用：

```bat
build-local.cmd
```

或者先确保当前 shell 已经配置 Java 21。

## 查看构建产物

查看输出文件：

```powershell
Get-ChildItem build\libs
```

插件 zip 产物位于：

```text
build\libs\plugin-com.salt.autotagger-<version>.zip
```

例如当前版本可能生成：

```text
build\libs\plugin-com.salt.autotagger-0.1.2.zip
```

## 修改版本号

版本号在 `build.gradle.kts` 中维护：

```kotlin
version = "0.1.2"
```

修改后重新执行：

```bat
build-local.cmd
```

新的版本号会影响插件 zip 文件名，以及资源文件中通过 `__PLUGIN_VERSION__` 替换出来的插件版本信息。

## 常见问题

### `JAVA_HOME is not set`

说明当前 shell 没有找到 Java。建议优先使用：

```bat
build-local.cmd
```

如果仍然失败，检查 `D:\Java\jdk-21\bin\java.exe` 是否存在。

### 构建后没有看到 zip

先确认执行的是插件任务：

```powershell
.\gradlew.bat plugin
```

然后查看：

```powershell
Get-ChildItem build\libs
```

普通 `jar` 任务只生成 jar；给 Salt Player 使用的完整插件包应使用 `plugin` 任务生成 zip。

### 需要完全重新构建

可以先清理再构建：

```powershell
.\gradlew.bat clean
```

```bat
build-local.cmd
```
