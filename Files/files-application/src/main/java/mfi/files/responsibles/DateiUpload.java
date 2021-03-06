package mfi.files.responsibles;

import java.util.Map;

import org.springframework.stereotype.Component;

import mfi.files.annotation.Responsible;
import mfi.files.htmlgen.HTMLTable;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Condition;
import mfi.files.model.Model;

@Component
public class DateiUpload extends AbstractResponsible {

	@Responsible(conditions = { Condition.FILE_UPLOAD })
	public void fjUpload(StringBuilder sb, Map<String, String> parameters, Model model) {

		model.lookupConversation().getJavaScriptFilesToEmbed().add("fileupload.js");
		sb.append(HTMLUtils.buildMenuNar(model, "Dateien hochladen", true, null, false));

		HTMLTable table = new HTMLTable();
		String pwNamePrefix = "__clientpassword____hashedpassword__";
		table.addTD("Neues Passwort", 1, HTMLTable.TABLE_HEADER);
		table.addNewRow();
		table.addTD("Zum Verschlüsseln der Dateien bitte ein Passwort eingeben. Ohne Passwort erfolgt keine Verschlüsselung.", 1,
				" align='right'");
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildPasswordField(pwNamePrefix + "upload_pass_one", "", 30, null, false), 1, " align='center'");
		HTMLUtils.setFocus(pwNamePrefix + "upload_pass_one", model);
		table.addNewRow();
		table.addTD("Bestätigung: ", 1, " align='right'");
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildPasswordField(pwNamePrefix + "upload_pass_two", "", 30, null, false), 1, " align='center'");
		table.addNewRow();
		sb.append(table.buildTable(model));
		table = new HTMLTable();

		sb.append(HTMLUtils.buildHiddenField("uploadcaption", ""));
		table.addTD(HTMLUtils.spacifyFilePath("Zielverzeichnis: " + model.lookupConversation().getEditingFile().dateiNameUndPfadKlartext(),
				model), 1, HTMLTable.TABLE_HEADER);
		table.addNewRow();
		table.addTDSource(HTMLUtils.buildUploadFormular(model), 1, null);
		table.addNewRow();
		sb.append(table.buildTable(model));

		String s1 = KVMemoryMap.getInstance().readValueFromKey("application.properties.editableFiles");
		String s2 = KVMemoryMap.getInstance().readValueFromKey("application.properties.viewableImages");
		String s3 = KVMemoryMap.getInstance().readValueFromKey("application.properties.browserCompatibleFiles");
		sb.append(HTMLUtils.buildHiddenField("clientCryptoFileTypes", s1 + "," + s2 + "," + s3));

	}
}
