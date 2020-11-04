package mfi.files.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;

import org.apache.commons.lang3.StringUtils;

import mfi.files.io.FilesFile;

public class Conversation implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Integer conversationID;

	private Condition condition;
	private boolean originalRequestCondition;
	private Condition forwardCondition;
	private Condition stepBackCondition;
	private List<Cookie> cookiesReadFromRequest;
	private Map<String, Cookie> cookiesToWriteToResponse;
	private String verzeichnis;
	private LinkedList<FilesFile> fsListe;
	private FilesFile editingFile;
	private boolean textViewPush;
	private boolean textViewNumbers;
	private boolean filesystemViewDetails;
	private List<String> meldungen;
	private List<String> javaScriptOnPageLoaded;
	private List<String> javaScriptFilesToEmbed;
	private List<String> htmlElementsAfterFormular;
	private Map<String, String> keyPressEvents;
	private boolean ajaxFillIn;
	private String fileContent;

	public Conversation(Integer id, String startVerzeichnis) {
		conversationID = id;
		cookiesReadFromRequest = new ArrayList<>();
		cookiesToWriteToResponse = new HashMap<>();
		javaScriptOnPageLoaded = new LinkedList<>();
		javaScriptFilesToEmbed = new LinkedList<>();
		htmlElementsAfterFormular = new LinkedList<>();
		keyPressEvents = new HashMap<>();
		setMeldungen(new LinkedList<>());
		verzeichnis = startVerzeichnis;
		setForwardCondition(null);
		ajaxFillIn = false;
		originalRequestCondition = true;
	}

	public void lookupConditionForRequest(String conditionString) {
		if (StringUtils.isEmpty(conditionString)) {
			setFsListe(null);
			setEditingFile(null);
			setCondition(Condition.NULL);
		} else {
			setCondition(Condition.valueOf(conditionString));
		}
	}

	public List<Cookie> getCookiesReadFromRequest() {
		if (cookiesReadFromRequest == null) {
			cookiesReadFromRequest = new ArrayList<>();
		}
		return cookiesReadFromRequest;
	}

	public void setCookiesReadFromRequest(List<Cookie> cookiesReadFromRequest) {
		this.cookiesReadFromRequest = cookiesReadFromRequest;
	}

	public void setCookiesReadFromRequestConvenient(Cookie[] cookiesReadFromRequest) {
		if (cookiesReadFromRequest != null) {
			this.cookiesReadFromRequest = Arrays.asList(cookiesReadFromRequest);
		} else {
			this.cookiesReadFromRequest = null;
		}
	}

	public Map<String, Cookie> getCookiesToWriteToResponse() {
		if (cookiesToWriteToResponse == null) {
			cookiesToWriteToResponse = new HashMap<>();
		}
		return cookiesToWriteToResponse;
	}

	public void setCookiesToWriteToResponse(Map<String, Cookie> cookiesToWriteToResponse) {
		this.cookiesToWriteToResponse = cookiesToWriteToResponse;
	}

	public Condition getCondition() {
		return condition;
	}

	public void setCondition(Condition condition) {
		this.condition = condition;
	}

	public Condition getForwardCondition() {
		return forwardCondition;
	}

	public void setForwardCondition(Condition redirectCondition) {
		this.forwardCondition = redirectCondition;
	}

	public String getVerzeichnis() {
		return verzeichnis;
	}

	public void setVerzeichnis(String verzeichnis) {
		this.verzeichnis = verzeichnis;
	}

	public List<FilesFile> getFsListe() {
		return fsListe;
	}

	public void setFsListe(List<FilesFile> fsListe) {
		this.fsListe = (LinkedList<FilesFile>) fsListe;
	}

	public FilesFile getEditingFile() {
		return editingFile;
	}

	public void setEditingFile(FilesFile editingFile) {
		if (editingFile != this.editingFile) {
			getJavaScriptOnPageLoaded().add("forgetPasswords();");
		}
		this.editingFile = editingFile;
	}

	public void setNextEditingFile(FilesFile editingFile) {
		this.editingFile = editingFile;
	}

	public boolean isTextViewPush() {
		return textViewPush;
	}

	public void setTextViewPush(boolean textViewPush) {
		this.textViewPush = textViewPush;
	}

	public void resetTextViewPush(Model model) {
		setTextViewPush(!model.isPhone());
	}

	public List<String> getMeldungen() {
		return meldungen;
	}

	public void setMeldungen(List<String> meldungen) {
		this.meldungen = meldungen;
	}

	public List<String> getJavaScriptOnPageLoaded() {
		return javaScriptOnPageLoaded;
	}

	public void setJavaScriptOnPageLoaded(List<String> javaScriptOnPageLoaded) {
		this.javaScriptOnPageLoaded = javaScriptOnPageLoaded;
	}

	public Integer getConversationID() {
		return conversationID;
	}

	public boolean isTextViewNumbers() {
		return textViewNumbers;
	}

	public void setTextViewNumbers(boolean textViewNumbers) {
		this.textViewNumbers = textViewNumbers;
	}

	public Map<String, String> getKeyPressEvents() {
		return keyPressEvents;
	}

	public void setKeyPressEvents(Map<String, String> keyPressEvents) {
		this.keyPressEvents = keyPressEvents;
	}

	public List<String> getJavaScriptFilesToEmbed() {
		return javaScriptFilesToEmbed;
	}

	public void setJavaScriptFilesToEmbed(List<String> javaScriptFilesToEmbed) {
		this.javaScriptFilesToEmbed = javaScriptFilesToEmbed;
	}

	public boolean isFilesystemViewDetails() {
		return filesystemViewDetails;
	}

	public void setFilesystemViewDetails(boolean filesystemViewDetails) {
		this.filesystemViewDetails = filesystemViewDetails;
	}

	public List<String> getHtmlElementsAfterFormular() {
		return htmlElementsAfterFormular;
	}

	public void setHtmlElementsAfterFormular(List<String> htmlElementsAfterFormular) {
		this.htmlElementsAfterFormular = htmlElementsAfterFormular;
	}

	public boolean isAjaxFillIn() {
		return ajaxFillIn;
	}

	public void setAjaxFillIn(boolean ajaxFillIn) {
		this.ajaxFillIn = ajaxFillIn;
	}

	public Condition getStepBackCondition() {
		return stepBackCondition;
	}

	public void setStepBackCondition(Condition lastCondition) {
		this.stepBackCondition = lastCondition;
	}

	public String getFileContent() {
		return fileContent;
	}

	public void setFileContent(String fileContent) {
		this.fileContent = fileContent;
	}

	public boolean isOriginalRequestCondition() {
		return originalRequestCondition;
	}

	public void setOriginalRequestCondition(boolean originalRequestCondition) {
		this.originalRequestCondition = originalRequestCondition;
	}

}
