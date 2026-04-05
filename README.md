# Flexcil Backup Viewer

A universal, **100% offline** viewer for **Flexcil backup files (`.flex`)**.  
Works as a browser web app, a PWA you can install on any device, and as a native **Android APK** built automatically by GitHub Actions.

---

## Features

| Feature | Details |
|---|---|
| Open any `.flex` file | Drag & drop or file picker — any Flexcil backup |
| Folder / document tree | Exact same hierarchy as inside Flexcil |
| PDF viewer | Full in-browser rendering with page navigation and zoom |
| Password-protected PDFs | Clean unlock screen with password field |
| Cover preview | Thumbnail Flexcil generated for each document |
| Annotation inspector | Pen stroke and highlight counts per document |
| Document metadata | Dates, page sizes, file sizes, document IDs |
| Dark / Light mode | Toggle in the sidebar header |
| Multi-select | Checkboxes on documents and entire folders |
| ZIP batch export | Download selected PDFs + previews as a single ZIP |
| Save to folder | Choose any folder on your device (desktop Chrome + modern Android) |
| Per-file download | Download any individual PDF or preview |
| Search | Instant search across all document names |
| Desktop mode (APK) | Android app renders in full desktop layout — same as desktop Chrome |
| 100% offline | No server — all parsing runs in your browser |

---

## Live Web App (GitHub Pages)

After completing the setup below your app will be live at:

```
https://YOUR_USERNAME.github.io/YOUR_REPO_NAME/
```

---

## Step-by-Step Setup Guide

### Prerequisites

- A [GitHub](https://github.com) account
- [Git](https://git-scm.com/) installed on your machine
- [Node.js 20+](https://nodejs.org/) (for local development only)
- [pnpm](https://pnpm.io/) package manager

---

### Step 1 — Get the code onto GitHub

```bash
# If you don't have a local copy yet, clone this project first:
git clone https://github.com/YOUR_USERNAME/YOUR_REPO.git
cd YOUR_REPO

# Or if you already have the code locally, just init a repo and push:
git init
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git add .
git commit -m "Initial commit"
git push -u origin main
```

---

### Step 2 — Enable GitHub Pages (web app deployment)

1. Go to your repo on GitHub
2. Click **Settings** (top menu)
3. In the left sidebar, click **Pages**
4. Under **Source**, select **GitHub Actions**
5. Click **Save**

Now every push to `main` will automatically build and publish the web app.

---

### Step 3 — Wait for the first build to finish

1. On GitHub, click the **Actions** tab
2. You'll see a workflow called **Build Web App** running
3. Wait for the green tick — takes about 1–2 minutes
4. Your live web app is now at `https://YOUR_USERNAME.github.io/YOUR_REPO_NAME/`

---

### Step 4 — Build the Android APK

No local Android or Java setup needed. Everything runs in GitHub Actions.

#### Trigger the APK build manually

1. GitHub → **Actions** tab
2. Click **Build Android APK** in the left list
3. Click **Run workflow** → **Run workflow** (green button)
4. Wait ~5–10 minutes for the build to complete

#### Or trigger automatically by pushing a version tag

```bash
git tag v1.0.0
git push origin v1.0.0
```

This kicks off the APK build automatically.

---

### Step 5 — Download and install the APK

1. Go to **Actions → Build Android APK → (latest run)**
2. Scroll to the bottom to the **Artifacts** section
3. Download `FlexcilViewer-debug-*.zip` and unzip it to get `app-debug.apk`
4. Transfer the APK to your Android phone (email, cable, Google Drive, etc.)
5. On your phone:
   - Go to **Settings → Security** (or Apps → Special App Access)
   - Enable **Install unknown apps** for your file manager or browser
6. Tap the APK file → **Install** → **Open**

> The app opens in full **desktop mode** — same layout as the web app in a desktop browser.  
> The "Save to Folder" button lets you pick any folder on your internal storage for exports.

---

## Local Development

### Install dependencies

```bash
npm install -g pnpm   # if pnpm is not installed
pnpm install
```

### Run the dev server

```bash
pnpm --filter @workspace/flex-viewer run dev
```

Open the URL shown in the terminal (usually `http://localhost:XXXX`) and drag in your `.flex` file.

### Build for production locally

```bash
PORT=3000 BASE_PATH=/ pnpm --filter @workspace/flex-viewer run build
# Output → artifacts/flex-viewer/dist/public/
```

---

## GitHub Actions Workflow Details

### `build-web.yml` — Web App + GitHub Pages

| Step | What it does |
|---|---|
| Checkout | Pulls the latest code |
| Setup Node 20 + pnpm 10 | Install toolchain |
| `pnpm install` | Install all workspace dependencies |
| `pnpm build` | Builds with `PORT=3000` and `BASE_PATH=/<repo-name>/` |
| Upload artifact | Stores the `dist/public/` folder |
| Deploy to Pages | Publishes to GitHub Pages (main branch only) |

**Required GitHub setting:** Repo → Settings → Pages → Source → **GitHub Actions**

---

### `build-apk.yml` — Android APK

| Step | What it does |
|---|---|
| Checkout | Pulls the latest code |
| Setup Node 20 + pnpm 10 | Install toolchain |
| `pnpm install` | Install workspace dependencies |
| `pnpm build` | Builds web assets with `PORT=3000 BASE_PATH=/` |
| Create isolated Capacitor workspace | Runs npm in `/tmp/cap-build` (avoids pnpm workspace conflicts) |
| Install `@capacitor/cli @capacitor/core @capacitor/android` | Capacitor packages |
| Write `capacitor.config.json` | Points `webDir` to the built web assets |
| `cap add android` | Creates the Android project |
| Patch `AndroidManifest.xml` | Adds storage permissions for file export |
| `cap sync android` | Copies web assets into the Android project |
| Setup Java 21 | Required by Capacitor's Android library |
| Setup Android SDK | Install Android build tools |
| `./gradlew assembleDebug` | Compiles the APK |
| Upload artifact | Makes `app-debug.apk` downloadable |

**No local Android Studio or Java setup needed** — everything is automated.

---

## Exporting Files

### Single file
Open any document → tap **PDF** or **Preview** in the top-right → file downloads immediately.

### Batch export (multiple documents)
1. Check the boxes next to documents in the sidebar (or click a folder checkbox to select all inside it)
2. The **Export Bar** appears at the bottom
3. Choose an export option:

| Button | What you get |
|---|---|
| **PDFs ZIP** | ZIP of all selected PDF files |
| **Previews ZIP** | ZIP of all cover/thumbnail images |
| **All as ZIP** | PDFs + thumbnails, organized by folder |
| **Save to Folder** | Opens a native folder picker — saves files directly to the folder you choose (no ZIP) |

> **Save to Folder** is available in Chrome 86+ on desktop and Chrome 109+ on Android.  
> On older browsers the ZIP download is used automatically.

---

## File Format Reference

Flexcil backups use a nested ZIP structure:

```
YourBackup.flex                       ← outer ZIP
└── flexcilbackup/
    ├── info                          ← JSON: app name, backup date, version
    └── Documents/
        └── FolderName/
            ├── .itemInfo             ← binary metadata
            └── DocumentName.flx     ← inner ZIP
                ├── attachment/PDF/<UUID>      ← the actual PDF
                ├── thumbnail                  ← JPEG cover
                ├── thumbnail@2x               ← retina JPEG
                ├── pages.index                ← JSON page layout
                ├── outlines                   ← JSON outline / TOC
                ├── objects/<UUID>.drawings    ← JSON pen strokes
                ├── objects/<UUID>.annotations ← JSON highlights
                └── info                       ← JSON doc metadata
```

All parsing uses [fflate](https://github.com/101arrowz/fflate) for ZIP and [PDF.js](https://mozilla.github.io/pdf.js/) for rendering. Nothing is ever uploaded anywhere.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React 18 + TypeScript |
| Build | Vite 7 |
| Styling | Tailwind CSS v4 |
| ZIP parsing | fflate |
| PDF rendering | PDF.js (pdfjs-dist) |
| Icons | Lucide React |
| Android packaging | Capacitor |
| CI / CD | GitHub Actions |

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
│       ├── zip-export.ts          ← ZIP bundler, folder picker, downloader
│       └── theme.tsx              ← Dark/light mode context
├── public/
│   └── manifest.json              ← PWA manifest
└── index.html                     ← Entry HTML

.github/workflows/
├── build-web.yml                  ← Build + deploy to GitHub Pages
└── build-apk.yml                  ← Build Android APK (Java 21, Capacitor)

capacitor.config.json              ← Capacitor config (desktop user agent, storage)
README.md                          ← This file
```

---

## Frequently Asked Questions

**Is my data safe? Does anything get uploaded?**  
Nothing leaves your device. All processing happens locally in your browser.

**Can I open any `.flex` file?**  
Yes — any Flexcil backup from any device or iOS/Android version.

**The PDF shows a blank or error — why?**  
Some PDFs use encryption or uncommon features. Enter the password if prompted, or use the Preview tab to see the cover image.

**How do I update the APK after code changes?**  
Push a new tag (`git tag v1.0.1 && git push origin v1.0.1`) or manually trigger the workflow and download the new APK.

**Can I run this fully offline?**  
Yes. Once loaded (or installed as a PWA), it works without internet.

**Can I install it without an app store?**  
Yes. Two options: install as a **PWA** directly from the browser (Settings → Add to Home Screen in Chrome), or sideload the **APK** built by GitHub Actions.

---

## Signed / Release APK (Optional)

To produce a signed APK (required for Play Store submission), add these secrets to your repo under **Settings → Secrets and Variables → Actions**:

| Secret name | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 your-key.jks` output |
| `KEYSTORE_PASSWORD` | Your keystore password |
| `KEY_ALIAS` | Your key alias |
| `KEY_PASSWORD` | Your key password |

Then uncomment the **Release APK** section at the bottom of `.github/workflows/build-apk.yml`.

---

## License

MIT — use, modify, and distribute freely.
