package mfi.files.htmlgen;

import mfi.files.model.Condition;

public class Icon {

	private String name;

	private Condition condition;

	private String imageClass;

	private String id = null;

	private Integer index;

	public Icon(String name, Condition condition, String image, String id, Integer index) {
		this.name = name;
		this.condition = condition;
		this.imageClass = image;
		this.id = id;
		this.index = index;
	}

	public String print() {

		String link;

		if (index != null) {
			link = "onclick=\"" + HTMLUtils.buildConditionSubmitJS(condition, index) + "\"";
		} else {
			link = "onclick=\"" + HTMLUtils.buildConditionSubmitJS(condition) + "\"";
		}

		String idString = "";
		if (id != null) {
			idString = " id=\"" + id + "\"";
		}

		String icon = "<div" + idString + " class=\"icon " + imageClass + "\"" + link + "></div>";

		return icon;
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

	public String getImageClass() {
		return imageClass;
	}

	public void setImageClass(String imageClass) {
		this.imageClass = imageClass;
	}

	public String getId() {
		return id;
	}

	public Integer getIndex() {
		return index;
	}

	public void setIndex(Integer index) {
		this.index = index;
	}

}
