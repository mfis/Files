package mfi.files.htmlgen;

import java.util.LinkedList;
import java.util.List;

public class TitleBar {

	private List<OptionLink> options;

	public TitleBar() {
		super();
		options = new LinkedList<OptionLink>();
	}

	public String print() {
		StringBuilder sb = new StringBuilder();
		sb.append("<div class=\"titlebar\">\n");
		for (OptionLink option : options) {
			sb.append(option.print());
		}
		sb.append("</div>\n");
		return sb.toString();
	}

	public List<OptionLink> getOptions() {
		return options;
	}

	public void setOptions(List<OptionLink> options) {
		this.options = options;
	}

}
