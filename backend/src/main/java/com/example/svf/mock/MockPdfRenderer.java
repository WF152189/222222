package com.example.svf.mock;

import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class MockPdfRenderer {
    private static final int MAX_FONTS = 4;
    // Microsoft YaHei covers simplified Chinese plus kana and common kanji, so it is preferred.
    private static final List<Path> FONT_CANDIDATES = List.of(
            Path.of("C:/Windows/Fonts/msyh.ttc"),
            Path.of("C:/Windows/Fonts/meiryo.ttc"),
            Path.of("C:/Windows/Fonts/YuGothM.ttc"),
            Path.of("C:/Windows/Fonts/msgothic.ttc"),
            Path.of("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
            Path.of("/usr/share/fonts/opentype/noto/NotoSansCJKjp-Regular.otf")
    );

    public byte[] render(String artifactName, String formPath, String csvData, String userName) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            List<PDFont> fonts = loadFonts(document);
            PDFont fallback = fonts.isEmpty()
                    ? new PDType1Font(Standard14Fonts.FontName.HELVETICA)
                    : null;
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                writeLine(content, fonts, fallback, 50, 780, 18, "SVF Cloud Mock PDF");
                writeLine(content, fonts, fallback, 50, 740, 10, "Artifact: " + artifactName);
                writeLine(content, fonts, fallback, 50, 720, 10, "User: " + userName);
                writeLine(content, fonts, fallback, 50, 700, 10, "Form: " + formPath);
                writeLine(content, fonts, fallback, 50, 680, 10, "Generated: " + OffsetDateTime.now());
                writeLine(content, fonts, fallback, 50, 650, 10, "CSV preview:");

                List<String> lines = csvData.lines().limit(14).toList();
                int y = 630;
                for (String line : lines) {
                    writeLine(content, fonts, fallback, 50, y, 10, abbreviate(line, 95));
                    y -= 18;
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to render mock PDF", ex);
        }
    }

    private List<PDFont> loadFonts(PDDocument document) {
        List<PDFont> fonts = new ArrayList<>();
        for (Path candidate : FONT_CANDIDATES) {
            if (fonts.size() >= MAX_FONTS || !Files.exists(candidate)) {
                continue;
            }
            try {
                fonts.add(loadFont(document, candidate.toFile()));
            } catch (IOException | RuntimeException ignored) {
                // Try next font candidate.
            }
        }
        return fonts;
    }

    private PDFont loadFont(PDDocument document, File fontFile) throws IOException {
        String lowerName = fontFile.getName().toLowerCase();
        if (lowerName.endsWith(".ttc")) {
            AtomicReference<PDFont> fontRef = new AtomicReference<>();
            try (TrueTypeCollection collection = new TrueTypeCollection(fontFile)) {
                collection.processAllFonts((TrueTypeFont trueTypeFont) -> {
                    if (fontRef.get() == null) {
                        fontRef.set(PDType0Font.load(document, trueTypeFont, true));
                    }
                });
            }
            PDFont font = fontRef.get();
            if (font != null) {
                return font;
            }
            throw new IOException("No TrueType font found in collection: " + fontFile);
        }
        return PDType0Font.load(document, fontFile);
    }

    private void writeLine(PDPageContentStream content, List<PDFont> fonts, PDFont fallback,
                           int x, int y, float size, String text) throws IOException {
        content.beginText();
        content.newLineAtOffset(x, y);
        if (fonts.isEmpty()) {
            content.setFont(fallback, size);
            content.showText(text.replaceAll("[^\\x20-\\x7E]", "?"));
            content.endText();
            return;
        }
        for (TextRun run : splitIntoRuns(fonts, text)) {
            showRun(content, fonts, size, run);
        }
        content.endText();
    }

    private List<TextRun> splitIntoRuns(List<PDFont> fonts, String text) {
        List<TextRun> runs = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int currentFontIndex = Integer.MIN_VALUE;
        int offset = 0;
        while (offset < text.length()) {
            int codePoint = text.codePointAt(offset);
            int fontIndex = findFontIndex(fonts, codePoint);
            if (fontIndex != currentFontIndex && buffer.length() > 0) {
                runs.add(new TextRun(currentFontIndex, buffer.toString()));
                buffer.setLength(0);
            }
            currentFontIndex = fontIndex;
            buffer.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }
        if (buffer.length() > 0) {
            runs.add(new TextRun(currentFontIndex, buffer.toString()));
        }
        return runs;
    }

    private int findFontIndex(List<PDFont> fonts, int codePoint) {
        for (int i = 0; i < fonts.size(); i++) {
            PDFont font = fonts.get(i);
            if (font instanceof PDType0Font type0Font) {
                try {
                    if (type0Font.hasGlyph(codePoint)) {
                        return i;
                    }
                } catch (IOException | RuntimeException ignored) {
                    // Treat unreadable fonts as missing the glyph.
                }
            }
        }
        return -1;
    }

    private void showRun(PDPageContentStream content, List<PDFont> fonts, float size, TextRun run) throws IOException {
        List<Integer> order = new ArrayList<>();
        if (run.fontIndex() >= 0) {
            order.add(run.fontIndex());
        }
        for (int i = 0; i < fonts.size(); i++) {
            if (!order.contains(i)) {
                order.add(i);
            }
        }
        for (int fontIndex : order) {
            try {
                content.setFont(fonts.get(fontIndex), size);
                content.showText(run.text());
                return;
            } catch (IOException | RuntimeException ignored) {
                // GSUB lookup can fail for some TTC fonts; fall back to the next font.
            }
        }
        content.setFont(fonts.get(0), size);
        content.showText(run.text().replaceAll("[^\\x20-\\x7E]", "?"));
    }

    private String abbreviate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }

    private record TextRun(int fontIndex, String text) {
    }
}
