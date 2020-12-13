package mfi.files.htmlgen;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.StringUtils;
import mfi.files.helper.ServletHelper;
import mfi.files.helper.StringHelper;
import mfi.files.io.FilesFile;
import mfi.files.logic.AjaxFillIn;
import mfi.files.logic.Security;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Condition;
import mfi.files.model.Model;
import mfi.files.servlet.FilesDownloadServlet;

public class HTMLUtils {

	private static final String CLASS_EQ = "class='";
	private static final String END_A = "</a>";
	public static final String END_DIV = "</div>";
	public static final String CONDITION = "condition";
	public static final String TABLEINDEX = "tableIndex";
	public static final String RUNNING_AS_WEBAPP = "runAsWebApp";
	public static final String CONVERSATION = "convID";
	public static final String NOTVISIBLE = "notVisible";
	public static final String PACKAGE_TD_BEGIN = "<td class='" + NOTVISIBLE + "'>";
	public static final String PACKAGE_TABLE_BEGIN = "<table class='" + NOTVISIBLE + "'><tr>" + PACKAGE_TD_BEGIN;
	public static final String PACKAGE_TABLE_END = "</td></tr></table>";
	public static final String TEXTMARKER_1_START = "((1$$##**";
	public static final String TEXTMARKER_1_END = "**##$$1))";
	public static final String TEXTMARKER_2_START = "((2$$##**";
	public static final String TEXTMARKER_2_END = "**##$$2))";
	public static final String SECURE_ATTRIBUTE = "__secure__";

	public static final int UNDEF_NUMERIC = -1;

	private static final String CHAR_QUOTE = "'";
	private static final String CHAR_EMPTY = "";
	private static final String CHAR_SPACE = " ";
	private static final String CHAR_EQUAL = "=";

	public static String buildAttribute(String key, String value) {

		if (StringUtils.isNotEmpty(value)) {
			return CHAR_SPACE + key + CHAR_EQUAL + CHAR_QUOTE + value + CHAR_QUOTE;
		} else {
			return CHAR_EMPTY;
		}
	}

	public static String newLine() {
		return "\n";
	}

	public static String buildAttribute(String key, int value) {

		return buildAttribute(key, String.valueOf(value));
	}

	public static String addClassToAttributes(String attribute, String cssClass) {

		if (attribute == null) {
			return buildAttribute("class", cssClass);
		}

		if (attribute.indexOf("\"") != -1) {
			attribute = StringUtils.replace(attribute, "\"", "'");
		}
		if (attribute.indexOf(CLASS_EQ) != -1) {
			attribute = StringUtils.replace(attribute, CLASS_EQ, CLASS_EQ + cssClass + " ");
		} else {
			attribute = attribute + buildAttribute("class", cssClass);
		}
		return attribute;
	}

	public String buildValueNotEmpty(String value) {

		if (StringUtils.isNotEmpty(value)) {
			return value;
		} else {
			return "&nbsp;";
		}
	}

	public static String buildAndFormatText(String string, String cssClass) {
		string = StringUtils.replace(string, "\r\n", "<br/>");
		string = StringUtils.replace(string, "\n", "<br/>") + "<br/><br/>";
		return "<a class='" + cssClass + "'><pre>" + string + "</pre></a>";
	}

	public static String buildMenuNar(Model model, String name, Condition alternateBackButton, ButtonBar buttonBar,
			boolean contentIsManually100PercentWidth) {

		return buildMenuNarInternal(model, name, alternateBackButton, buttonBar, contentIsManually100PercentWidth);
	}

	public static String buildMenuNar(Model model, String name, boolean insertBackButton, ButtonBar buttonBar,
			boolean contentIsManually100PercentWidth) {

		return buildMenuNarInternal(model, name, (insertBackButton ? Condition.FS_CANCEL_EDITED_FILE : null), buttonBar,
				contentIsManually100PercentWidth);
	}

	private static String buildMenuNarInternal(Model model, String name, Condition backButtonCondition, ButtonBar buttonBar,
			boolean contentIsManually100PercentWidth) {

		Condition menuCondition = Condition.SYS_FJ_OPTIONS;
		if (buttonBar == null) {
			buttonBar = new ButtonBar();
		}
		if (backButtonCondition != null) {
			buttonBar.getButtons().add(0, new Button(SprachKonstanten.ZURUECK, backButtonCondition));
			if (!model.isClientTouchDevice()) {
				model.lookupConversation().getKeyPressEvents().put(KeyCodes.ESCAPE.methodDeclarator(),
						buildConditionSubmitJS(backButtonCondition)); // ESC
			}
		}

		StringBuilder sb = new StringBuilder();
		sb.append("<div class=\"siteheaderA\">\n");
		sb.append("<div class=\"title\"><h1>" + name + "</h1></div>\n");

		TitleBar titleBar = new TitleBar();

		OptionLink refresh = new OptionLink("", menuCondition, "");
		refresh.setId("refresh");
		titleBar.getOptions().add(refresh);
		model.lookupConversation().getJavaScriptOnPageLoaded().add("document.getElementById('refresh').className = '';");
		if (StringUtils.isNotBlank(model.getUser())) {
			titleBar.getOptions().add(new OptionLink("Menü", menuCondition, "menu"));
			if (!model.isPhone()) {
				titleBar.getOptions()
						.add(new OptionLink("Neuer Tab", "/?" + CONVERSATION + "=" + model.lookupNextConversationID(), "addnew"));
				titleBar.getOptions().add(new OptionLink(model.getUser(), "", "user"));
				titleBar.getOptions().add(new OptionLink("Abmelden", Condition.LOGOFF, "exit"));
				if (!model.isClientTouchDevice()) {
					model.lookupConversation().getKeyPressEvents().put(KeyCodes.F12.methodDeclarator(),
							buildConditionSubmitJS(Condition.LOGOFF)); // CTRL+X
				}
			}
		} else {
			titleBar.getOptions().add(new OptionLink("Account anlegen", Condition.LOGIN_GENERATE_CREDENTIALS_FORM, "user"));
			titleBar.getOptions().add(new OptionLink("Passwort ändern", Condition.LOGIN_CHANGE_PASS_FORM, "user"));
		}
		sb.append(titleBar.print());
		sb.append("</div>\n");
		sb.append(buttonBar.print());

		return sb.toString();
	}

	public static String contentBreak() {
		return "</div><div class=\"content\">\n";
	}

	public static String contentBreakMitTrennlinie() {
		return "</div><div class=\"trenner\"></div><div class=\"content\">\n";
	}

	public static String buildTextArea(String name, String value) {
		String id = StringHelper.idFromName(name) + SECURE_ATTRIBUTE;
		return "<textarea" + buildAttribute("id", id) + buildAttribute("name", name + SECURE_ATTRIBUTE) + ">" + value + "</textarea>";
	}

	public static String buildIFrame(String id, String source) {
		return "<iframe" + buildAttribute("id", id) + buildAttribute("src", source) + "></iframe>";
	}

	public static String buildTextField(String name, String value, int size, Condition conditionOnEnter) {
		return buildTextField(name, value, size, conditionOnEnter, null, true, false);
	}

	public static String buildTextField(String name, String value, int size, Condition conditionOnEnter, String placeholder,
			boolean autocomplete, boolean numericKeyboard) {
		String id = StringHelper.idFromName(name);
		String js = "";
		if (conditionOnEnter != null) {
			js = " onkeydown=\"if (event.keyCode == 13) { condSubmit('" + conditionOnEnter + "','-1','"
					+ ServletHelper.lookupAjaxSubmitConfig() + "'); return false; }\"";
		}
		String type = numericKeyboard ? "number" : "text";
		return "<div class='rightdiv'><div class='boxWrapper'><input" + buildAttribute("type", type) + buildAttribute("id", id)
				+ buildAttribute("name", name) + buildAttribute("size", size) + buildAttribute("value", value)
				+ buildAttribute("placeholder", placeholder) + buildAttribute("autocomplete", autocomplete == false ? "off" : null) + js
				+ "></input></div></div>";
	}

	public static String buildNumberField(String name, String value, int size, Condition conditionOnEnter) {
		return buildTextField(name, value, size, conditionOnEnter, null, true, true);
	}

	public static String buildCheckBox(String label, String name, boolean checked, Condition condition) {

		if (name == null) {
			name = label;
		}

		String id = StringHelper.idFromName(name);

		String checkedString = "";
		if (checked) {
			checkedString = "checked=\"checked\" ";
		}

		String onclick = "";
		if (condition != null) {
			onclick = " onclick=\"" + buildConditionSubmitJS(condition) + "\"";
		}

		return "<div class=\"rightdiv\"><table class=\"notVisible\"><tr><td class=\"noborderNoxpadding\"><a>" + label
				+ "</a></td><td class=\"noborderNoxpadding\"><div class=\"check\">" + "<input type=\"checkbox\"" + onclick + " value=\"1\" "
				+ checkedString + "name=\"" + name + "\" id=\"" + id + "\"/>" + "<label for=\"" + id
				+ "\"></label></div></td></tr></table></div>";

	}

	public static String buildRadioButton(String group, String label, String id, boolean checked) {

		String checkedString = "";
		if (checked) {
			checkedString = "checked=\"checked\" ";
		}

		return "<div class=\"rightdiv\"><table class=\"notVisible\"><tr><td class=\"noborderNoxpadding\"><a>" + label
				+ "</a></td><td class=\"noborderNoxpadding\"><div class=\"radiobtn\">" + "<input type=\"radio\" value=\"" + id + "\" "
				+ checkedString + "name=\"" + group + "\" id=\"" + id + "\"/>" + "<label for=\"" + id
				+ "\"></label></div></td></tr></table></div>";

	}

	public static void setFocus(String name, Model model) {
		String id = StringHelper.idFromName(name);
		model.lookupConversation().getJavaScriptOnPageLoaded().add("focusTextField(\"" + id + "\");");
	}

	public static String buildHiddenField(String name, String value) {
		return ("<input type='hidden' name = '" + name + "' id='" + name + "' value='" + value + "'/>\n");
	}

	public static String buildPasswordField(String name, String value, int size, Condition conditionOnEnter, boolean autocomplete) {
		String id = StringHelper.idFromName(name);
		String js = "";
		if (conditionOnEnter != null) {
			js = " onkeydown=\"if (event.keyCode == 13) { condSubmit('" + conditionOnEnter + "','-1','"
					+ ServletHelper.lookupAjaxSubmitConfig() + "'); return false; }\"";
		} else {
			js = " onkeydown=\"if (event.keyCode == 13) { return false; }\"";
		}
		return "<div class='rightdiv'><div class='boxWrapper'><input" + buildAttribute("id", id) + buildAttribute("name", name)
				+ buildAttribute("size", size) + buildAttribute("type", "password") + buildAttribute("value", value)
				+ (autocomplete ? "" : buildAttribute("autocomplete", "off")) + js + "/></div></div>";
	}

	public static String buildConditionSubmitLink(String name, Condition condition) {
		return buildConditionSubmitLink(name, condition, UNDEF_NUMERIC);
	}

	public static String buildConditionSubmitLink(String name, Condition condition, int tableIndex) {
		return buildConditionSubmitLink(name, condition, tableIndex, "");
	}

	public static String buildConditionSubmitLinkWithToolTip(String name, Condition condition, String tooltip, int tableIndex,
			String cssClassString) {

		if (condition != null && StringUtils.isNotBlank(condition.toString())) {
			String tooltipString = "";
			if (StringUtils.isNotEmpty(tooltip)) {
				tooltipString = " title=\"" + tooltip + "\"";
			}
			return "<a" + tooltipString + cssClassString + " href=\"javascript:" + buildConditionSubmitJS(condition, tableIndex) + "\">"
					+ name + END_A;
		} else {
			return "<a" + cssClassString + ">" + name + END_A;
		}
	}

	public static String buildConditionSubmitLink(String name, Condition condition, int tableIndex, String cssClassString) {

		return buildConditionSubmitLinkWithToolTip(name, condition, null, tableIndex, cssClassString);
	}

	public static String buildConfirmConditionSubmitLink(String id, String name, Condition condition, int tableIndex) {

		String s = "<a id='" + id + "' href=\"javascript:confirm('" + id + "','" + condition + "'," + "'" + tableIndex + "','"
				+ ServletHelper.lookupAjaxSubmitConfig() + "')\">" + name + END_A;
		return s;
	}

	public static String buildAjaxFillInText(AjaxFillIn function, String parameter, boolean lowPriority, Model model,
			String additionalCssClass) {

		String cssClass = StringUtils.isNoneBlank(additionalCssClass) ? " " + additionalCssClass : "";

		String s = "<a class=\"ajaxFillIn" + cssClass + "\" id=\"" + function.name() + "-" + parameter + "\">" + function.getLoadString()
				+ END_A;
		model.lookupConversation().setAjaxFillIn(true);
		return s;
	}

	public static String buildDiv(String id, String cssClass, String content, String onclick) {
		String div = "<div";
		if (StringUtils.isNotEmpty(cssClass)) {
			div = div + " class=\"" + cssClass + "\"";
		}
		if (StringUtils.isNotEmpty(id)) {
			div = div + " id=\"" + id + "\"";
		}
		if (StringUtils.isNotEmpty(onclick)) {
			div = div + " onclick=\"" + onclick + "\"";
		}
		div = div + ">" + content + "</div>";
		return div;
	}

	public static String buildA(String id, String cssClass, String content) {
		String a = "<a";
		if (StringUtils.isNotEmpty(cssClass)) {
			a = a + " class=\"" + cssClass + "\"";
		}
		if (StringUtils.isNotEmpty(id)) {
			a = a + " id=\"" + id + "\"";
		}
		a = a + ">" + content + "</a>";
		return a;
	}

	public static String buildP(String id, String cssClass, String content) {
		String p = "<p";
		if (StringUtils.isNotEmpty(cssClass)) {
			p = p + " class=\"" + cssClass + "\"";
		}
		if (StringUtils.isNotEmpty(id)) {
			p = p + " id=\"" + id + "\"";
		}
		p = p + ">" + content + "</p>";
		return p;
	}

	public static String buildSpan(String id, String cssClass, String content) {
		return buildSpan(id, cssClass, content, null);
	}

	public static String buildSpan(String id, String cssClass, String content, String onclick) {
		String span = "<span";
		if (StringUtils.isNotEmpty(cssClass)) {
			span = span + " class=\"" + cssClass + "\"";
		}
		if (StringUtils.isNotEmpty(id)) {
			span = span + " id=\"" + id + "\"";
		}
		if (StringUtils.isNotEmpty(onclick)) {
			span = span + " onclick=\"" + onclick + "\"";
		}
		span = span + ">" + content + "</span>";
		return span;
	}

	public static String buildDropDownListeSelectContains(String name, Collection<String> elemente, String contains) {

		String sel = null;
		for (String element : elemente) {
			if (StringUtils.containsIgnoreCase(element, contains)) {
				sel = element;
				break;
			}
		}
		return buildDropDownListe(name, elemente, sel);
	}

	public static String buildDropDownListe(String name, Collection<String> elemente, String selectedValue) {

		StringBuilder sb = new StringBuilder(elemente.size() * 60);
		sb.append("<div class='rightdiv'><div class='boxWrapper'><label class='selectLabel'><select name='" + name + "'>");
		for (String element : elemente) {
			String selected = selectedValue != null && StringUtils.equalsIgnoreCase(element, selectedValue) ? " selected='selected'" : "";
			sb.append("<option" + selected + ">" + element + " &nbsp; &nbsp; &nbsp; </option>");
		}
		sb.append("</select></label></div></div>");
		return sb.toString();
	}

	public static String buildImage(FilesFile srcFile, boolean fullscreen, Condition conditionOnError, Model model) {

		String className = "";
		String img = "";
		if (fullscreen) {
			className = "imgFull";
			img = "<div class=\"imgWrapper\" id=\"imgWrapperBackground\">";
		} else {
			className = "imgSmall";
		}
		String url = buildDownloadURLForOpeningInBrowser(model, srcFile);
		String id = StringHelper.idFromName(FilesFile.dateinameOhneSuffix(srcFile));
		if (srcFile.isClientCrypted()) {
			img = img + "\n<img class=\"" + className + "\" alt=\"" + id + "\" title=\"" + id + "\" id=\"" + id + "\" />\n";
			String mimeType = StringUtils.trimToEmpty(
					KVMemoryMap.getInstance().readValueFromKey("application.properties.mimetypes." + srcFile.dateinamenSuffix()));
			model.lookupConversation().getJavaScriptOnPageLoaded().add(
					"downloadClientCryptedFile('" + id + "', '" + url + "', '', '" + mimeType + "', '" + conditionOnError.name() + "' );");
		} else {
			img = img + "\n<img class=\"" + className + "\" src=\"" + url + "\" alt=\"" + id + "\" title=\"" + id + "\" />\n";
		}
		if (fullscreen) {
			img = img + "</div>";
		}

		return img;
	}

	private static String buildDownloadURInternal(Model model, FilesFile file, long duration, boolean forceDownload) {

		long expire = System.currentTimeMillis() + duration;

		String token = UUID.randomUUID().toString();
		token = token + file.dateiNameUndPfadEscaped();
        token = Security.cleanUpKvSubKey(token);
		token = new String(new Base32(0).encode(token.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
		token = StringUtils.remove(token, "=");

		KVMemoryMap.getInstance().readListWithPartKey(FilesDownloadServlet.DOWNLOADTOKEN);
		KVMemoryMap.getInstance().writeKeyValue(FilesDownloadServlet.DOWNLOADTOKEN + token + "." + expire, file.getAbsolutePath(), false);

		String url = FilesDownloadServlet.SERVLETPFAD + "?token=" + token + "&" + CONVERSATION + "="
				+ model.lookupConversation().getConversationID() + "&" + FilesDownloadServlet.FORCE_DOWNLOAD + "=" + forceDownload;

		if (file.isServerCryptedDirectPassword() || file.isServerCryptedHashedPassword()) {
			url = url + "&" + CONVERSATION + "=" + model.lookupConversation().getConversationID();
		}

		return url;
	}

	public static String buildDownloadURLForOpeningInBrowser(Model model, FilesFile file) {
		// 10 Minutes
		return buildDownloadURInternal(model, file, 1000 * 60 * 10, false);
	}

	public static String buildDownloadURLForDirectAttachmentDownload(Model model, FilesFile file) {
		// 10 Minutes, force download
		return buildDownloadURInternal(model, file, 1000 * 60 * 10, true);
	}

	public static String buildUploadFormular(Model model) {

		StringBuilder sb = new StringBuilder(2000);
		sb.append(
				"<div><input name=\"file\" type=\"file\" id=\"fileA\" style=\"display:none\" multiple=\"multiple\" onchange=\"fileChange();\"/></div>\n");
		sb.append(
				"<div class=\"button\" id=\"selectFiles\"><a href=\"javascript:document.getElementById('fileA').click();\">Dateien auswählen</a></div>\n");
		sb.append(
				"<div class=\"button\" id=\"uploadStarten\" style=\"display:none\"><a href=\"javascript:uploadFile();\">Upload starten</a></div>\n");
		sb.append(
				"<div class=\"button\" id=\"uploadAbbrechen\" style=\"display:none\"><a href=\"javascript:uploadAbort();\">Upload abbrechen</a></div>\n");
		sb.append(contentBreak());
		sb.append("<div><ul id=\"fileList\"></ul></div>\n");
		sb.append(contentBreak());
		sb.append("<br/><div id=\"progress\"></div>\n");
		sb.append(contentBreak());
		sb.append("<div><a id=\"prozent\"></a></div>\n");
		sb.append(contentBreak());
		sb.append("<div><a id=\"state\"></a></div>\n");

		return sb.toString();
	}

	public static String buildImageNavigation(Condition prev, Condition exit, Condition next, Model model) {

		if (!model.isClientTouchDevice()) {
			model.lookupConversation().getKeyPressEvents().put(KeyCodes.LEFT.methodDeclarator(), HTMLUtils.buildConditionSubmitJS(prev));
			model.lookupConversation().getKeyPressEvents().put(KeyCodes.ESCAPE.methodDeclarator(), HTMLUtils.buildConditionSubmitJS(exit));
			model.lookupConversation().getKeyPressEvents().put(KeyCodes.RIGHT.methodDeclarator(), HTMLUtils.buildConditionSubmitJS(next));
		}

		String nav = "";
		nav = nav + "<div class=\"imgWrapper\">";
		nav = nav + "<div class=\"imgNav\" id=\"imgNavLeft\" onclick=\"" + buildConditionSubmitJS(prev) + "\"></div>";
		nav = nav + "<div class=\"imgNav\" id=\"imgNavCenter\" onclick=\"" + buildConditionSubmitJS(exit) + "\"></div>";
		nav = nav + "<div class=\"imgNav\" id=\"imgNavRight\" onclick=\"" + buildConditionSubmitJS(next) + "\"></div>";
		nav = nav + "</div>";
		return nav;
	}

	public static String buildConditionSubmitJS(Condition condition) {
		return buildConditionSubmitJS(condition, UNDEF_NUMERIC);
	}

	public static String buildConditionSubmitJS(Condition condition, int tableIndex) {
		if (condition == null) {
			return "return false;";
		} else {
			return "condSubmit('" + condition + "'," + "'" + tableIndex + "','" + ServletHelper.lookupAjaxSubmitConfig() + "');";
		}
	}

	public static String spacifyFilePath(String text, Model model) {

		String newText = StringUtils.replace(text, "/", " / ").trim();
		return newText;
	}

	public static String buildElement(String content) {
		return "<div class=\"element\">\n" + content + "\n</div>";
	}

	public static void buildHtmlHeader(StringBuilder out, Model model, String servletpfad, String stringEncoding,
			Map<String, String> parameters) {

		boolean isAjaxRequest = ServletHelper.lookupIsCurrentRequestTypeAjax(parameters);

		if (!isAjaxRequest) {
			out.append(
					"<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n ");
			out.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xml:lang=\"de\" lang=\"de\">\n");
			out.append("<head>\n");

			out.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"></meta>\n");
			out.append("<meta http-equiv=\"expires\" content=\"0\"></meta>\n");
			out.append("<meta name=\"format-detection\" content=\"telephone=no\"></meta>\n");

			if (model.isClientTouchDevice()) {
				out.append("<meta name=\"mobile-web-app-capable\" content=\"yes\"></meta>\n"); // Android
				out.append("<meta name=\"apple-mobile-web-app-capable\" content=\"yes\"></meta>\n"); // iOS
				out.append("<meta name=\"apple-mobile-web-app-status-bar-style content=\"black\"></meta>\n");
				out.append(
						"<meta name = \"viewport\" id = \"viewport\" content = \"width = device-width, height = device-height, minimum-scale = 1.0 maximum-scale = 1.0\"></meta>\n");
			}

			out.append("<link rel='stylesheet' type='text/css' href='" + "style.css" + "'></link>\n");
			out.append("<link rel='icon' href='" + "favicon.ico" + "' type='image/x-icon'></link>\n");
			out.append("<link rel='shortcut icon' href='" + "favicon.ico" + "'></link>\n");

			// TouchIcons
			if (model.isTouchIconFaehig()) {
				if (model.isPhone()) {
					// iOS6 Retina
					out.append("<link rel=\"apple-touch-icon-precomposed\" sizes=\"114x114\" href=\""
							+ "apple-touch-icon-114x114-precomposed.png" + "\" ></link>\n");
					// iOS7+ Retina
					out.append("<link rel=\"apple-touch-icon-precomposed\" sizes=\"120x120\" href=\""
							+ "apple-touch-icon-120x120-precomposed.png" + "\"></link>\n");
				} else if (model.isTablet()) {
					// iOS6 Non-Retina
					out.append("<link rel=\"apple-touch-icon-precomposed\" sizes=\"72x72\" href=\""
							+ "apple-touch-icon-72x72-precomposed.png" + "\"></link>\n");
					// iOS7+ Non-Retina
					out.append("<link rel=\"apple-touch-icon-precomposed\" sizes=\"76x76\" href=\""
							+ "apple-touch-icon-76x76-precomposed.png" + "\"></link>\n");
					// iOS6 Retina
					out.append("<link rel=\"apple-touch-icon-precomposed\" sizes=\"144x144\" href=\""
							+ "apple-touch-icon-144x144-precomposed.png" + "\"></link>\n");
					// iOS7+ Retina
					out.append("<link rel=\"apple-touch-icon-precomposed\" sizes=\"152x152\" href=\""
							+ "apple-touch-icon-152x152-precomposed.png" + "\"></link>\n");
				}

			}

			out.append("<script type='text/javascript' src='" + "crypto-js-aes-v3.1.6.js" + "'></script>\n");
			out.append("<script type='text/javascript' src='" + "cryptography-v1.js" + "'></script>\n");
			out.append("<script type='text/javascript' src='" + "script.js" + "'></script>\n");
			out.append("<title>Files</title>\n");
			out.append("</head>\n");
			out.append("<body>");
		}

		out.append("<form id='id_mainform' action='" + servletpfad + "' method='post' accept-charset='" + stringEncoding + "'>\n\n");

		out.append("<div class=\"hiddenfields\">\n");
		out.append("<input type='hidden' name = '" + CONVERSATION + "' id='" + CONVERSATION + "' value='"
				+ model.lookupConversation().getConversationID().toString() + "'></input>\n");
		out.append("<input type='hidden' name = '" + CONDITION + "' id='" + CONDITION + "' value=''></input>\n");
		out.append("<input type='hidden' name = '" + TABLEINDEX + "' id='" + TABLEINDEX + "' value=''></input>\n");
		out.append("<input type='hidden' name = '" + RUNNING_AS_WEBAPP + "' id='" + RUNNING_AS_WEBAPP + "' value=''></input>\n");
		out.append("<input type='hidden' name = '" + ServletHelper.REQUEST_TYPE_PARAMETER + "' id='" + ServletHelper.REQUEST_TYPE_PARAMETER
				+ "' value=''></input>\n");
		out.append("</div>\n");
	}

	public static void buildHtmlFooter(StringBuilder out, Model model, Map<String, String> parameters) {

		boolean isAjaxRequest = ServletHelper.lookupIsCurrentRequestTypeAjax(parameters);

		if (model.isClientTouchDevice() && (model.lookupConversation().getCondition() == Condition.NULL
				|| model.lookupConversation().getCondition() == Condition.LOGIN_FORMULAR || !model.isUserAuthenticated())) {
			out.append("<script type='text/javascript'>putStandaloneMarkerToElement();</script>\n");
		}

		out.append("<div class=\"hiddenfields\" id=\"idhiddenfields\">\n");

		if (model.lookupConversation().getEditingFile() != null && model.lookupConversation().getEditingFile().isFile()) {
			model.lookupConversation().getJavaScriptOnPageLoaded()
					.add("document.title = 'Files - " + model.lookupConversation().getEditingFile().dateiNameKlartext() + "';");
		} else {
			model.lookupConversation().getJavaScriptOnPageLoaded().add("document.title = 'Files';");
		}

		if (model.lookupConversation().isAjaxFillIn()) {
			String script = "fillIn();";
			model.lookupConversation().getJavaScriptOnPageLoaded().add(script);
			model.lookupConversation().setAjaxFillIn(false);
		}

		if (model.lookupConversation().getFileContent() != null) {
			out.append("<input type='hidden' id='fileContent' value='" + model.lookupConversation().getFileContent() + "'></input>\n");
			model.lookupConversation().setFileContent(null);
		}

		if (model.lookupConversation().getJavaScriptFilesToEmbed() != null
				&& !model.lookupConversation().getJavaScriptFilesToEmbed().isEmpty()) {
			for (String line : model.lookupConversation().getJavaScriptFilesToEmbed()) {
				if (isAjaxRequest) {
					String script = "loadJS('" + line + "');";
					model.lookupConversation().getJavaScriptOnPageLoaded().add(script);
				} else {
					out.append("<script type='text/javascript' src='" + line + "'></script>\n");
				}
			}
			model.lookupConversation().getJavaScriptFilesToEmbed().clear();
		}

		if (model.lookupConversation().getJavaScriptOnPageLoaded() != null
				&& !model.lookupConversation().getJavaScriptOnPageLoaded().isEmpty()) {
			for (String line : model.lookupConversation().getJavaScriptOnPageLoaded()) {
				if (StringUtils.contains(line, "'")) {
					line = StringUtils.replace(line, "'", "\"");
				}
				out.append("<input type='hidden' name = 'scriptnames' value='" + line + "'></input>\n");
			}
			model.lookupConversation().getJavaScriptOnPageLoaded().clear();
		}

		if (model.lookupConversation().getKeyPressEvents() != null && !model.lookupConversation().getKeyPressEvents().isEmpty()
				&& !model.isClientTouchDevice()) {
			for (String key : model.lookupConversation().getKeyPressEvents().keySet()) {
				String method = model.lookupConversation().getKeyPressEvents().get(key);
				if (StringUtils.contains(method, "'")) {
					method = StringUtils.replace(method, "'", "\"");
				}
				out.append("<input type='hidden' class='keyEvents' id='" + key + "' value='" + method + "'></input>\n");
			}
			model.lookupConversation().getKeyPressEvents().clear();
		}

		if (!isAjaxRequest) {
			// Wenn Ajax Request, wird runScripts im js code selbst getriggert.
			out.append("<script type='text/javascript'>runScripts();</script>\n");
		}

		out.append("</div>\n"); // hiddenfields div end

		out.append("</form>\n");

		if (model.lookupConversation().getHtmlElementsAfterFormular() != null
				&& !model.lookupConversation().getHtmlElementsAfterFormular().isEmpty()) {
			for (String element : model.lookupConversation().getHtmlElementsAfterFormular()) {
				out.append("\n" + element + "\n");
			}
			model.lookupConversation().getHtmlElementsAfterFormular().clear();
		}

		if (!isAjaxRequest) {
			out.append("</body>\n");
			out.append("</html>\n");
		}
	}

}
