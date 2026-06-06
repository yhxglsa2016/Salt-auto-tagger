# Implementation Notes

## Scope of v0.1.0

This repository implements the most reliable first milestone for `Salt Auto Tagger`:

- lyrics correction
- local LRC fallback
- optional remote lyrics lookup

It does not yet write corrected lyrics back into audio tags.

## Why Tag Writing Is Not In v0.1.0

The current `spw-workshop-api` exposes playback lifecycle and lyrics loading hooks, but it does not expose a host-side API for mutating track tags.

That means a full "edit title / album / artist / lyrics / cover directly from SPW" mod will likely need one of these follow-up routes:

1. embed a local tag-writing library inside the mod
2. call an external local helper process
3. extend the SPW workshop API itself

## Recommended Next Steps

1. Keep this mod focused on lyrics replacement first.
2. Expose a tiny lyrics-repair endpoint from your existing API project.
3. After the lyrics workflow is stable, add a second module for tag write-back.

## Suggested API Response

Keep the first API as plain text:

```text
[00:00.00]Corrected lyric line
[00:05.00]Second line
```

That avoids adding JSON parsing dependencies inside the mod and makes debugging easier.
