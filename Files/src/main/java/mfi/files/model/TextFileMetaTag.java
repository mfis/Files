package mfi.files.model;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import mfi.files.helper.Hilfsklasse;

public class TextFileMetaTag {

	private TextFileMetaTagName textFileMetaTagName;

	private List<String> arguments;

	private String caption;

	private String id;

	public TextFileMetaTag(TextFileMetaTagName textFileMetaTagName) {
		this.textFileMetaTagName = textFileMetaTagName;
		arguments = new LinkedList<String>();
	}

	public TextFileMetaTag(String textFileMetaTagName) {
		this.textFileMetaTagName = null;
		for (TextFileMetaTagName t : TextFileMetaTagName.values()) {
			String name = StringUtils.remove(t.name(), '_');
			String parm = StringUtils.remove(textFileMetaTagName, '_');
			if (StringUtils.equalsIgnoreCase(parm, name)) {
				this.textFileMetaTagName = t;
				break;
			}
		}
		arguments = new LinkedList<String>();
	}

	public boolean isUnknownTag() {
		return textFileMetaTagName == null;
	}

	public boolean argumentsAreNumbers() {

		if (textFileMetaTagName != TextFileMetaTagName.CHOICE) {
			return false;
		}

		for (String arg : getArguments()) {
			if (!Hilfsklasse.isNumeric(arg)) {
				return false;
			}
		}

		return true;
	}

	public List<String> getArguments() {
		return arguments;
	}

	public void setArguments(List<String> arguments) {
		this.arguments = arguments;
	}

	public TextFileMetaTagName getTextFileMetaTagName() {
		return textFileMetaTagName;
	}

	public String getCaption() {
		return caption;
	}

	public void setCaption(String caption) {
		this.caption = caption;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

}
