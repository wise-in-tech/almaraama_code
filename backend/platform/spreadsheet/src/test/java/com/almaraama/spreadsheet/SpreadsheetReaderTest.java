package com.almaraama.spreadsheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * ADR-027: the reader accepts a filled template and refuses every trapped or foreign file with the same answer (one
 * test per kind of trap: ZIP bomb, XML external entity, renamed .xls, macros, too many rows, too many bytes, not built
 * from the template).
 */
class SpreadsheetReaderTest {

    private static final Set<String> KEYS = Set.of("reference", "type");
    private static final int TWO_MB = 2 * 1024 * 1024;

    @Test
    void A6_aFilledTemplateIsReadByKeyWhateverTheLanguageOfTheLabels() throws IOException {
        byte[] template = TemplateWriter.write(new TemplateWriter.Spec("الوحدات",
                List.of(new TemplateWriter.Column("reference", "المرجع", true, List.of()),
                        new TemplateWriter.Column("type", "النوع", true, List.of("APARTMENT", "PARKING"))),
                "مساعدة", List.of("سطر"), true));
        byte[] filled = fill(template, List.of(List.of("A-1", "APARTMENT"), List.of(), List.of(" P-1 ", "PARKING")));

        List<SheetRow> rows = SpreadsheetReader.read(filled, KEYS, TWO_MB, 10);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).excelRow()).isEqualTo(3);
        assertThat(rows.get(0).get("reference")).contains("A-1");
        assertThat(rows.get(1).excelRow()).isEqualTo(5);
        assertThat(rows.get(1).get("reference")).contains("P-1");
    }

    @Test
    void tooManyRowsAreRefusedAsTooLarge() throws IOException {
        byte[] filled = fill(template(), List.of(List.of("A", "APARTMENT"), List.of("B", "APARTMENT"),
                List.of("C", "APARTMENT")));
        assertRefused(() -> SpreadsheetReader.read(filled, KEYS, TWO_MB, 2), SpreadsheetException.Kind.TOO_LARGE);
    }

    @Test
    void tooManyBytesAreRefusedAsTooLarge() throws IOException {
        byte[] filled = fill(template(), List.of(List.of("A", "APARTMENT")));
        assertRefused(() -> SpreadsheetReader.read(filled, KEYS, 100, 10), SpreadsheetException.Kind.TOO_LARGE);
    }

    @Test
    void anOldXlsFileRenamedIsRefused() {
        byte[] ole2 = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1, 0, 0};
        assertRefused(() -> SpreadsheetReader.read(ole2, KEYS, TWO_MB, 10), SpreadsheetException.Kind.INVALID);
    }

    @Test
    void aPlainZipIsRefused() throws IOException {
        byte[] zip = rezip(Map.of("hello.txt", "hi".getBytes(StandardCharsets.UTF_8)));
        assertRefused(() -> SpreadsheetReader.read(zip, KEYS, TWO_MB, 10), SpreadsheetException.Kind.INVALID);
    }

    @Test
    void aWorkbookWithMacrosIsRefused() throws IOException {
        byte[] filled = fill(template(), List.of(List.of("A", "APARTMENT")));
        byte[] macro = rewrite(filled, entries -> {
            String types = new String(entries.get("[Content_Types].xml"), StandardCharsets.UTF_8)
                    .replace("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
                            "application/vnd.ms-excel.sheet.macroEnabled.main+xml");
            entries.put("[Content_Types].xml", types.getBytes(StandardCharsets.UTF_8));
            entries.put("xl/vbaProject.bin", new byte[]{1, 2, 3});
            return entries;
        });
        assertRefused(() -> SpreadsheetReader.read(macro, KEYS, TWO_MB, 10), SpreadsheetException.Kind.INVALID);
    }

    @Test
    void anXmlExternalEntityIsRefused() throws IOException {
        byte[] filled = fill(template(), List.of(List.of("A", "APARTMENT")));
        byte[] xxe = rewrite(filled, entries -> {
            String sheet = new String(entries.get("xl/worksheets/sheet1.xml"), StandardCharsets.UTF_8);
            int afterDeclaration = sheet.indexOf("?>") + 2;
            sheet = sheet.substring(0, afterDeclaration)
                    + "<!DOCTYPE x [<!ENTITY secret SYSTEM \"file:///etc/passwd\">]>"
                    + sheet.substring(afterDeclaration).replace(">A<", ">&secret;<");
            entries.put("xl/worksheets/sheet1.xml", sheet.getBytes(StandardCharsets.UTF_8));
            return entries;
        });
        assertRefused(() -> SpreadsheetReader.read(xxe, KEYS, TWO_MB, 10), SpreadsheetException.Kind.INVALID);
    }

    @Test
    void aZipBombIsRefused() throws IOException {
        byte[] filled = fill(template(), List.of(List.of("A", "APARTMENT")));
        byte[] bomb = rewrite(filled, entries -> {
            entries.put("xl/bomb.xml", new byte[30 * 1024 * 1024]);
            return entries;
        });
        assertThat(bomb.length).isLessThan(TWO_MB);
        assertRefused(() -> SpreadsheetReader.read(bomb, KEYS, TWO_MB, 10), SpreadsheetException.Kind.INVALID);
    }

    @Test
    void aWorkbookNotBuiltFromTheTemplateIsRefused() throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("x");
            sheet.createRow(0).createCell(0).setCellValue("Référence");
            sheet.createRow(2).createCell(0).setCellValue("A-1");
            workbook.write(out);
            assertRefused(() -> SpreadsheetReader.read(out.toByteArray(), KEYS, TWO_MB, 10),
                    SpreadsheetException.Kind.INVALID);
        }
    }

    // ------------------------------------------------------------------------------------------- helpers

    /** A two-column template in French. */
    private static byte[] template() {
        return TemplateWriter.write(new TemplateWriter.Spec("Biens",
                List.of(new TemplateWriter.Column("reference", "Référence", true, List.of()),
                        new TemplateWriter.Column("type", "Type", true, List.of("APARTMENT", "PARKING"))),
                "Aide", List.of("Une ligne par bien"), false));
    }

    /** Writes the rows below the labels of a template, as a person would in Excel. */
    private static byte[] fill(byte[] template, List<List<String>> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 2);
                for (int c = 0; c < rows.get(r).size(); c++) {
                    row.createCell(c).setCellValue(rows.get(r).get(c));
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /** Rewrites the entries of an archive. */
    private static byte[] rewrite(byte[] zip, UnaryOperator<Map<String, byte[]>> change) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), in.readAllBytes());
            }
        }
        return rezip(change.apply(entries));
    }

    /** An archive with these entries. */
    private static byte[] rezip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** The call is refused with this kind. */
    private static void assertRefused(Runnable call, SpreadsheetException.Kind kind) {
        assertThatThrownBy(call::run).isInstanceOf(SpreadsheetException.class)
                .extracting(e -> ((SpreadsheetException) e).kind()).isEqualTo(kind);
    }
}
