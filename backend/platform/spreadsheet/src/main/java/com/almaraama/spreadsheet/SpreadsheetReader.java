package com.almaraama.spreadsheet;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.poi.ooxml.util.SAXHelper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFRelation;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * Reads the first sheet of an {@code .xlsx} file safely (ADR-027, ASVS V5, V12). The file must be a real
 * {@code .xlsx} workbook (checked from its content, never its name), without macros, at most {@code maxBytes} bytes
 * and {@code maxRows} data rows. Reading is streamed (SAX) so memory stays bounded; ZIP bombs are refused by POI's
 * {@link ZipSecureFile} limits; XML external entities are refused by POI's secure parsers; formulas are never
 * evaluated (only their cached value is read, as text). The file is never written anywhere.
 * <p>
 * Layout of the templates: row 1 (hidden) holds the column keys, row 2 the labels in the person's language, data
 * starts at row 3. Columns are found by key, so a template in any language imports the same way.
 */
public final class SpreadsheetReader {

    /** Content type of the main part of a plain workbook; a macro-enabled workbook declares another one. */
    private static final String WORKBOOK_TYPE = XSSFRelation.WORKBOOK.getContentType();
    private static final Pattern MACROS = Pattern.compile("/xl/vbaProject\\.bin", Pattern.CASE_INSENSITIVE);
    private static final int KEY_ROW = 0;
    private static final int LABEL_ROW = 1;

    static {
        // ZIP bombs (ASVS V12.1.2): an entry may not inflate more than 100 times, nor exceed 20 MB.
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(20L * 1024 * 1024);
    }

    /** Static helpers only. */
    private SpreadsheetReader() {
    }

    /**
     * Reads the data rows of the first sheet.
     *
     * @param content      the uploaded bytes
     * @param requiredKeys column keys the hidden first row must contain
     * @param maxBytes     size limit of the file
     * @param maxRows      limit of non-empty data rows
     * @return the non-empty data rows, in file order
     * @throws SpreadsheetException {@code INVALID} or {@code TOO_LARGE}
     */
    public static List<SheetRow> read(byte[] content, Set<String> requiredKeys, int maxBytes, int maxRows) {
        if (content.length > maxBytes) {
            throw new SpreadsheetException(SpreadsheetException.Kind.TOO_LARGE, "File over " + maxBytes + " bytes");
        }
        if (!startsLikeZip(content)) {
            throw invalid("Not a ZIP container (an .xls file or another format)", null);
        }
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(content))) {
            checkWorkbook(pkg);
            XSSFReader reader = new XSSFReader(pkg);
            Iterator<InputStream> sheets = reader.getSheetsData();
            if (!sheets.hasNext()) {
                throw invalid("No sheet", null);
            }
            RowCollector collector = new RowCollector(maxRows);
            try (InputStream sheet = sheets.next()) {
                XMLReader parser = SAXHelper.newXMLReader();
                parser.setContentHandler(new XSSFSheetXMLHandler(reader.getStylesTable(),
                        new ReadOnlySharedStringsTable(pkg), collector, new DataFormatter(Locale.ROOT), false));
                parser.parse(new InputSource(sheet));
            }
            collector.requireKeys(requiredKeys);
            return collector.rows;
        } catch (SpreadsheetException e) {
            throw e;
        } catch (TooManyRows e) {
            throw new SpreadsheetException(SpreadsheetException.Kind.TOO_LARGE, "More than " + maxRows + " rows");
        } catch (Exception | LinkageError e) {
            // Corrupt archive, ZIP bomb, XML entity, unexpected structure: the same answer for every trap.
            throw invalid("Unreadable workbook: " + e.getClass().getSimpleName(), e);
        }
    }

    /** {@code INVALID} unless the package declares a plain workbook and carries no macro project. */
    private static void checkWorkbook(OPCPackage pkg) throws Exception {
        if (pkg.getPartsByContentType(WORKBOOK_TYPE).isEmpty()) {
            throw invalid("Not a plain .xlsx workbook (macros, template or another document)", null);
        }
        if (!pkg.getPartsByName(MACROS).isEmpty()) {
            throw invalid("Macro project present", null);
        }
    }

    /** Whether the bytes start with the local file header of a ZIP archive ({@code PK\3\4}). */
    private static boolean startsLikeZip(byte[] content) {
        return content.length > 4 && content[0] == 'P' && content[1] == 'K' && content[2] == 3 && content[3] == 4;
    }

    /** The single refusal for a file that is not an importable workbook. */
    private static SpreadsheetException invalid(String message, Throwable cause) {
        return new SpreadsheetException(SpreadsheetException.Kind.INVALID, message, cause);
    }

    /** Thrown from inside the SAX handler to stop reading as soon as the row limit is passed. */
    private static final class TooManyRows extends RuntimeException {
        TooManyRows() {
            super(null, null, false, false);
        }
    }

    /** Collects the keys of the hidden first row and the non-empty data rows. */
    private static final class RowCollector implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final int maxRows;
        private final Map<Integer, String> keysByColumn = new HashMap<>();
        private final List<SheetRow> rows = new ArrayList<>();
        private Map<String, String> current;
        private int currentRow;

        /** @param maxRows limit of non-empty data rows */
        RowCollector(int maxRows) {
            this.maxRows = maxRows;
        }

        /** Starts collecting the cells of a row. */
        @Override
        public void startRow(int rowNum) {
            currentRow = rowNum;
            current = new HashMap<>();
        }

        /** Keeps a non-empty data row; stops reading when there are too many. */
        @Override
        public void endRow(int rowNum) {
            if (rowNum > LABEL_ROW && !current.isEmpty()) {
                if (rows.size() >= maxRows) {
                    throw new TooManyRows();
                }
                rows.add(new SheetRow(rowNum + 1, current));
            }
        }

        /** Records a key of the first row, or the trimmed value of a data cell under a known key. */
        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            if (cellReference == null || formattedValue == null) {
                return;
            }
            int column = new CellReference(cellReference).getCol();
            String value = formattedValue.strip();
            if (value.isEmpty()) {
                return;
            }
            if (currentRow == KEY_ROW) {
                keysByColumn.put(column, value);
            } else if (currentRow > LABEL_ROW) {
                String key = keysByColumn.get(column);
                if (key != null) {
                    current.put(key, value);
                }
            }
        }

        /** {@code INVALID} when the hidden key row lacks a column of the template. */
        void requireKeys(Set<String> requiredKeys) {
            if (!keysByColumn.values().containsAll(requiredKeys)) {
                throw invalid("Not built from the template: key row incomplete", null);
            }
        }
    }
}
