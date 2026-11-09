# FreeLabels Desktop V2
## User Manual & Version 2 Release Notes

**Version:** 2.0  
**Product:** FreeLabels Desktop  
**Platform:** Windows / Linux / macOS (Java Desktop Application)

---

## 1. Introduction

FreeLabels Desktop V2 introduces a more flexible label-designing experience. Users can create customized labels using dynamic input fields, import bulk data from Excel spreadsheets, and generate labels containing barcodes, QR codes, and plain text.

Version 2 also introduces improved Unicode text handling, including support for the Indian Rupee symbol (₹) and multilingual characters in PDF exports.

## 2. What's New in Version 2?

| Feature | Description |
|---|---|
| Custom Fields | Create any number of custom input fields. |
| Multiple Field Types | Choose Barcode, QR Code, or Plain Text/Number. |
| Excel Import | Import bulk label data from custom Excel spreadsheets. |
| Automatic Content Detection | Detect barcode and QR code values using prefixes. |
| Dynamic Label Content | Each Excel column represents a separate line or field on the label. |
| Automatic Text Wrapping | Long text automatically wraps onto additional lines. |
| Logo Positioning | Place the label icon/logo in any of four corners. |
| Unicode PDF Support | Improved support for ₹ and international characters. |
| Improved Text Processing | Better handling of UTF-8, UTF-16, and different newline formats. |
| PDF Export Improvements | Improved font embedding and multiline text rendering. |

## 3. Getting Started

### Step 1: Launch FreeLabels Desktop

1. Open the FreeLabels Desktop application.
2. Wait for the main application window to load.
3. Configure your label dimensions and page settings.
4. Select the number of rows and columns required for your label sheet.

### Step 2: Configure Label Layout

Configure the following settings according to your label requirements:

- Label width and height.
- Page size.
- Number of rows and columns.
- Horizontal and vertical spacing.
- Page margins.
- Background color or background image.

The preview area displays the label layout based on the selected settings.

## 4. Label Icon / Logo Positioning

Version 2 allows users to control where the uploaded logo or icon appears on the label.

### How to configure logo positioning

1. Locate the **Label Icon / Logo** section.
2. Upload or select your logo image.
3. Choose one of the following positions using the radio buttons:
   - Top Left Corner
   - Top Right Corner
   - Bottom Left Corner
   - Bottom Right Corner
4. The first option, **Top Right Corner**, is selected by default.
5. Check the label preview to verify the logo placement.

The selected position is applied to the generated labels.

## 5. Custom Fields

The previous **Single Label Data** section has been replaced with the new **Custom Fields** section. This allows users to create labels with their own data structure.

### Available field types

#### Plain Text / Number

Use this field type for product names, descriptions, SKU numbers, prices, batch numbers, manufacturing dates, and expiry dates.

#### Barcode

Use this field type to generate a barcode from the entered value. Typical applications include product identification, inventory management, retail product labels, and warehouse identification.

#### QR Code

Use this field type to generate QR codes containing website URLs, product information, serial numbers, contact information, or custom identification data.

### How to add custom fields

1. Open the **Custom Fields** section.
2. Click **Add Field**.
3. Enter the field name.
4. Select **Plain Text / Number**, **Barcode**, or **QR Code**.
5. Enter the field value.
6. Repeat to add additional fields.
7. Review the label preview.

You can create as many custom fields as required, subject to the available label space.

## 6. Import Bulk Label Data from Excel

Prepare label data in Excel and import it directly into FreeLabels Desktop.

### Excel file requirements

- Supported file format: `.xlsx`
- Each column represents one input field or label line.
- Each row represents one individual label.
- The first row should contain column headings.
- Each cell contains the corresponding label data.

### Example Excel structure

| Product Name | Product Code | Price | Website |
|---|---|---|---|
| Essential Oil | `barcode=EO1001` | ₹500 | `qr_code=https://example.com/eo1` |
| Lavender Oil | `barcode=EO1002` | ₹650 | `qr_code=https://example.com/eo2` |
| Lemon Oil | `barcode=EO1003` | ₹450 | `qr_code=https://example.com/eo3` |

### How to import Excel data

1. Open the **Excel Import** section.
2. Click **Browse / Choose File**.
3. Select your `.xlsx` file.
4. Import the spreadsheet.
5. The application reads the spreadsheet columns and rows.
6. Each row is converted into an individual label.
7. Review the imported information in the **Labels Data** section at the bottom of the application.
8. Check the label preview before generating the final output.

## 7. Barcode and QR Code Detection Using Prefixes

Specify the content type directly inside an Excel cell using a prefix.

### Barcode

Use:

```text
barcode=YOUR_BARCODE_VALUE
```

Example:

```text
barcode=EO1001
```

The application identifies the prefix and generates a barcode using the remaining value.

### QR Code

Use:

```text
qr_code=YOUR_QR_CONTENT
```

Example:

```text
qr_code=https://www.freelabels.in
```

The application identifies the prefix and generates a QR code.

### Plain Text / Number

Enter the value without any prefix. Examples include `Essential Oil`, `₹500`, `Batch No. 1025`, and `Manufacturing Date: 08-11-2026`. These values are treated as normal text.

> **Important:** The prefix is an instruction to the application and is not intended to appear as part of the printed barcode or QR-code content.

## 8. Automatic Text Wrapping

Long product names or descriptions do not need manually inserted line breaks. Version 2 supports automatic text wrapping when text exceeds the available label width.

Example input:

```text
Premium Natural Lavender Essential Oil for Aromatherapy
```

The application automatically wraps the text onto additional lines when necessary. Keep descriptions concise to maintain readability, particularly on small labels.

## 9. Labels Data Section

The **Labels Data** section is located at the bottom of the application. After importing an Excel file, it displays the imported data, including product names, barcode values, QR-code content, prices, other text values, and additional custom columns.

Each spreadsheet row represents one label. Review the imported data before generating the final PDF or printer output.

## 10. Generating PDF Labels

1. Review the label dimensions.
2. Check the logo position.
3. Verify the custom fields.
4. Confirm the imported Excel data.
5. Inspect the label preview.
6. Click **Generate PDF**.
7. Select the destination for the generated PDF file.
8. Open the PDF and verify the output before printing.

### Improved PDF text support

Version 2 includes improved Unicode font support for the Indian Rupee symbol (₹), international currency symbols supported by the selected font, multilingual Unicode text, multiline text, and automatic text wrapping.

## 11. Printing Labels

1. Verify the PDF page size.
2. Confirm that the label dimensions match your physical label sheet.
3. Check the page margins and gaps.
4. Ensure the printer paper size matches the PDF.
5. Print a test page before printing a complete batch.

For thermal label printers, select the appropriate printer output format supported by the application.

## 12. Tips for Best Results

1. Always verify the label preview before generating the final output.
2. Use high-resolution logo images for better print quality.
3. Avoid unnecessarily long product descriptions.
4. Use valid barcode values supported by the selected barcode format.
5. Test QR codes using a smartphone before printing bulk labels.
6. Keep Excel column headings meaningful.
7. Avoid blank rows in the Excel spreadsheet.
8. Ensure Excel values are stored in the intended format, particularly product codes and numbers with leading zeros.
9. Use an appropriate label size for barcode and QR-code readability.
10. Print a sample label before starting large-scale production.

## 13. Version 2 Technical Improvements

### Unicode and encoding improvements

- UTF-8 text stream support.
- UTF-16 Little Endian and Big Endian support.
- Byte Order Mark (BOM) handling.
- Improved handling of multibyte characters.
- Surrogate-pair support.
- Defensive handling of malformed text data.

### Newline handling

- Unix/Linux LF.
- Windows CRLF.
- Legacy Mac CR.
- Source newline preservation.
- Explicit newline conversion.

### PDF rendering improvements

- Embedded TrueType Unicode fonts.
- Improved currency symbol rendering.
- Safer multiline text processing.
- Correct PDF text-block lifecycle management.
- Better handling of unsupported glyphs.

## 14. Version Information

**Product:** FreeLabels Desktop  
**Release:** Version 2.0  
**Major Focus:** Custom Label Creation, Excel Bulk Import, Barcode/QR Generation, Unicode Text and PDF Improvements.

---

**FreeLabels Desktop V2**  
*Create labels your way. Import your data. Generate labels effortlessly.*
