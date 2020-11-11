package mfi.files.htmlgen;

import org.apache.commons.lang3.StringUtils;

import mfi.files.model.Condition;

public class OptionLink {

	private String name;

	private Condition condition;

	private String externLink = null;

	private String id = null;

	private String imageClass;

	public OptionLink(String name, Condition condition) {
		this.name = name;
		this.condition = condition;
		this.externLink = null;
		this.id = null;
	}

	public OptionLink(String name, Condition condition, String image) {
		this.name = name;
		this.condition = condition;
		this.externLink = null;
		this.id = null;
		this.imageClass = image;
	}

	public OptionLink(String name, String externLink) {
		this.name = name;
		this.condition = null;
		this.externLink = externLink;
		this.id = null;
	}

	public OptionLink(String name, String externLink, String image) {
		this.name = name;
		this.condition = null;
		this.externLink = externLink;
		this.id = null;
		this.imageClass = image;
	}

	public String print() {
		String link;
		if (condition == null && StringUtils.isEmpty(externLink)) {
			link = "";
		} else {
			if (StringUtils.isEmpty(externLink)) {
				link = " onclick=\"" + HTMLUtils.buildConditionSubmitJS(condition) + "\"";
			} else {
				link = " onclick=\"window.open('" + externLink + "','_blank');\"";
			}
		}
		String idString = "";
		if (id != null) {
			idString = " id=\"" + id + "\"";
		}

		String imgString = "";
		String imgStringClose = "";
		String classString = "";
		if (StringUtils.isNoneBlank(imageClass)) {
			classString = "figureicon pointer " + imageClass;
		}
		// imgString = "<div class=\"figure\"" + link + "><p><div" + idString + " class=\"" + classString + "\"></div></p><p><a>";
		imgString = "<div class=\"figure\"" + link + "><div" + idString + " class=\"" + classString + "\"></div><p><a>";
		imgStringClose = "</a></p></div>";

		String p = " <div class=\"options\">" + imgString + name + imgStringClose + "</div>\n";

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

	public String getExternLink() {
		return externLink;
	}

	public void setExternLink(String externLink) {
		this.externLink = externLink;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getImageClass() {
		return imageClass;
	}

	public void setImageClass(String imageClass) {
		this.imageClass = imageClass;
	}

}
