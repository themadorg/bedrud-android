## Summary

What does this PR do?

---

## Changes

- 
- 
- 

---

## Evidence

Show the change, don't only describe it — a reviewer should see what moved without
reading the diff.

**If it touches the UI**, a before/after pair per affected screen, same device and same
state in both. Capture the "before" on the base branch *before* starting the work.

| Before | After |
|---|---|
| <!-- ![before](...) --> | <!-- ![after](...) --> |

**Otherwise**, the few lines that *are* the change — not a tour of every file.

```kotlin
// the one snippet that carries the change
```

<details>
<summary>Where screenshots live</summary>

GitHub's drag-and-drop attachment flow is browser-only, so it can't be scripted. Push
images to the non-merging **`pr-assets`** branch under `<pr-number>/<name>.png` and link
them by raw URL:

```
https://raw.githubusercontent.com/themadorg/bedrud-android/pr-assets/<pr>/<name>.png
```

This keeps binaries out of the main history. Two rules: capture on an **emulator with a
throwaway account** — this repo is public and a real device puts account names, server
hostnames and room IDs in frame — and **never overwrite an image path**, because GitHub's
camo proxy caches them and will keep serving the old picture.

</details>

---

## Type

- [ ] Feature
- [ ] Bug fix
- [ ] Infra
- [ ] Docs
- [ ] Refactor

---

## Testing

- [ ] Tested locally
- [ ] Tested in staging

---

## Checklist

- [ ] Code reviewed
- [ ] Tests passing
- [ ] Docs updated

---

## Related Issues

Closes #
