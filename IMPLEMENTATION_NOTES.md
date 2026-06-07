# Implementation Notes

## Current Scope

Salt Auto Tagger is a Salt Player for Windows plugin focused on lyrics loading, completion, saving, and diagnostics.

Current capabilities:

- Load local sidecar lyrics from same-folder `.lrc` or `.txt` files.
- Load manually corrected lyrics from the plugin override folder.
- Read existing embedded lyrics tags when available.
- Query online lyrics from Kugou, QQ Music, and Netease.
- Save online lyrics as same-folder LRC files, or write lyrics back to supported audio tags when the tag is missing lyrics.
- Record structured debug logs using `event | key=value` lines.
- Publish releases through GitHub Actions from strict `v<version>` tags.

## Compatibility Notes

- The plugin keeps one runtime config file, `lyrics.json`, so existing user settings continue to load.
- Existing config keys and callback names are treated as public compatibility surface.
- Online lyrics source APIs are external and may change; failures should be diagnosed through debug logs before changing user-facing behavior.

## Development Priorities

1. Keep runtime behavior stable and observable.
2. Add regression tests around pure logic before changing search, matching, source order, or save behavior.
3. Validate release artifacts before upload so Manifest fields, version, and Release Notes stay consistent.
