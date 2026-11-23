package com.almaraama.spreadsheet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Produces an import template (ADR-027): the data sheet first, with the column keys in a hidden first row (what
 * {@link SpreadsheetReader} reads), the labels in the person's language in the second row, drop-down lists for the
 * closed values, then a help sheet. Generated at each request, never served from a static file.
 */
public final class TemplateWriter {

    /** Rows offered with drop-down lists below the labels (the import accepts up to 2,000). */
    private static final int LIST_ROWS = 2_000;
    private static final int COLUMN_WIDTH = 22 * 256;

    /**
     * One column of a template.
     *
     * @param key           column key read back by the import ({@code reference})
     * @param label         header in the person's language
     * @param required      whether the label is marked as mandatory ({@code *})
     * @param allowedValues closed list offered as a drop-down; empty for free text
     */
    public record Column(String key, String label, boolean required, List<String> allowedValues) {
        public Column {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(label, "label");
            allowedValues = List.copyOf(allowedValues);
        }
    }

    /**
     * What to write.
     *
     * @param dataSheetName name of the data sheet, in the person's language
     * @param columns       the columns, in order
     * @param helpSheetName name of the help sheet
     * @param helpLines     one instruction per line
     * @param rightToLeft   sheets written right to left (Arabic)
     */
    public record Spec(String dataSheetName, List<Column> columns, String helpSheetName, List<String> helpLines,
                       boolean rightToLeft) {
        public Spec {
            columns = List.copyOf(columns);
            helpLines = List.copyOf(helpLines);
        }
    }

    /** Static helpers only. */
    private TemplateWriter() {
    }

    /** The {@code .xlsx} bytes of the template. */
    public static byte[] write(Spec spec) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet data = workbook.createSheet(WorkbookUtil.createSafeSheetName(spec.dataSheetName()));
            Sheet help = workbook.createSheet(WorkbookUtil.createSafeSheetName(spec.helpSheetName()));
            Sheet lists = workbook.createSheet("lists");
            workbook.setSheetVisibility(workbook.getSheetIndex(lists), SheetVisibility.VERY_HIDDEN);
            data.setRightToLeft(spec.rightToLeft());
            help.setRightToLeft(spec.rightToLeft());

            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);

            Row keys = data.createRow(0);
            keys.setZeroHeight(true);
            Row labels = data.createRow(1);
            DataValidationHelper validations = data.getDataValidationHelper();
            for (int c = 0; c < spec.columns().size(); c++) {
                Column column = spec.columns().get(c);
                keys.createCell(c).setCellValue(column.key());
                var label = labels.createCell(c);
                label.setCellValue(column.required() ? column.label() + " *" : column.label());
                label.setCellStyle(bold);
                data.setColumnWidth(c, COLUMN_WIDTH);
                if (!column.allowedValues().isEmpty()) {
                    offerList(workbook, lists, data, validations, c, column);
                }
            }
            data.createFreezePane(0, 2);

            for (int i = 0; i < spec.helpLines().size(); i++) {
                help.createRow(i).createCell(0).setCellValue(spec.helpLines().get(i));
            }
            help.setColumnWidth(0, 120 * 256);
            workbook.setActiveSheet(0);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes the allowed values in the hidden {@code lists} sheet and offers them as a drop-down on the data rows of
     * the column (a named range, so that long lists such as categories are not limited to 255 characters).
     */
    private static void offerList(XSSFWorkbook workbook, Sheet lists, Sheet data, DataValidationHelper validations,
                                  int column, Column spec) {
        List<String> values = spec.allowedValues();
        for (int r = 0; r < values.size(); r++) {
            Row row = lists.getRow(r) == null ? lists.createRow(r) : lists.getRow(r);
            row.createCell(column).setCellValue(values.get(r));
        }
        String letter = CellReference.convertNumToColString(column);
        Name name = workbook.createName();
        name.setNameName("list_" + column);
        name.setRefersToFormula("lists!$" + letter + "$1:$" + letter + "$" + values.size());
        DataValidationConstraint constraint = validations.createFormulaListConstraint("list_" + column);
        DataValidation validation = validations.createValidation(constraint,
                new CellRangeAddressList(2, LIST_ROWS + 1, column, column));
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(true);
        data.addValidationData(validation);
    }
}
