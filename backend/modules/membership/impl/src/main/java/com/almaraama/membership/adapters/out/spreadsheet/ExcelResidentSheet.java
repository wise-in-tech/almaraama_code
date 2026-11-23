package com.almaraama.membership.adapters.out.spreadsheet;

import com.almaraama.membership.domain.ResidentSheet;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.Language;
import com.almaraama.spreadsheet.SheetRow;
import com.almaraama.spreadsheet.SpreadsheetException;
import com.almaraama.spreadsheet.SpreadsheetReader;
import com.almaraama.spreadsheet.TemplateWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link ResidentSheet} with the hardened reader of {@code platform/spreadsheet} (ADR-027): {@code .xlsx} only, 2 MB,
 * 2,000 rows, traps refused; the file is never stored. The template is produced in the person's language.
 */
@Component
class ExcelResidentSheet implements ResidentSheet {

    private static final Logger LOG = LoggerFactory.getLogger(ExcelResidentSheet.class);
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_ROWS = 2_000;

    /** Labels of the columns, in the order of {@link ResidentSheet#COLUMNS}, per language. */
    private static final Map<Language, List<String>> LABELS = Map.of(
            Language.AR, List.of("اللقب", "الاسم", "البريد الإلكتروني", "الهاتف", "اللغة", "الوحدة", "الصفة",
                    "مستأجر رئيسي"),
            Language.EN, List.of("Last name", "First name", "E-mail", "Phone", "Language", "Unit", "Role",
                    "Primary tenant"),
            Language.FR, List.of("Nom", "Prénom", "E-mail", "Téléphone", "Langue", "Bien", "Rôle",
                    "Locataire principal"));

    /** Help lines of the template, per language. */
    private static final Map<Language, List<String>> HELP = Map.of(
            Language.AR, List.of("سطر واحد لكل وحدة مقررة لشخص.",
                    "الأسطر التي تحمل نفس البريد الإلكتروني تكوّن تسجيلًا مسبقًا واحدًا ومفتاحًا واحدًا.",
                    "الوحدة: مرجع وحدة موجودة في الإقامة. الصفة: OWNER (مالك) أو TENANT (مستأجر).",
                    "اللغة: AR أو EN أو FR. الهاتف: رقم جوال بالصيغة الدولية أو الجزائرية.",
                    "لا تغيّر السطر الأول المخفي ولا ترتيب الأعمدة."),
            Language.EN, List.of("One row per unit planned for a person.",
                    "Rows with the same e-mail make one pre-registration and one key.",
                    "Unit: reference of an existing unit of the residence. Role: OWNER or TENANT.",
                    "Language: AR, EN or FR. Phone: mobile number, international or Algerian form.",
                    "Do not change the hidden first row nor the order of the columns."),
            Language.FR, List.of("Une ligne par bien prévu pour une personne.",
                    "Les lignes d'un même e-mail forment une seule pré-inscription et une seule clé.",
                    "Bien : référence d'un bien existant de la résidence. Rôle : OWNER (propriétaire) ou TENANT "
                            + "(locataire).",
                    "Langue : AR, EN ou FR. Téléphone : mobile, au format international ou algérien.",
                    "Ne modifiez ni la première ligne masquée ni l'ordre des colonnes."));

    private static final Map<Language, String[]> SHEETS = Map.of(
            Language.AR, new String[]{"السكان", "مساعدة"},
            Language.EN, new String[]{"Residents", "Help"},
            Language.FR, new String[]{"Résidents", "Aide"});

    /** Reads the lines; maps the reader's refusals to the contract's errors (415, 413). */
    @Override
    public List<Line> read(byte[] content) {
        try {
            List<Line> lines = new ArrayList<>();
            for (SheetRow row : SpreadsheetReader.read(content, Set.copyOf(COLUMNS), MAX_BYTES, MAX_ROWS)) {
                lines.add(new Line(row.excelRow(), row.values()));
            }
            return lines;
        } catch (SpreadsheetException e) {
            LOG.debug("Resident import file refused: {}", e.getMessage());
            throw e.kind() == SpreadsheetException.Kind.TOO_LARGE
                    ? new BusinessException("IMPORT_FILE_TOO_LARGE", ErrorCategory.TOO_LARGE, "Over the limits")
                    : new BusinessException("IMPORT_FILE_INVALID", ErrorCategory.UNSUPPORTED_CONTENT,
                    "Not an .xlsx built from the template");
        }
    }

    /** The template: labels and help in the language, lists of languages, roles and yes/no. */
    @Override
    public byte[] template(Language language) {
        List<String> labels = LABELS.get(language);
        List<TemplateWriter.Column> columns = new ArrayList<>();
        for (int i = 0; i < COLUMNS.size(); i++) {
            String key = COLUMNS.get(i);
            List<String> allowed = switch (key) {
                case "language" -> List.of("AR", "EN", "FR");
                case "role" -> List.of("OWNER", "TENANT");
                case "primaryTenant" -> List.of("TRUE", "FALSE");
                default -> List.of();
            };
            columns.add(new TemplateWriter.Column(key, labels.get(i), !key.equals("primaryTenant"), allowed));
        }
        String[] sheets = SHEETS.get(language);
        return TemplateWriter.write(new TemplateWriter.Spec(sheets[0], columns, sheets[1], HELP.get(language),
                language == Language.AR));
    }
}
