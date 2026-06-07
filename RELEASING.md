# Salt Auto Tagger 发版流程

本项目从 `v0.1.2` 开始使用 GitHub Actions 自动创建正式 Release。

## 正式发版步骤

1. 修改 [build.gradle.kts](/E:/Code/Salt%20Auto%20Tagger/salt-auto-tagger-mod/build.gradle.kts) 中的 `version = "x.y.z"`。
2. 提交版本相关改动。
3. 创建语义化 tag，例如 `v0.1.2`。
4. 推送提交与 tag 到 GitHub。

示例命令：

```powershell
git add .
git commit -m "release 0.1.2"
git tag v0.1.2
git push origin main
git push origin v0.1.2
```

## 自动化发布内容

推送 `v<version>` tag 后，GitHub Actions 会自动执行：

- 使用 `windows-latest` 和 Java 21 运行 `gradlew.bat plugin`
- 校验 tag 版本与 `build.gradle.kts` 中的版本完全一致
- 校验产物是否存在：`build/libs/plugin-com.salt.autotagger-<version>.zip`
- 读取 UTF-8 的 [release-notes-template.md](/E:/Code/Salt%20Auto%20Tagger/salt-auto-tagger-mod/release-notes-template.md)
- 替换 `{{VERSION}}` 与 `{{ASSET_NAME}}` 占位符
- 创建或更新同名 GitHub Release
- 上传或覆盖同名 zip 附件

## Release 约定

- Tag 名：`v<version>`
- Release 标题：`Salt Auto Tagger v<version>`
- 附件名：`plugin-com.salt.autotagger-<version>.zip`

## 失败排查

- 如果 workflow 提示版本不匹配，先检查 tag 与 `build.gradle.kts` 的 `version` 是否一致。
- 如果 workflow 提示找不到 zip，先在本地运行 `.\gradlew.bat plugin` 确认构建正常。
- 如果需要更新 Release 说明，不再手工在命令行拼接正文，直接修改模板文件后重新发版即可。
