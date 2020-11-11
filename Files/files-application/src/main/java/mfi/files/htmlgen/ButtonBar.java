package mfi.files.htmlgen;

import java.util.LinkedList;
import java.util.List;

public class ButtonBar {

	private List<Button> buttons;

	public ButtonBar() {
		super();
		buttons = new LinkedList<Button>();
	}

	public String print() {
		if (buttons.isEmpty()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<div class=\"siteheaderB\">\n<div class=\"buttonbar\">\n");
		for (Button button : buttons) {
			sb.append(button.print());
		}
		sb.append("</div>\n</div>\n");
		return sb.toString();
	}

	public List<Button> getButtons() {
		return buttons;
	}

	public void setButtons(List<Button> buttons) {
		this.buttons = buttons;
	}

}
