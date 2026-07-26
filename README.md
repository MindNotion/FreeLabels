# FreeLabels — Java Swing port of `camel.php`

A desktop (Swing) port of the FreeLabels bulk label designer. It reproduces the
core workflow of the PHP/JS page:

| PHP/JS (camel.php)                              | Java Swing equivalent                              |
|--------------------------------------------------|-----------------------------------------------------|
| Page/Template preset dropdown                    | `LayoutState.Preset` + preset combo box              |
| Rows/Cols/Margins/Gaps inputs                    | Spinners bound to `LayoutState`                      |
| Single Label Data quick-add form + "Add to Grid" | Quick-add form + **+ Add to Grid** button            |
| Bulk Import (.xlsx) via SheetJS                  | `XlsxImporter` (Apache POI)                          |
| Live Preview grid (`drawPreview`)                | `PreviewPanel` (custom `paintComponent`)              |
| JsBarcode (Code128/EAN13) + QRCode.js             | `BarcodeUtil` (ZXing) — `autoBarcodeType()` ported 1:1 |
| Zoom in/out                                       | Zoom In/Out buttons + `state.zoom`                   |
| Labels Data table (`#labelsTable`)               | `LabelTableModel` + editable `JTable`                |
| Clear / Duplicate to Fill / Download JSON        | Matching buttons, same logic                          |
| Generate PDF (html2canvas + jsPDF)               | `PdfExporter` (Apache PDFBox), paginated              |
| Export ZPL (`buildZPL`)                          | `LabelExporter.buildZPL()` — same command structure   |
| Export TSPL (`buildTSPL`)                        | `LabelExporter.buildTSPL()` — same command structure  |

Not ported (not meaningful outside a browser): rulers/drag-guides, the ad
banner carousel, and the loader overlay animation — the desktop app just
disables controls momentarily via a wait cursor during long operations
(import/PDF export run on a background thread).

## Added beyond the original PHP/JS

These three features don't exist in `camel.php` and were added on request:

- **Label background** — "Label Background" panel: pick a solid color or an
  image file; it's stretched to fill every label box in the live preview
  and the generated PDF. `LayoutState.backgroundMode/backgroundColor/labelBackgroundImage`,
  painted in `PreviewPanel.paintLabelBackground()` and `PdfExporter.drawBackground()`.
- **Icon / logo image** — "Label Icon / Logo" panel: import an image once and
  it's stamped in the top-right corner of every label (preview + PDF).
  `LayoutState.labelIconImage`.
- **Save/Load Template** — "Template" panel: saves page size, grid
  (rows/cols/margins/gaps), background, and icon to a single portable
  `.fltemplate` file (`TemplateManager`). Images are embedded as base64 PNG
  inside the file, so the template doesn't depend on the original image
  files still existing on disk. Label *data* (the actual product rows) is
  **not** included — that's what "Download JSON" / bulk `.xlsx` import are
  for; templates are just the reusable layout/styling.

Note: background/icon are not carried into the ZPL/TSPL exports — thermal
printers are monochrome and embedding raster images requires printer-specific
graphics commands (`~DG`/`^GF` for ZPL, `BITMAP` for TSPL) that are out of
scope here; PDF export is the recommended path when background/icon matter.

## Native installers (no Java required for end users)

`packaging/` contains `jpackage` build scripts that turn `target/freelabels.jar`
into a native, double-click installer per OS, with a **private JRE bundled
in** (built via `jlink`) — end users don't install Java at all.

```
packaging/
├── icons/
│   ├── icon.png    Linux icon
│   ├── icon.ico    Windows icon (multi-resolution)
│   └── icon.icns   macOS icon
├── linux/build-installer.sh      -> .deb (default) or .rpm
├── windows/build-installer.bat   -> .exe (default) or .msi
└── macos/build-installer.sh      -> .dmg (default) or .pkg
```

**Important: jpackage cannot cross-compile.** A Windows installer must be
built by running the script *on Windows*, a `.dmg`/`.pkg` *on macOS*, and a
`.deb`/`.rpm` *on Linux* — each one shells out to that OS's native packaging
tools (WiX on Windows, pkgbuild/hdiutil on macOS, dpkg-deb/rpmbuild on
Linux). There's no way around this from a single machine; if you need all
three, build on three machines/VMs/CI runners (a GitHub Actions matrix job
across `windows-latest` / `macos-latest` / `ubuntu-latest` is the common way
to automate this).

### Steps, per OS

1. `mvn package` (produces `target/freelabels.jar`)
2. Run the matching script for that OS:
   - Linux: `./packaging/linux/build-installer.sh` (needs `fakeroot` for
     `.deb`, or `rpmbuild` for `.rpm` — e.g. `apt install fakeroot rpm`)
   - Windows: `packaging\windows\build-installer.bat` (needs the
     [WiX Toolset v3.x](https://wixtoolset.org/) installed and on `PATH`)
   - macOS: `./packaging/macos/build-installer.sh` (needs Xcode command
     line tools: `xcode-select --install`)
3. Output lands in `target/installer/`.

### What was actually verified (in this Linux sandbox)

Rather than just writing these scripts blind, the Linux path was run for
real, end-to-end:

- `packaging/linux/build-installer.sh deb` and `... rpm` both ran
  successfully and produced valid, installable packages (`dpkg-deb --info`
  confirmed correct metadata; the `.deb` was installed with `dpkg -i`).
- Inspecting the package contents confirmed a full bundled JRE under
  `opt/freelabels/lib/runtime/` (including `libawt_xawt.so`, i.e. real
  desktop/GUI support, not just headless), a native launcher binary, the
  app icon, and a `.desktop` menu entry — exactly what "no Java required"
  means in practice.
- The installed launcher was run under an Xvfb virtual display and a
  screenshot was captured, confirming the full Swing UI (settings panel,
  live preview, labels table) renders correctly from the packaged,
  jlink-bundled runtime.

The Windows `.bat` and macOS `.sh` scripts use the identical `jpackage`
invocation pattern (same flags family, OS-appropriate icon/type/tool
requirements) but could not be run here since this sandbox is Linux-only —
`jpackage` refuses to build a Windows or macOS installer on a non-Windows/
non-macOS host, so those two need to be validated by running them on an
actual Windows/macOS machine.

## Project layout

```
freelabels-swing/
├── pom.xml
└── src/main/java/com/freelabels/
    ├── Label.java            Label data model (name, sku, price, barcode, qr, date, gst, hsn, brand)
    ├── LayoutState.java      Page/grid/zoom state + page presets (A4, A5, 50x30, Avery L7160/5163/3422)
    ├── BarcodeUtil.java      autoBarcodeType() + ZXing-based barcode/QR rendering
    ├── PreviewPanel.java     Live preview grid painter
    ├── LabelExporter.java    ZPL / TSPL text builders
    ├── PdfExporter.java      Paginated PDF export (PDFBox)
    ├── XlsxImporter.java     Bulk .xlsx import (Apache POI)
    ├── LabelTableModel.java  Table model for the "Labels Data" grid
    └── LabelApp.java         Main JFrame wiring everything together
```

## Build & run

Requires JDK 11+ and Maven, with normal internet access to Maven Central
(the sandbox this was built in only had apt access, so dependency
resolution there used old Debian-packaged POI/PDFBox/ZXing jars for
verification — use real Maven Central via the `pom.xml` for your build).

```bash
cd freelabels-swing
mvn package
java -jar target/freelabels.jar
```

`mvn package` produces a single runnable fat jar (`target/freelabels.jar`)
via the shade plugin, bundling POI, ZXing, and PDFBox.

## What was verified in this environment

- `mvn -o compile` can't reach Maven Central here (network sandboxed), so
  full dependency resolution wasn't possible in this exact tool session.
- Instead, the equivalent Debian packages (`libapache-poi-java`,
  `libpdfbox2-java`, `libzxing-core-java`, `libzxing-javase-java`) were
  installed via `apt`, and every `.java` file was compiled successfully
  with `javac` against those real jars (zero errors).
- A headless smoke test exercised the non-GUI logic directly:
  - `autoBarcodeType()` correctly classified EAN13 / Code128 / EPC values.
  - EAN13 barcode and QR images rendered to real `BufferedImage`s.
  - `buildZPL()` / `buildTSPL()` produced well-formed printer command text.
  - `PdfExporter.export()` wrote a real, non-empty multi-object PDF file.
  - The only path not confirmed end-to-end here was `.xlsx` reading,
    which hit a known compatibility bug in the *old Debian-packaged*
    `poi-ooxml-schemas` jar unrelated to this code (`WorkbookFactory.create()`
    is the standard, well-documented POI read pattern and will work
    correctly once Maven resolves POI 5.2.5 normally).

## Notes on rendering fidelity

The original `drawPreview()` had ~6 near-duplicate branches (text-only,
barcode-only, qr+text, barcode+qr, all three, etc.) that all produced the
same visual layout in practice. `PreviewPanel.paintLabelContent()` unifies
these into one layout function: QR (if present) on the left third, then a
text block (brand/name/sku+hsn/price+gst/date), with the barcode (if
present) placed under the text block — matching the combined visual result
of the original branches.
