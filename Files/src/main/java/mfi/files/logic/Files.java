package mfi.files.logic;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mfi.files.annotation.Responsible;
import mfi.files.helper.ReflectionHelper;
import mfi.files.htmlgen.Button;
import mfi.files.htmlgen.HTMLTable;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.io.FilesFile;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Condition;
import mfi.files.model.Condition.Checks;
import mfi.files.model.Condition.Resets;
import mfi.files.model.Condition.StepBack;
import mfi.files.model.Model;
import mfi.files.model.ResponsibleMethod;

public class Files {

	private static Files instance;
	private static final Logger logger = LoggerFactory.getLogger(Files.class);
	private static Map<Condition, ResponsibleMethod> map;

	static {
		instance = new Files();
		logger.info("Initializing Files Singleton");
		map = new HashMap<Condition, ResponsibleMethod>();
		lookupAnnotationList();
	}

	private Files() {
		// private default constructor
	}

	private static void lookupAnnotationList() {

		try {

			Class<?>[] responsibleClasses = ReflectionHelper.getClassesInPackage("mfi.files.responsibles");

			for (Class<?> clazz : responsibleClasses) {

				if (!Modifier.isAbstract(clazz.getModifiers()) && !clazz.isAnonymousClass()) {

					Object instance = clazz.newInstance();
					Method[] methods = clazz.getDeclaredMethods();

					for (Method method : methods) {
						Annotation[] annotations = method.getDeclaredAnnotations();
						for (Annotation annotation : annotations) {
							if (annotation instanceof Responsible) {
								Condition[] conditions = ((Responsible) annotation).conditions();
								for (Condition condition : conditions) {
									if (map.containsKey(condition)) {
										throw new RuntimeException("Doppelter Responsible Eintrag: " + condition.toString());
									} else {
										map.put(condition, new ResponsibleMethod(instance, method));
									}
								}
							}
						}
					}
				}
			}

		} catch (Exception e) {
			throw new RuntimeException("Responsible Liste konnte nicht erstellt werden.", e);
		}

	}

	public static Files getInstance() {
		return instance;
	}

	public void files(StringBuilder sb, Map<String, String> parameters, Model model) throws Exception {

		verarbeitungVorbereiten(sb, parameters, model);

		int iterations = 0;
		int posMeldungen = sb.length();
		model.lookupConversation().setOriginalRequestCondition(true);

		do {
			if (map.containsKey(model.lookupConversation().getCondition())) {
				ResponsibleMethod rm = map.get(model.lookupConversation().getCondition());

				conditionVorbereiten(parameters, model);
				rm.getMethod().invoke(rm.getClazz(), sb, parameters, model);
				conditionNachbereiten(model);

			} else {
				throw new RuntimeException(
						"Responsible Methode nicht gefunden fuer: " + model.lookupConversation().getCondition().toString());
			}

			if (model.lookupConversation().getCondition() != null
					&& model.lookupConversation().getCondition().getStepBack() == StepBack.YES) {
				model.lookupConversation().setStepBackCondition(model.lookupConversation().getCondition());
			} else {
				model.lookupConversation().setStepBackCondition(null);
			}

			model.lookupConversation().setCondition(model.lookupConversation().getForwardCondition());
			model.lookupConversation().setForwardCondition(null);
			model.lookupConversation().setOriginalRequestCondition(false);

			iterations++;
			if (iterations > 100) {
				throw new RuntimeException("Detected infinite loop:" + model.lookupConversation().getCondition());
			}
		} while (model.lookupConversation().getCondition() != null);

		setzeMeldungen(sb, posMeldungen, parameters, model);
	}

	private void verarbeitungVorbereiten(StringBuilder sb, Map<String, String> parameters, Model model) {

		if (model.isDevelopmentMode()) {
			sb.append(setzeDevelopmentModeWarnung(model));
		}

		if (model.isClientTouchDevice() && parameters.containsKey(HTMLUtils.RUNNING_AS_WEBAPP)
				&& StringUtils.isNotEmpty(parameters.get(HTMLUtils.RUNNING_AS_WEBAPP))) {
			boolean marker = StringUtils.equalsIgnoreCase(parameters.get(HTMLUtils.RUNNING_AS_WEBAPP), "true");
			model.setIstWebApp(marker);
		}
		model.lookupConversation().setForwardCondition(null);

		boolean fileSystemCheckOk = StringUtils.equalsIgnoreCase(KVMemoryMap.getInstance().readValueFromKey("application.fileSystemCheck"),
				Boolean.TRUE.toString());

		if (!fileSystemCheckOk) {
			model.lookupConversation().getMeldungen().add("Achtung: Dateisystem ist nicht beschreibbar !");
		}
	}

	private void conditionVorbereiten(Map<String, String> parameters, Model model) {

		if (model.lookupConversation().getCondition() != null) {

			if (model.lookupConversation().getCondition().getChecks() == Checks.SELECTED_FILE) {

				checkEditedFile(parameters, model);
			}

			if (model.lookupConversation().getCondition().getResets() == Resets.LOCK_BEFORE
					|| model.lookupConversation().getCondition().getResets() == Resets.LOCK_AND_FILE_BEFORE) {

				if (model.lookupConversation().getEditingFile() != null) {
					model.lookupConversation().getEditingFile().loescheFileLock(model.getUser());
				}
			}

			if (model.lookupConversation().getCondition().getResets() == Resets.LOCK_AND_FILE_BEFORE) {

				model.lookupConversation().setEditingFile(null);
			}
		}
	}

	private void conditionNachbereiten(Model model) {

		if (model.lookupConversation().getCondition() != null) {

			if (model.lookupConversation().getCondition().getResets() == Resets.LOCK_AFTER
					|| model.lookupConversation().getCondition().getResets() == Resets.LOCK_AND_FILE_AFTER) {

				if (model.lookupConversation().getEditingFile() != null) {
					model.lookupConversation().getEditingFile().loescheFileLock(model.getUser());
				}
			}

			if (model.lookupConversation().getCondition().getResets() == Resets.LOCK_AND_FILE_AFTER) {

				model.lookupConversation().setEditingFile(null);
			}
		}
	}

	public static String verarbeiteFehler(Model model) {

		Security.logoffUser(model);

		HTMLTable table = new HTMLTable();
		table.addTD("<pre>" + "Bei der Verarbeitung der Anfrage ist ein Fehler aufgetreten" + "</pre>", 1,
				HTMLUtils.buildAttribute("width", "100%") + HTMLUtils.buildAttribute("class", "red"));
		table.addNewRow();
		table.addTDSource(new Button("Restart", Condition.LOGIN_FORMULAR).printForUseInTable(), 1,
				HTMLUtils.buildAttribute("height", "20px"));
		table.addNewRow();

		return table.buildTable(model);
	}

	private void setzeMeldungen(StringBuilder sb, int posMeldungen, Map<String, String> parameters, Model model) throws IOException {

		if (model.lookupConversation().getMeldungen().size() > 0) {

			StringBuilder hinweise = new StringBuilder();
			for (int i = 0; i < model.lookupConversation().getMeldungen().size(); i++) {
				hinweise.append("<div class=\"infobanner\"><a>" + "&bull; &nbsp; " + model.lookupConversation().getMeldungen().get(i)
						+ "</a></div>\n");
			}

			sb.insert(posMeldungen, "\n " + hinweise.toString() + " \n");

			model.lookupConversation().setMeldungen(new LinkedList<String>());
		}

		if (StringUtils.equalsIgnoreCase(KVMemoryMap.getInstance().readValueFromKey("application.unlimitedStrengthCryptoEnabled"),
				Boolean.FALSE.toString())) {
			sb.insert(posMeldungen, "\n " + setzeUnlimitedStrengthCryptoWarnung(model) + " \n");
		}

	}

	private String setzeDevelopmentModeWarnung(Model model) {
		return "<div class=\"warningbanner\"><a id=\"dspl\" onclick=\"display();\" href=\"#\">DEVELOPMENT MODUS - "
				+ model.lookupConversation().getCondition() + "</a></div>\n";
	}

	private String setzeUnlimitedStrengthCryptoWarnung(Model model) {
		return "<div class=\"noticebanner\">" + "Achtung! UnlimitedStrengthCrypto ist nicht aktiv!" + "</div>\n";
	}

	private static final void checkEditedFile(Map<String, String> parameters, Model model) {

		FilesFile edited = lookupEditedFile(parameters, model);
		if (edited != null) {
			model.lookupConversation().setEditingFile(edited);
		}

		if (model.lookupConversation().getEditingFile() != null) {
			// Hash aktualisieren fuer EventServlet
			model.lookupConversation().getEditingFile().refreshHashValue();
		}
	}

	private static FilesFile lookupEditedFile(Map<String, String> parameters, Model model) {

		if (model.lookupConversation().getFsListe() != null && parameters.containsKey(HTMLUtils.TABLEINDEX)
				&& StringUtils.isNotEmpty(parameters.get(HTMLUtils.TABLEINDEX))
				&& StringUtils.isNumeric(parameters.get(HTMLUtils.TABLEINDEX).trim())) {

			FilesFile file = model.lookupConversation().getFsListe().get(Integer.parseInt(parameters.get(HTMLUtils.TABLEINDEX)));

			if (file != null) {
				if (file.isFile()) {
					if (Security.isFileAllowedForUser(model, file)) {
						return file;
					}
				} else {
					// dir immer zurueck, auch wenn nicht berechtigt
					return file;
				}
			}
		}

		return null;
	}

}
