# Flexcil Backup Viewer

A universal, offline-first viewer for **Flexcil backup files (`.flex`)** — works as a web app, a PWA installable on Android/iOS/desktop, and can be packaged as a native Android APK via GitHub Actions.

---

## What It Does

| Feature | Description |
|---|---|
| Open any `.flex` file | Drag & drop or browse — any Flexcil backup, any size |
| Hierarchical browser | Exact same folder / document structure as in Flexcil |
| PDF viewer | Full in-browser PDF rendering with page navigation & zoom |
| Cover preview | View the thumbnail Flexcil generated for each document |
| Annotation inspector | See how many pen strokes, highlights, etc. are in each doc |
| Document details | Dates, page sizes, file sizes, document IDs |
| Dark / Light mode | Toggle in the top-right of the sidebar |
| Multi-select | Check boxes next to any documents or whole folders |
| Batch ZIP export | Download selected PDFs + previews as a single ZIP |
| Per-file download | Download any individual PDF or preview image |
| Search | Instant search across all document names |
| 100% offline | No server — all parsing happens in your browser |

---

## Quick Start (Web)

### Run locally

```bash
# 1. Clone the repo
git clone https://github.com/YOUR_USERNAME/YOUR_REPO.git
cd YOUR_REPO

# 2. Install pnpm (if not already installed)
npm install -g pnpm

# 3. Install all dependencies
pnpm install

# 4. Start the viewer (runs on http://localhost:PORT)
pnpm --filter @workspace/flex-viewer run dev
```

Then open `http://localhost:<PORT>` in your browser and drop your `.flex` file.

### Build for production (static site)

```bash
pnpm --filter @workspace/flex-viewer run build
# Output → artifacts/flex-viewer/dist/public/
```

You can deploy the `dist/public/` folder to any static host:
- **GitHub Pages** (automatic via CI — see below)
- Vercel, Netlify, Cloudflare Pages, Firebase Hosting, etc.

---

## Install as PWA (Android / iOS / Desktop)

Because the app includes a Web App Manifest and runs over HTTPS, you can install it directly from the browser — **no app store needed**.

### Android (Chrome)
1. Open the app URL in Chrome
2. Tap the three-dot menu → **"Add to Home screen"** → **Install**
3. The app appears on your home screen and works offline

### iOS (Safari)
1. Open the URL in Safari
2. Tap the **Share** button → **"Add to Home Screen"**

### Desktop (Chrome / Edge)
1. Look for the install icon in the address bar
2. Click → **Install**

Once installed as a PWA, the app opens in a standalone window (no browser chrome), works offline, and behaves exactly like a native app. Downloads go to your device's `Downloads` folder automatically.

---

## Android APK via GitHub Actions

For a proper Android APK (installable without a browser), use the included GitHub Actions workflow powered by **Capacitor**.

### One-time setup

**Step 1 — Push your code to GitHub**
```bash
git init   # skip if already a git repo
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git add .
git commit -m "Initial commit"
git push -u origin main
```

**Step 2 — Enable GitHub Actions**  
Go to your repo → **Actions** tab → allow workflows if prompted.

**Step 3 — Initialize the Android project locally** (first time only)

```bash
# Install Capacitor
npm install @capacitor/cli @capacitor/core @capacitor/android

# Build the web app first
pnpm --filter @workspace/flex-viewer run build

# Add Android platform (creates the android/ directory)
npx cap add android

# Commit the android/ directory
git add android/
git commit -m "Add Capacitor Android project"
git push
```

### Building the APK

**Option A — Trigger manually:**  
GitHub → Actions → **"Build Android APK"** → **Run workflow** → Download the APK from the workflow artifacts when done.

**Option B — Push a version tag:**
```bash
git tag v1.0.0
git push origin v1.0.0
```
The workflow runs automatically and uploads `app-debug.apk` as a downloadable artifact.

### Installing the APK on your phone

1. Download `flexcil-viewer-debug-apk-*.apk` from the GitHub Actions run
2. Transfer to your Android device (via cable, WhatsApp, email, etc.)
3. On your phone: Settings → Security → **Enable "Install unknown apps"** for your file manager
4. Tap the APK → Install → Open

> **Note:** Debug APKs are signed with a debug key. For a production/release APK (signed with your own keystore), uncomment the Release section in `.github/workflows/build-apk.yml` and configure the secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) in your repo's Settings → Secrets.

---

## Exporting Files

### Export individual files
- Open any document → click **PDF** or **Preview** button in the top-right header
- The file downloads immediately to your `Downloads` folder

### Export multiple files as ZIP
1. Check the boxes next to documents in the sidebar
2. Use the **folder checkbox** to select an entire folder at once
3. The **Export Bar** appears at the bottom of the screen
4. Choose:
   - **Export PDFs** — ZIP of all selected PDF files
   - **Export Previews** — ZIP of all cover/thumbnail images
   - **Export All as ZIP** — everything together, organized by folder

ZIPs are downloaded directly to your browser's `Downloads` folder and can be opened on Android with any file manager.

---

## File Format Reference

Flexcil uses a nested ZIP structure:

```
YourBackup.flex  (ZIP)
└── flexcilbackup/
    ├── info                          ← JSON: app name, backup date, version
    └── Documents/
        └── FolderName/
            ├── .itemInfo             ← binary metadata
            └── DocumentName.flx     ← inner ZIP
                ├── attachment/PDF/<UUID>    ← the actual PDF
                ├── thumbnail                ← JPEG cover image
                ├── thumbnail@2x             ← retina JPEG
                ├── pages.index              ← JSON page layout
                ├── outlines                 ← JSON outline/TOC
                ├── objects/<UUID>.drawings  ← JSON pen strokes
                ├── objects/<UUID>.annotations ← JSON highlights
                └── info                     ← JSON doc metadata
```

All parsing is done in your browser using [fflate](https://github.com/101arrowz/fflate) (ZIP) and [PDF.js](https://mozilla.github.io/pdf.js/) (PDF rendering). Nothing is uploaded anywhere.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18 + TypeScript |
| Build | Vite 6 |
| Styling | Tailwind CSS v4 |
| ZIP parsing | fflate |
| PDF rendering | PDF.js (pdfjs-dist) |
| Icons | Lucide React |
| Android packaging | Capacitor |
| CI / APK build | GitHub Actions |

---

## Project Structure

```
artifacts/flex-viewer/
├── src/
│   ├── App.tsx                    ← Root component, multi-select state
│   ├── components/
│   │   ├── UploadZone.tsx         ← Drag & drop landing screen
│   │   ├── Sidebar.tsx            ← File tree with checkboxes
│   │   ├── DocumentViewer.tsx     ← PDF / preview / info tabs
│   │   ├── PdfViewer.tsx          ← PDF.js canvas renderer
│   │   ├── ExportBar.tsx          ← Floating batch export bar
│   │   └── WelcomePane.tsx        ← Dashboard after opening backup
│   └── lib/
│       ├── flexcil-parser.ts      ← ZIP decoder, data types
│       ├── zip-export.ts          ← ZIP bundler, file downloader
│       └── theme.tsx              ← Dark/light mode context
├── public/
│   └── manifest.json              ← PWA manifest
└── index.html                     ← Entry HTML with PWA meta tags

.github/workflows/
├── build-web.yml                  ← Build + deploy to GitHub Pages
└── build-apk.yml                  ← Build Android APK

capacitor.config.json              ← Capacitor Android config
README.md                          ← This file
```

---

## Frequently Asked Questions

**Q: Is my data safe? Does anything get uploaded?**  
A: Nothing leaves your device. All processing happens in your browser with zero network requests for your files.

**Q: Can I open any `.flex` file, not just this specific one?**  
A: Yes — any Flexcil backup file from any device will work.

**Q: The PDF shows a blank/error — why?**  
A: Some PDFs use encryption or uncommon features. Try the Preview tab instead to at least see the cover image.

**Q: How do I update the APK after code changes?**  
A: Push a new tag (e.g. `v1.0.1`) or manually trigger the workflow → download the new APK.

**Q: Can I run this completely offline?**  
A: Yes. Once the web page is loaded (or installed as PWA), it works fully offline.

---

## License

MIT — use, modify, and distribute freely.
