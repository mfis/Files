package mfi.files.responsibles;

import java.nio.charset.StandardCharsets;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mfi.files.htmlgen.HTMLTable;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.model.Model;

public abstract class AbstractResponsible {

	public static final Logger logger = LoggerFactory.getLogger(AbstractResponsible.class);

	public void buildTextviewTable(String titel, StringBuilder sb, Model model, String inhalt, boolean showNumbers, boolean volleBreite,
			boolean clearAuswahlListe) {

		HTMLTable table = new HTMLTable("id_ajaxFillInTextFileContent");
		if (titel != null) {
			table.addTDSource(
					HTMLUtils.buildSpan(null, null, titel) + HTMLUtils.buildSpan("status_indicator", null, "")
							+ HTMLUtils.buildSpan("searchspan", null,
									HTMLUtils.buildTextField("searchtext", "", 30, null, "Suchen", false, false)),
					showNumbers ? 2 : 1, HTMLTable.TABLE_HEADER);
			table.addNewRow();
		} else {
			sb.append(HTMLUtils.buildHiddenField("searchtext", ""));
			table.addNewRow();
		}

		String content = StringUtils.trimToEmpty(inhalt);
		if (model.lookupConversation().getEditingFile() != null && model.lookupConversation().getEditingFile().isClientCrypted()) {
			model.lookupConversation().getJavaScriptOnPageLoaded().add("showFileContentToView(true);");
		} else {
			content = new String(Base64.encodeBase64(content.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
			model.lookupConversation().getJavaScriptOnPageLoaded().add("showFileContentToView(false);");
		}
		model.lookupConversation().setFileContent(content);

		table.setWidthTo100Percent(volleBreite);
		sb.append(table.buildTable(model));
	}
}
