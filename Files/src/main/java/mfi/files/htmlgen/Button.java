package mfi.files.htmlgen;

import mfi.files.helper.StringHelper;
import mfi.files.model.Condition;

public class Button {

	private String name;

	private Condition condition;

	private boolean confirm = false;

	private String externLink = null;

	private boolean newTab = false;

	public Button(String name, Condition condition) {
		this.name = name;
		this.condition = condition;
		this.confirm = false;
		this.externLink = null;
	}

	public Button(String name, Condition condition, boolean confirm) {
		this.name = name;
		this.condition = condition;
		this.confirm = confirm;
		this.externLink = null;
	}

	public Button(String name, String externLink, boolean newTab) {
		this.name = name;
		this.condition = null;
		this.confirm = false;
		this.externLink = externLink;
		this.newTab = newTab;
	}

	public String print() {
		return printInternal() + "\n";
	}

	public String printForUseInTable() {
		return printInternal();
	}

	private String printInternal() {
		String link;
		if (confirm) {
			link = HTMLUtils.buildConfirmConditionSubmitLink(StringHelper.idFromName(name), name, condition, -1);
		} else {
			if (externLink == null) {
				link = HTMLUtils.buildConditionSubmitLink(name, condition);
			} else {
				String tab = "";
				if (newTab) {
					tab = " target='_blank'";
				}
				link = "<a id=\"" + StringHelper.idFromName(name) + "\" href=\"" + externLink + "\"" + tab + ">" + name + "</a>";
			}
		}
		String p = " <div class=\"button\">" + link + "</div>";
		return p;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Condition getCondition() {
		return condition;
	}

	public void setCondition(Condition condition) {
		this.condition = condition;
	}

	public boolean isConfirm() {
		return confirm;
	}

	public void setConfirm(boolean confirm) {
		this.confirm = confirm;
	}

	public String getExternLink() {
		return externLink;
	}

	public void setExternLink(String externLink) {
		this.externLink = externLink;
	}

}
