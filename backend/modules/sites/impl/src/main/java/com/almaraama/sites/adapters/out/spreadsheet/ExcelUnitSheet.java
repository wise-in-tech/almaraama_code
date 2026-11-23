package com.almaraama.sites.adapters.out.spreadsheet;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.Language;
import com.almaraama.sites.api.UnitType;
import com.almaraama.sites.domain.UnitSheet;
import com.almaraama.spreadsheet.SheetRow;
import com.almaraama.spreadsheet.SpreadsheetException;
import com.almaraama.spreadsheet.SpreadsheetReader;
import com.almaraama.spreadsheet.TemplateWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link UnitSheet} with the hardened reader of {@code platform/spreadsheet} (ADR-027): {@code .xlsx} only, 2 MB,
 * 2,000 rows, traps refused; the file is never stored. The template is produced in the person's language.
 */
@Component
class ExcelUnitSheet implements UnitSheet {

    private static final Logger LOG = LoggerFactory.getLogger(ExcelUnitSheet.class);
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_ROWS = 2_000;

    /** Labels of the columns, in the order of {@link UnitSheet#COLUMNS}, per language. */
    private static final Map<Language, List<String>> LABELS = Map.of(
            Language.AR, List.of("المرجع", "النوع", "الفئة", "العمارة", "المساحة الخارجية", "الطابق", "ملحق بـ",
                    "قابل للبيع", "قابل للإيجار"),
            Language.EN, List.of("Reference", "Type", "Category", "Building", "Outdoor area", "Floor", "Annex of",
                    "Saleable", "Rentable"),
            Language.FR, List.of("Référence", "Type", "Catégorie", "Bâtiment", "Espace extérieur", "Étage",
                    "Annexe de", "Vendable", "Louable"));

    /** Help lines of the template, per language. */
    private static final Map<Language, List<String>> HELP = Map.of(
            Language.AR, List.of("سطر واحد لكل وحدة. المرجع والنوع والفئة إلزامية.",
                    "النوع: APARTMENT (شقة)، SHOP (محل)، PARKING (موقف)، STORAGE (قبو)، OTHER (أخرى).",
                    "الفئة: رمز فئة متاحة في الإقامة (مثلًا T3 لشقة).",
                    "العمارة أو المساحة الخارجية: الاسم كما هو في الإقامة، وليس الاثنين معًا. الطابق داخل عمارة فقط.",
                    "ملحق بـ: مرجع شقة (للموقف أو القبو فقط)، في الإقامة أو في هذا الملف.",
                    "قابل للبيع / للإيجار: TRUE أو FALSE (TRUE إذا تُرك فارغًا).",
                    "لا تغيّر السطر الأول المخفي."),
            Language.EN, List.of("One row per unit. Reference, type and category are mandatory.",
                    "Type: APARTMENT, SHOP, PARKING, STORAGE, OTHER.",
                    "Category: code of a category usable in the residence (for example T3 for an apartment).",
                    "Building or outdoor area: its name in the residence, never both. A floor only inside a building.",
                    "Annex of: reference of an apartment (parking or storage only), in the residence or this file.",
                    "Saleable / rentable: TRUE or FALSE (TRUE when empty).",
                    "Do not change the hidden first row."),
            Language.FR, List.of("Une ligne par bien. Référence, type et catégorie sont obligatoires.",
                    "Type : APARTMENT (appartement), SHOP (magasin), PARKING, STORAGE (cave), OTHER (autre).",
                    "Catégorie : code d'une catégorie utilisable dans la résidence (par exemple T3 pour un "
                            + "appartement).",
                    "Bâtiment ou espace extérieur : son nom dans la résidence, jamais les deux. Un étage seulement "
                            + "dans un bâtiment.",
                    "Annexe de : référence d'un appartement (parking ou cave seulement), dans la résidence ou ce "
                            + "fichier.",
                    "Vendable / louable : TRUE ou FALSE (TRUE si vide).",
                    "Ne modifiez pas la première ligne masquée."));

    private static final Map<Language, String[]> SHEETS = Map.of(
            Language.AR, new String[]{"الوحدات", "مساعدة"},
            Language.EN, new String[]{"Units", "Help"},
            Language.FR, new String[]{"Biens", "Aide"});

    /** Reads the lines; maps the reader's refusals to the contract's errors (415, 413). */
    @Override
    public List<Line> read(byte[] content) {
        try {
            List<Line> lines = new ArrayList<>();
            for (SheetRow row : SpreadsheetReader.read(content, Set.copyOf(REQUIRED_COLUMNS), MAX_BYTES, MAX_ROWS)) {
                lines.add(new Line(row.excelRow(), row.values()));
            }
            return lines;
        } catch (SpreadsheetException e) {
            LOG.debug("Unit import file refused: {}", e.getMessage());
            throw e.kind() == SpreadsheetException.Kind.TOO_LARGE
                    ? new BusinessException("IMPORT_FILE_TOO_LARGE", ErrorCategory.TOO_LARGE, "Over the limits")
                    : new BusinessException("IMPORT_FILE_INVALID", ErrorCategory.UNSUPPORTED_CONTENT,
                    "Not an .xlsx built from the template");
        }
    }

    /** The template: labels and help in the language, lists of types, categories and yes/no. */
    @Override
    public byte[] template(Language language, List<String> categoryCodes) {
        List<String> labels = LABELS.get(language);
        List<TemplateWriter.Column> columns = new ArrayList<>();
        for (int i = 0; i < COLUMNS.size(); i++) {
            String key = COLUMNS.get(i);
            List<String> allowed = switch (key) {
                case "type" -> Arrays.stream(UnitType.values()).map(Enum::name).toList();
                case "category" -> categoryCodes;
                case "saleable", "rentable" -> List.of("TRUE", "FALSE");
                default -> List.of();
            };
            columns.add(new TemplateWriter.Column(key, labels.get(i), REQUIRED_COLUMNS.contains(key), allowed));
        }
        String[] sheets = SHEETS.get(language);
        return TemplateWriter.write(new TemplateWriter.Spec(sheets[0], columns, sheets[1], HELP.get(language),
                language == Language.AR));
    }
}
