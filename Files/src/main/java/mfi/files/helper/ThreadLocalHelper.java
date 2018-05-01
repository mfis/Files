package mfi.files.helper;

import org.apache.commons.lang3.StringUtils;

public class ThreadLocalHelper {

	private static final ThreadLocal<Integer> threadLocalConversationID = new ThreadLocal<Integer>();

	// Um Passwoerter nicht im Klartext ins Modell schreiben zu muessen, werden diese verschluesselt im Modell vorgehalten. Die
	// Verschluesselung selbst geschieht mit einem Passwort, welches unter folgendem Key in der ThreadLocal VariableMap steht.
	private static final ThreadLocal<String> threadLocalModelPassword = new ThreadLocal<String>();

	private ThreadLocalHelper() {
		// private
	}

	public static void setConversationID(String id) {
		if (StringUtils.isEmpty(id)) {
			threadLocalConversationID.set(null);
		} else {
			threadLocalConversationID.set(Integer.parseInt(id));
		}
	}

	public static Integer getConversationID() {
		return threadLocalConversationID.get();
	}

	public static void setModelPassword(String pass) {
		if (StringUtils.isEmpty(pass)) {
			threadLocalModelPassword.set(null);
		} else {
			threadLocalModelPassword.set(pass);
		}
	}

	public static String getModelPassword() {
		return threadLocalModelPassword.get();
	}

	public static void unset() {
		threadLocalConversationID.remove();
		threadLocalModelPassword.remove();
	}

}
