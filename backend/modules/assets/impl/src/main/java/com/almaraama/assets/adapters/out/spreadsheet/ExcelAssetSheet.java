package com.almaraama.assets.adapters.out.spreadsheet;

import com.almaraama.assets.domain.AssetSheet;
import com.almaraama.assets.domain.AssetState;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.Language;
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
 * {@link AssetSheet} with the hardened reader of {@code platform/spreadsheet} (ADR-027 revision 3): {@code .xlsx}
 * only, 2 MB, 2,000 rows, traps refused; the file is never stored. The template is produced in the person's language.
 */
@Component
class ExcelAssetSheet implements AssetSheet {

    private static final Logger LOG = LoggerFactory.getLogger(ExcelAssetSheet.class);
    static final int MAX_BYTES = 2 * 1024 * 1024;
    static final int MAX_ROWS = 2_000;

    /** Labels of the columns, in the order of {@link AssetSheet#COLUMNS}, per language. */
    private static final Map<Language, List<String>> LABELS = Map.of(
            Language.AR, List.of("الاسم", "العائلة", "الطراز", "المرجع", "العمارة", "الطابق", "المساحة الخارجية",
                    "الرقم التسلسلي", "الحالة", "تاريخ التشغيل", "المورد"),
            Language.EN, List.of("Name", "Family", "Model", "Reference", "Building", "Floor", "Outdoor area",
                    "Serial number", "State", "Commissioned on", "Supplier"),
            Language.FR, List.of("Nom", "Famille", "Modèle", "Référence", "Bâtiment", "Étage", "Espace extérieur",
                    "Numéro de série", "État", "Mise en service", "Fournisseur"));

    /** Help lines of the template, per language. */
    private static final Map<Language, List<String>> HELP = Map.of(
            Language.AR, List.of("سطر واحد لكل تجهيز. الاسم والعائلة إلزاميان.",
                    "العائلة: رمز عائلة متاحة (مثلًا ELEVATOR). الطراز: اسم طراز من هذه العائلة.",
                    "المرجع: فريد في الإقامة؛ يُنشأ تلقائيًا إذا تُرك فارغًا.",
                    "العمارة أو المساحة الخارجية: الاسم كما هو في الإقامة، وليس الاثنين معًا. الطابق داخل عمارة فقط.",
                    "الحالة: IN_SERVICE أو OUT_OF_ORDER أو DECOMMISSIONED (IN_SERVICE إذا تُرك فارغًا).",
                    "تاريخ التشغيل: YYYY-MM-DD. المورد: اسم مورد مسجّل.",
                    "لا تغيّر السطر الأول المخفي."),
            Language.EN, List.of("One row per equipment. Name and family are mandatory.",
                    "Family: code of a usable family (for example ELEVATOR). Model: name of a model of that family.",
                    "Reference: unique in the residence; generated when empty.",
                    "Building or outdoor area: its name in the residence, never both. A floor only inside a building.",
                    "State: IN_SERVICE, OUT_OF_ORDER or DECOMMISSIONED (IN_SERVICE when empty).",
                    "Commissioned on: YYYY-MM-DD. Supplier: name of a recorded supplier.",
                    "Do not change the hidden first row."),
            Language.FR, List.of("Une ligne par équipement. Nom et famille sont obligatoires.",
                    "Famille : code d'une famille utilisable (par exemple ELEVATOR). Modèle : nom d'un modèle de "
                            + "cette famille.",
                    "Référence : unique dans la résidence ; générée si vide.",
                    "Bâtiment ou espace extérieur : son nom dans la résidence, jamais les deux. Un étage seulement "
                            + "dans un bâtiment.",
                    "État : IN_SERVICE (en service), OUT_OF_ORDER (en panne) ou DECOMMISSIONED (hors service) ; "
                            + "IN_SERVICE si vide.",
                    "Mise en service : AAAA-MM-JJ. Fournisseur : nom d'un fournisseur enregistré.",
                    "Ne modifiez pas la première ligne masquée."));

    private static final Map<Language, String[]> SHEETS = Map.of(
            Language.AR, new String[]{"التجهيزات", "مساعدة"},
            Language.EN, new String[]{"Equipment", "Help"},
            Language.FR, new String[]{"Équipements", "Aide"});

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
            LOG.debug("Equipment import file refused: {}", e.getMessage());
            throw e.kind() == SpreadsheetException.Kind.TOO_LARGE
                    ? new BusinessException("IMPORT_FILE_TOO_LARGE", ErrorCategory.TOO_LARGE, "Over the limits")
                    : new BusinessException("IMPORT_FILE_INVALID", ErrorCategory.UNSUPPORTED_CONTENT,
                    "Not an .xlsx built from the template");
        }
    }

    /** The template: labels and help in the language, lists of families, states, buildings, areas, suppliers. */
    @Override
    public byte[] template(Language language, TemplateLists lists) {
        List<String> labels = LABELS.get(language);
        List<TemplateWriter.Column> columns = new ArrayList<>();
        for (int i = 0; i < COLUMNS.size(); i++) {
            String key = COLUMNS.get(i);
            List<String> allowed = switch (key) {
                case "family" -> lists.familyCodes();
                case "state" -> Arrays.stream(AssetState.values()).map(Enum::name).toList();
                case "building" -> lists.buildings();
                case "outdoorArea" -> lists.outdoorAreas();
                case "supplier" -> lists.suppliers();
                default -> List.of();
            };
            columns.add(new TemplateWriter.Column(key, labels.get(i), REQUIRED_COLUMNS.contains(key), allowed));
        }
        String[] sheets = SHEETS.get(language);
        return TemplateWriter.write(new TemplateWriter.Spec(sheets[0], columns, sheets[1], HELP.get(language),
                language == Language.AR));
    }
}
