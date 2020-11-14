package mfi.files.logic;

import org.apache.commons.lang3.StringUtils;
import mfi.files.io.FilesFile;
import mfi.files.model.Conversation;

public enum AjaxFillIn {

	DIR_SIZE_LIST("...", "%s", "n/a") {
		@Override
		public String readValue(Conversation conversation, String parameter) {
			FilesFile file = conversation.getFsListe().get(Integer.parseInt(parameter));
			return StringUtils.replace(getFillInString(), PLACEHOLDER, DateiZugriff.fileGroesseFormatieren(file));
		}
	},

	DIR_SIZE_EDITING("Größe: ...", "Größe: %s", "Größe konnte nicht ermittelt werden.") {
		@Override
		public String readValue(Conversation conversation, String parameter) {
			FilesFile file = conversation.getEditingFile();
			return StringUtils.replace(getFillInString(), PLACEHOLDER, DateiZugriff.fileGroesseFormatieren(file));
		}
	},

	;

	public abstract String readValue(Conversation conversation, String parameter);

	private AjaxFillIn(String loadString, String fillInString, String failString) {
		this.loadString = loadString;
		this.fillInString = fillInString;
		this.failString = failString;
	}

	private final static String PLACEHOLDER = "%s";

	private String loadString;

	private String fillInString;

	private String failString;

	public String getLoadString() {
		return loadString;
	}

	public String getFillInString() {
		return fillInString;
	}

	public String getFailString() {
		return failString;
	}

}
