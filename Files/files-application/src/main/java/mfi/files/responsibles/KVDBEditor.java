package mfi.files.responsibles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import mfi.files.annotation.Responsible;
import mfi.files.helper.Hilfsklasse;
import mfi.files.htmlgen.Button;
import mfi.files.htmlgen.ButtonBar;
import mfi.files.htmlgen.HTMLTable;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.io.FilesFile;
import mfi.files.logic.Filter;
import mfi.files.logic.Filter.IgnoreCase;
import mfi.files.logic.Filter.Preset;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Condition;
import mfi.files.model.Model;

@Component
public class KVDBEditor extends AbstractResponsible {

    private static final String COVER_DOTS = StringUtils.leftPad("", 3, Character.toString((char) 0x00b7));

    private static final String SEL_VALUE = "sel_value";

    private static final String SEL_KEY = "sel_key";

    private static final String NEG_FILTER = "neg_filter";

    private static final String POS_FILTER = "pos_filter";

    private static final List<String> COVER_VALUE_TOKEN_PREFIX = List.of(KVMemoryMap.KVDB_KEY_LOGINTOKEN);

    private static final List<String> COVER_VALUE_COMPLETE_PREFIX =
        List.of(FilesFile.APPLICATION_PROPERTIES_CIPHER_FILE_NAME_CRYPTO_KEY, KVMemoryMap.PREFIX_CRYPTO_ENTRY_ENC);

    private static final List<String> COVER_VALUE_COMPLETE_SUFFIX =
        List.of(".loginTokenSecret", ".pass", ".pin", ".resetPass", ".apiToken");

	@Responsible(conditions = { Condition.KVDB_EDIT_START, Condition.KVDB_EDIT, Condition.KVDB_DELETE, Condition.KVDB_INSERT,
			Condition.KVDB_RESET })
    public void backup(StringBuilder sb, Map<String, String> parameters, Model model) throws IOException {

		String posFilter = StringUtils.trimToEmpty(parameters.get(POS_FILTER));
		String negFilter = StringUtils.trimToEmpty(parameters.get(NEG_FILTER));
		String selKey = StringUtils.trimToEmpty(parameters.get(SEL_KEY));
		String selValue = StringUtils.trimToEmpty(parameters.get(SEL_VALUE));
		List<String> inhalt = KVMemoryMap.getInstance().dumpAsList();
		List<String> filtered = filter(inhalt, posFilter, negFilter);

		if (model.lookupConversation().getCondition().equals(Condition.KVDB_RESET)) {
			posFilter = "";
			negFilter = "";
			selKey = "";
			selValue = "";
			inhalt = KVMemoryMap.getInstance().dumpAsList();
			filtered = filter(inhalt, posFilter, negFilter);
		}

		if (model.lookupConversation().getCondition().equals(Condition.KVDB_EDIT)) {
			if (!filtered.isEmpty() && filtered.size() == 1) {
				String[] keyValue = KVMemoryMap.splitFileFormatLineInKeyValue(filtered.get(0));
				if (StringUtils.endsWithIgnoreCase(keyValue[0], selKey)) {
					writeLog("EDIT " + keyValue[0] + " = " + KVMemoryMap.getInstance().readValueFromKey(keyValue[0]) + " -> " + selValue);
					KVMemoryMap.getInstance().writeKeyValue(keyValue[0], selValue, true);
				} else {
					model.lookupConversation().getMeldungen()
							.add("Auswahl zum ändern ist nicht eindeutig. " + keyValue[0] + " / " + selKey);
				}
			} else {
				model.lookupConversation().getMeldungen().add("Keine Auswahl zum ändern gefunden.");
			}
		}

		if (model.lookupConversation().getCondition().equals(Condition.KVDB_DELETE)) {
			if (!filtered.isEmpty()) {
				for (String line : filtered) {
					String[] keyValue = KVMemoryMap.splitFileFormatLineInKeyValue(line);
					writeLog("DLET " + keyValue[0] + " = " + keyValue[1]);
					KVMemoryMap.getInstance().deleteKey(keyValue[0]);
				}
			} else {
				model.lookupConversation().getMeldungen().add("Keine Auswahl zum löschen gefunden.");
			}
		}

		if (model.lookupConversation().getCondition().equals(Condition.KVDB_INSERT)) {
			if (StringUtils.isNotBlank(selKey) && !KVMemoryMap.getInstance().containsKey(selKey)) {
				writeLog("ISRT " + selKey.trim() + " = " + selValue);
				KVMemoryMap.getInstance().writeKeyValue(selKey.trim(), StringUtils.trimToEmpty(selValue), false);
			} else {
				model.lookupConversation().getMeldungen().add("Kein Satz zum einfügen gefunden oder Key nicht vorhanden oder doppelt.");
			}
		}

		if (model.lookupConversation().getCondition().equals(Condition.KVDB_EDIT)
				|| model.lookupConversation().getCondition().equals(Condition.KVDB_INSERT)
				|| model.lookupConversation().getCondition().equals(Condition.KVDB_DELETE)) {
			KVMemoryMap.getInstance().save();
		}

		ButtonBar buttonBar = new ButtonBar();
		buttonBar.getButtons().add(new Button("Filtern / Aktualisieren", Condition.KVDB_EDIT_START));
		buttonBar.getButtons().add(new Button("Reset", Condition.KVDB_RESET));
		sb.append(HTMLUtils.buildMenuNar(model, "KVDB Editor", true, buttonBar, false));

		HTMLTable tableFilter = new HTMLTable();
		tableFilter.addTD("Filter", 2, HTMLTable.TABLE_HEADER);
		tableFilter.addNewRow();
		tableFilter.addTD("Positiv: ", 1, null);
		tableFilter.addTDSource(HTMLUtils.buildTextField(POS_FILTER, posFilter, 30, Condition.KVDB_EDIT_START), 1, null);
		HTMLUtils.setFocus(POS_FILTER, model);
		tableFilter.addNewRow();
		tableFilter.addTD("Negativ: ", 1, null);
		tableFilter.addTDSource(HTMLUtils.buildTextField(NEG_FILTER, negFilter, 30, Condition.KVDB_EDIT_START), 1, null);
		tableFilter.addNewRow();
		sb.append(tableFilter.buildTable(model));

		if (!filtered.isEmpty() && filtered.size() == 1) {
			String[] keyValue = KVMemoryMap.splitFileFormatLineInKeyValue(filtered.get(0));
			HTMLTable tableAuswahl = new HTMLTable();
			tableAuswahl.addTD("Auswahl (einzeln)", 2, HTMLTable.TABLE_HEADER);
			tableAuswahl.addNewRow();
			tableAuswahl.addTD("Key: ", 1, null);
			tableAuswahl.addTDSource(HTMLUtils.buildTextField(SEL_KEY, keyValue[0], 30, null), 1, null);
			tableAuswahl.addNewRow();
			tableAuswahl.addTD("Value: ", 1, null);
			tableAuswahl.addTDSource(HTMLUtils.buildTextField(SEL_VALUE, keyValue[1], 30, null), 1, null);
			tableAuswahl.addNewRow();
			tableAuswahl.addTDSource(new Button("ändern", Condition.KVDB_EDIT, true).printForUseInTable(), 2, HTMLTable.NO_BORDER);
			tableAuswahl.addNewRow();
			tableAuswahl.addTDSource(new Button("löschen", Condition.KVDB_DELETE, true).printForUseInTable(), 2, HTMLTable.NO_BORDER);
			tableAuswahl.addNewRow();
			sb.append(tableAuswahl.buildTable(model));
		}

		if (!filtered.isEmpty() && filtered.size() > 1 && (filtered.size() < inhalt.size())) {
			HTMLTable tableAuswahl = new HTMLTable();
			tableAuswahl.addTD("Auswahl (Liste)", 2, HTMLTable.TABLE_HEADER);
			tableAuswahl.addNewRow();
			tableAuswahl.addTDSource(new Button("alle löschen", Condition.KVDB_DELETE, true).printForUseInTable(), 2, HTMLTable.NO_BORDER);
			tableAuswahl.addNewRow();
			sb.append(tableAuswahl.buildTable(model));
		}

		if (StringUtils.isBlank(posFilter) && StringUtils.isBlank(negFilter) && filtered.size() == inhalt.size()) {
			HTMLTable tableAuswahl = new HTMLTable();
			tableAuswahl.addTD("Einfügen", 2, HTMLTable.TABLE_HEADER);
			tableAuswahl.addNewRow();
			tableAuswahl.addTD("Key: ", 1, null);
			tableAuswahl.addTDSource(HTMLUtils.buildTextField(SEL_KEY, "", 30, null), 1, null);
			tableAuswahl.addNewRow();
			tableAuswahl.addTD("Value: ", 1, null);
			tableAuswahl.addTDSource(HTMLUtils.buildTextField(SEL_VALUE, "", 30, null), 1, null);
			tableAuswahl.addNewRow();
			tableAuswahl.addTDSource(new Button("einfügen", Condition.KVDB_INSERT, true).printForUseInTable(), 2, HTMLTable.NO_BORDER);
			tableAuswahl.addNewRow();
			sb.append(tableAuswahl.buildTable(model));
		}

		inhalt = KVMemoryMap.getInstance().dumpAsList();
		filtered = filter(inhalt, posFilter, negFilter);

		sb.append(HTMLUtils.contentBreak());

		String count = filtered.size() + (filtered.size() == 1 ? " Eintrag" : " Einträge");

		StringBuilder lines = new StringBuilder();
		for (String zeile : filtered) {
			String[] strings = StringUtils.split(zeile, '=');
            String key = strings[0].trim();
            String val = coverValue(strings).trim();
			lines.append(key + " = " + val + "\n");
		}

		buildTextviewTable("KVDB - " + count, sb, model, lines.toString(), model.lookupConversation().isTextViewNumbers(), true, true);

	}

    public String coverValue(String[] strings) {

        String key = strings[0].trim();

        for (String prefix : COVER_VALUE_TOKEN_PREFIX) {
            if (key.startsWith(prefix)) {
                return COVER_DOTS
                    + StringUtils.substringAfter(
                        StringUtils.substringBeforeLast(StringUtils.substringBeforeLast(strings[1], "*"), "*"), "*")
                    + COVER_DOTS;
            }
        }
        
        for (String prefix : COVER_VALUE_COMPLETE_PREFIX) {
            if (key.startsWith(prefix)) {
                return COVER_DOTS;
            }
        }

        for (String suffix : COVER_VALUE_COMPLETE_SUFFIX) {
            if (key.endsWith(suffix)) {
                return COVER_DOTS;
            }
        }

        return strings[1];
    }

	private List<String> filter(List<String> list, String posFilter, String negFilter) {

        Set<String> in = new HashSet<>();
		if (StringUtils.isNotBlank(posFilter)) {
			in.add(posFilter);
		}
        Set<String> ex = new HashSet<>();
		if (StringUtils.isNotBlank(negFilter)) {
			ex.add(negFilter);
		}

        return Filter.filter(list, in, ex, Preset.CONTAINS, IgnoreCase.YES);
	}

	private void writeLog(String s) {

		FilesFile logFile = new FilesFile(KVMemoryMap.getInstance().readValueFromKey("application.kvdblog"));
		try {
            FileUtils.writeStringToFile(logFile, Hilfsklasse.zeitstempelAlsString() + " " + s + "\n",
                StandardCharsets.UTF_8.displayName(), true);
		} catch (IOException e) {
			throw new IllegalStateException("Could not write to KV Log:", e);
		}
	}

}
