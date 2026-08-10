# pr-assets

Screenshots referenced from pull request descriptions. **This branch is never merged.**

GitHub's drag-and-drop attachment flow is browser-session only, so it cannot be scripted.
Images live here instead and are linked by raw URL, which keeps binaries out of the main
history:

```
https://raw.githubusercontent.com/themadorg/bedrud-android/pr-assets/<pr>/<name>.png
```

## Rules

- **One directory per PR number.** `103/before-switcher.png`, `103/after-switcher.png`, …
- **Never overwrite a path.** GitHub proxies images through its camo cache, so replacing a
  file at an already-rendered URL can keep serving the old picture. New capture, new path.
- **Capture on an emulator with a throwaway account.** This repo is public; a real device
  puts account names, server hostnames and room IDs in frame.
- **Crop to the region under review.** A tighter frame is easier to compare and carries
  less incidental data.
