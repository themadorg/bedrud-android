# Bedrud Site — Agent Guide

Astro 6 SSG for [bedrud.org](https://bedrud.org). Landing pages, docs, blog. 10-locale i18n. Deploys to GitHub Pages.

---

## Toolchain

| Tool | Version/Notes |
|------|--------------|
| Runtime | Bun (not npm/yarn) |
| Framework | Astro 6 (`output: "static"`) |
| Styling | TailwindCSS v4 via `@tailwindcss/vite` plugin |
| Lint/Format | Biome 2 (not ESLint/Prettier) |
| Components | shadcn/ui (new-york style), React 19 |
| Content | MDX via `@astrojs/mdx`, Zod schemas |
| Code highlighting | rehype-pretty-code + Shiki |
| Search | MiniSearch (per-locale JSON indexes) |
| Icons | astro-icon + `@iconify-json/lucide` |
| SEO | astro-seo, JSON-LD, OpenGraph, sitemap |

---

## Commands

| Command | Purpose |
|---------|---------|
| `bun dev` | Dev server (generates search index first) |
| `bun run build` | Prod build → `dist/` (generates search index first) |
| `bun run check` | Biome lint + format check (CI) |
| `bun run lint` | Biome lint only |
| `bun run format` | Biome auto-format |
| `bun run typecheck` | TypeScript only (`tsc --noEmit`) |
| `bun run typecheck:astro` | Astro check (`astro check`) |
| `make dev-site` | Dev server (from repo root) |
| `make build-site` | Prod build (from repo root) |

**CI order:** `bun run check` → `bun run typecheck:astro` → `bun run build`.

---

## Directory Structure

```
apps/site/
├── astro.config.ts          Astro config: integrations, i18n, redirects, markdown plugins
├── biome.json               Biome lint/format config
├── components.json          shadcn/ui config (new-york, no RSC)
├── tsconfig.json            Strict TS, path aliases
├── scripts/
│   ├── generate-search-index.ts   Builds per-locale MiniSearch JSON
│   └── rehype-code-ltr.ts         Forces LTR on <code>/<pre> in RTL locales
├── public/                  Static assets (favicons, fonts, install scripts, search indexes, swagger.json)
│   ├── auth.md              Agent auth discovery (honest JWT/register docs; not Auth.md protocol runtime)
│   └── .well-known/
│       └── api-catalog      RFC 9727 linkset (OpenAPI + docs + demo health)
└── src/
    ├── components/
    │   ├── api-reference/   Scalar API reference (empty — uses page-level integration)
    │   ├── blog/            BlogPostCard, BlogPostHeader, BlogBackLink (.astro)
    │   ├── docs/            Sidebar, TOC, Search, MobileSidebar, MDX components (callout, tabs, etc.)
    │   ├── landing/         30+ landing components (hero, features, footer, navbar, CTA, etc.)
    │   ├── seo/             JSON-LD component
    │   └── ui/              shadcn/ui primitives (button, dialog, accordion, input, sheet, table, etc.)
    ├── content/
    │   ├── config.ts        Collection schemas (docs, blog) with Zod
    │   ├── docs/            Per-locale MDX docs (10 locales) + sidebar.ts + meta.ts
    │   └── blog/            Per-locale MDX blog posts (10 locales)
    ├── hooks/               useInViewRef, useReducedMotion
    ├── i18n/
    │   ├── utils.ts         t(), getDir(), isValidLocale(), supportedLocales, rtlLocales
    │   └── locales/         10 locale files (en, de, fr, es, zh, ja, tr, fa, ar, ru)
    ├── layouts/
    │   ├── Base.astro       HTML shell: SEO, dark mode, fonts, animations, skip-link
    │   ├── Landing.astro    Base + Navbar + Footer (landing pages)
    │   └── DocsLayout.astro Base + docs header + Sidebar + TOC + Footer (docs pages)
    ├── lib/
    │   ├── config.ts        Site constants (GitHub URL, demo URL, contact email, etc.)
    │   ├── github.ts        GitHub API client (repo info, 30min cache)
    │   └── utils.ts         cn() helper (clsx + tailwind-merge)
    ├── pages/
    │   ├── index.astro      Root redirect → /en
    │   └── [lang]/          Dynamic locale routing
    │       ├── index.astro  Landing page
    │       ├── features.astro, download.astro, about.astro, etc.
    │       ├── docs/        index.astro + [...slug].astro (dynamic docs)
    │       └── blog/        index.astro + [slug].astro (dynamic blog posts)
    └── styles/
        └── global.css       TailwindCSS v4, theme tokens, fonts, prose, animations, utilities
```

---

## Path Aliases

| Alias | Resolves to | Usage |
|-------|------------|-------|
| `~/*` | `./src/*` | All imports from src |
| `@/content/*` | `./src/content/*` | Content collection imports |

Never use `../src/*` — always use aliases.

---

## Routing & Pages

- **Root redirect:** `src/pages/index.astro` redirects to `/en`
- **Dynamic locale:** All pages live under `src/pages/[lang]/`
- **Default locale prefixed:** `/en/docs`, `/en/blog`, etc. (configured in `astro.config.ts`)
- **Redirects:** Short paths (`/docs`, `/blog`, `/download`, etc.) redirect to `/en/...` in `astro.config.ts`

### Page routes

| Route | Page | Layout |
|-------|------|--------|
| `/[lang]` | Landing (home) | Landing |
| `/[lang]/features` | Feature comparison | Landing |
| `/[lang]/download` | Download links | Landing |
| `/[lang]/install` | Install guide | Landing |
| `/[lang]/about` | About page | Landing |
| `/[lang]/demo` | Demo/FAQ | Landing |
| `/[lang]/changelog` | Changelog | Landing |
| `/[lang]/contributors` | Contributors | Landing |
| `/[lang]/contact` | Contact | Landing |
| `/[lang]/privacy` | Privacy policy | Landing |
| `/[lang]/terms` | Terms of service | Landing |
| `/[lang]/api-docs` | Scalar API reference | Landing |
| `/[lang]/docs` | Docs index | DocsLayout |
| `/[lang]/docs/[...slug]` | Dynamic doc page | DocsLayout |
| `/[lang]/blog` | Blog index | Landing |
| `/[lang]/blog/[slug]` | Blog post | Landing |
| `/[lang]/404` | Not found | Landing |

---

## Layouts

```
Base.astro          ← HTML shell, SEO, dark mode script, fonts, IntersectionObserver animations
├── Landing.astro   ← Navbar + main + Footer (most pages)
└── DocsLayout.astro ← Docs header + Sidebar + TOC + main + Footer (docs pages)
```

- **Base.astro** handles: `<html>` lang/dir, astro-seo `<SEO>`, JSON-LD, dark mode init (localStorage + prefers-color-scheme), `data-animate` IntersectionObserver, skip-link, `<ClientRouter>`
- **Landing.astro** adds: `<Navbar>`, `<Footer>`, optional `narrow` prop for constrained width
- **DocsLayout.astro** adds: Fixed docs header, `<Sidebar>`, `<Toc>`, `<Search>`, `<DocsMobileSidebar>`, `<Footer>`

---

## Components

### shadcn/ui (`components/ui/`)

Config: `components.json` — new-york style, no RSC, TSX, lucide icons.

Add components: `cd apps/site && bunx shadcn@latest add <name>`

Available: accordion, button, dialog, input, sheet, table + custom (macbook-scroll, mobile-phone-scroll, phone-mockup, spotlight, text-generate-effect).

Use `cn()` from `~/lib/utils` for className composition.

### Landing (`components/landing/`)

30+ components for marketing pages. Mix of `.astro` and `.tsx` (React). React components use `client:load` or `client:idle` directives in parent Astro files.

Key: `navbar.tsx`, `footer.tsx`, `hero-section.tsx`, `features-section.astro`, `cta-section.tsx`, `theme-toggle.tsx`, `language-switcher.tsx`, `mobile-menu.tsx`.

### Docs (`components/docs/`)

- `sidebar.tsx` — Interactive sidebar, highlights current section
- `toc.tsx` — Table of contents (reads headings from page)
- `search.tsx` — MiniSearch-powered full-text search dialog
- `docs-mobile-sidebar.tsx` — Mobile drawer for sidebar
- `mdx/` — Custom MDX components: callout, tabs (tabs/list/trigger/content/utils), create-admin, installer-steps, systemd-services

### Blog (`components/blog/`)

Astro components: `blog-post-card.astro`, `blog-post-header.astro`, `blog-back-link.astro`.

---

## Content (MDX)

### Collections (`src/content.config.ts`)

**Docs** — `src/content/docs/{locale}/*.mdx`
```ts
schema: { title, description, order?, date?, lastModified?, author?, image?, tags?, draft? }
```

**Blog** — `src/content/blog/{locale}/*.mdx`
```ts
schema: { title, description, date, author (default "Bedrud Team"), image?, tags (default []), draft? }
```

### Sidebar (`src/content/docs/sidebar.ts`)

**Manually defined** — not auto-generated. Adding a doc page requires adding a sidebar entry here.

Structure: `sections[]` → `{ title, titleKey, items: [{ slug, title, description, order }] }`.

Helpers: `getPreviousDoc(slug)`, `getNextDoc(slug)` for prev/next navigation.

### Adding content

1. Create MDX file in `src/content/docs/{locale}/` or `src/content/blog/{locale}/`
2. Add frontmatter matching the collection schema
3. For docs: add entry to `sidebar.ts` with slug, title, description, order
4. For missing locales: falls back to `en/` version automatically

### MDX plugins

- `remark-gfm` — GitHub Flavored Markdown (tables, strikethrough, task lists)
- `rehype-pretty-code` — Syntax highlighting (github-light/github-dark themes)
- `rehype-code-ltr` — Forces `dir="ltr"` on `<code>`/`<pre>` (custom plugin in `scripts/`)
- Mermaid diagrams supported via `mermaid` code blocks (rendered client-side)

---

## i18n

### Locales

10 locales: `en`, `de`, `fr`, `es`, `zh`, `ja`, `tr`, `fa`, `ar`, `ru`

RTL locales: `fa` (Persian), `ar` (Arabic) — `getDir()` returns `"rtl"` for these.

### Translation function

```ts
import { t, getDir, type Locale } from "~/i18n/utils";

t(lang, "nav.features")    // → "Features" (en), "ویژگی‌ها" (fa)
getDir(lang)                // → "ltr" | "rtl"
```

Falls back to English if key missing in current locale.

### Locale files

`src/i18n/locales/{lang}.ts` — Nested key-value objects. Add new keys to **all** locale files (minimum: `en.ts` and the target locale).

### Adding a new locale

1. Add locale code to `supportedLocales` in `src/i18n/utils.ts`
2. Create `src/i18n/locales/{lang}.ts`
3. Add to `locales` array in `astro.config.ts`
4. Add to `bcp47Map` in `Base.astro`
5. Add to `localeChunks` in `astro.config.ts` (sitemap)
6. If RTL: add to `rtlLocales` Set in `utils.ts`
7. Create content directories: `src/content/docs/{lang}/`, `src/content/blog/{lang}/`

---

## Styling

### TailwindCSS v4

Configured via `@tailwindcss/vite` Vite plugin (not PostCSS). Theme in `src/styles/global.css`.

### Design tokens (CSS variables)

All colors use CSS custom properties. Never hardcode hex for structural UI.

| Token | Light | Dark | Use |
|-------|-------|------|-----|
| `--background` | `#FFFBF9` | `#0C0A09` | Page background |
| `--foreground` | `#1C1917` | `#FAFAF9` | Body text |
| `--primary` | `#E11D48` (rose) | `#FB7185` | CTAs, links, active states |
| `--card` | `#FFF1F2` | `#1C1917` | Card surfaces |
| `--muted` | `#F0FDFA` | `#1C1917` | Muted backgrounds |
| `--muted-foreground` | `#57534E` | `#A8A29E` | Secondary text |
| `--border` | `#E7E5E4` | `#292524` | Borders, dividers |
| `--destructive` | `#DC2626` | `#F87171` | Errors |
| `--radius` | `0px` | `0px` | **Zero border-radius globally** |

### Fonts

- **Geist Sans** — Primary sans-serif (weights 100-900)
- **Geist Mono** — Monospace (code, CLI commands)
- **Vazirmatn** — Persian/Arabic font (auto-applied for `:lang(fa)` and `:lang(ar)`)

### Design rules

- **Zero border-radius** — All elements have sharp 90° corners (`--radius: 0px`)
- **Rose primary** — `bg-primary text-primary-foreground` for CTAs
- **Section spacing** — `py-12 lg:py-16` or `py-20` on section blocks
- **Container** — `.section-container` utility (centered `max-w-7xl` + horizontal padding)
- **Section alternation** — Alternate `bg-background` and `bg-muted/30` with `border-y border-border/40`
- **Dark mode** — `.dark` class on `<html>`, toggled via `theme-toggle.tsx`, persisted in localStorage
- **No hardcoded hex** for structural UI — use token classes only

### Utilities

```css
.section-y       /* Vertical section padding (6rem / 8rem responsive) */
.section-container /* Centered max-width container with padding */
```

### Animations

- `data-animate="fade-up|fade-left|fade-right"` — IntersectionObserver-driven CSS transitions (init in Base.astro)
- `data-animate-delay="N"` — Stagger delay in ms
- `.hero-glow` — Subtle opacity pulse for hero section
- `.spotlight-beam` — Aurora radial gradient drift animation
- `prefers-reduced-motion` — All animations disabled when user prefers reduced motion
- Theme transition — Circular reveal via View Transitions API on theme toggle

---

## SEO

Handled in `Base.astro` via `astro-seo`:

- `<title>`, `<meta description>`, canonical URL
- OpenGraph (title, type, image, url, locale, siteName)
- Twitter card (summary_large_image)
- `languageAlternates` — hreflang tags for all 10 locales + x-default
- JSON-LD — Organization + WebSite schemas (default), extendable per-page
- Sitemap — `@astrojs/sitemap` with per-locale chunks, i18n config, x-default for `/en`
- `robots.txt` in `public/`

---

## Search Index

`scripts/generate-search-index.ts` builds per-locale MiniSearch JSON → `public/search-index-{locale}.json`.

- Runs automatically before `dev` and `build`
- **Do not edit** generated `search-index-*.json` files
- Excluded from Biome via `biome.json` includes config

---

## Scripts

| Script | Purpose |
|--------|---------|
| `scripts/generate-search-index.ts` | Builds MiniSearch JSON indexes for all locales |
| `scripts/rehype-code-ltr.ts` | Rehype plugin: forces `dir="ltr"` on `<code>`/`<pre>` elements |

---

## Deployment

- **Target:** GitHub Pages (static output)
- **Build output:** `apps/site/dist/` → copied to root `site/` directory
- **CI:** `deploy-site.yml` triggers after CI success on master
- **Action:** `withastro/action`
- **CNAME:** `bedrud.org` (in `public/CNAME`)
- **`.nojekyll`:** Present in `public/` to bypass Jekyll processing

---

## Gotchas

- **Search index auto-generated** — `bun dev` and `bun run build` run `generate-search-index.ts` first. Don't edit `public/search-index-*.json`.
- **Sidebar is manual** — Adding a doc page? Add entry to `src/content/docs/sidebar.ts` too.
- **Default locale prefixed** — All URLs include locale: `/en/docs`, not `/docs`.
- **MDX not Markdown** — Content files are `.mdx`. Use MDX/JSX syntax, not plain Markdown in components.
- **Code blocks always LTR** — `rehype-code-ltr` plugin forces LTR on `<code>`/`<pre>` even in RTL locales.
- **Biome excludes** — Search index JSON and swagger.json excluded from Biome checks.
- **shadcn/ui has no RSC** — `rsc: false` in `components.json`. Don't use `"use client"` directives.
- **React in Astro** — React components need `client:load`, `client:idle`, or `client:visible` directives in parent `.astro` files.
- **`swagger.json`** — Lives in `public/`, consumed by API docs page. Not auto-generated here. Copied from `server/docs/swagger.json` on dev/build.
- **`/.well-known/api-catalog`** — Hand-maintained RFC 9727 linkset in `public/.well-known/api-catalog`. Points at `/swagger.json`, English API reference, and demo health (`bedrud.xyz`). GitHub Pages may serve the wrong `Content-Type` (not `application/linkset+json`); body is still valid linkset JSON. Custom headers need CDN/proxy (out of static SSG scope).
- **`/auth.md`** — Hand-maintained honest agent discovery in `public/auth.md`. Documents JWT + human registration; does **not** advertise unimplemented agent registration endpoints or OAuth AS metadata.
- **Fonts in `public/fonts/`** — Self-hosted Geist Sans, Geist Mono, Vazirmatn. Referenced via `@font-face` in `global.css`.

---

## Related Files

- `DESIGN.md` — Site-specific design guidelines
- `../../DESIGN.md` — Project-wide design system
- `../../AGENTS.md` — Monorepo conventions and architecture
- `../web/AGENTS.md` — Web app design system (shadcn tokens, patterns)
- `CONTRIBUTING.md` — Contribution guidelines
- `src/content.config.ts` — Content collection schemas
- `src/content/docs/sidebar.ts` — Manual sidebar definition
- `src/i18n/utils.ts` — i18n utilities and locale list
- `src/styles/global.css` — Theme tokens, fonts, animations
- `astro.config.ts` — Astro configuration, redirects, integrations
