package mfi.files.helper;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mfi.files.io.FilesFile;
import mfi.files.logic.Crypto;
import mfi.files.maps.KVMemoryMap;

public class ApplicationUtil {

	private static Logger logger = LoggerFactory.getLogger(ApplicationUtil.class);

	public static void checkApplicationFiles() {

		boolean allOK = true;

		Properties properties = ApplicationUtil.getApplicationProperties();

		for (String name : properties.stringPropertyNames()) {
			if (name.startsWith("/")) {
				FilesFile file = new FilesFile(properties.getProperty(name));
				if (!file.isFileWriteableForApplication()) {
					allOK = false;
				}
			}
		}

		KVMemoryMap.getInstance().writeKeyValue("application.fileSystemCheck", Boolean.toString(allOK), true);

	}

	public static void checkUnlimitedStrengthCryptoIsEnabled() {

		try {
			Crypto.passwordTo256bitKey("x");
			KVMemoryMap.getInstance().writeKeyValue("application.unlimitedStrengthCryptoEnabled", Boolean.toString(true), true);
		} catch (RuntimeException e) {
			KVMemoryMap.getInstance().writeKeyValue("application.unlimitedStrengthCryptoEnabled", Boolean.toString(false), true);
		}

	}

	public static void storeUptime() {
		Crypto.passwordTo256bitKey("x");
		KVMemoryMap.getInstance().writeKeyValue("application.uptime", Hilfsklasse.zeitstempelAlsString(new Date().getTime()), true);
	}

	public static Properties getApplicationProperties() {

		Properties properties = new Properties();
		String path = System.getProperty("user.home") + "/documents/config/files.properties";

		try {
			properties.load(new FileInputStream(path));
			return properties;
		} catch (Exception e) {
			logger.error("Properties could not be loaded from " + path + " - ", e);
			return null;
		}
	}

	public static String getSystemUptime() throws Exception {

		try {
			String os = System.getProperty("os.name").toLowerCase();
			if (os.contains("win")) {
				Process uptimeProc = Runtime.getRuntime().exec("net stats srv");
				BufferedReader in = new BufferedReader(new InputStreamReader(uptimeProc.getInputStream()));
				String line;
				while ((line = in.readLine()) != null) {
					// Statistics since 01.02.2017
					if (line.startsWith("Stat")) {
						line = StringUtils.substringAfter(line, " ");
						line = StringUtils.substringAfter(line, " ");
						return line.trim();
					}
				}
			} else if (os.contains("mac") || os.contains("nix") || os.contains("nux") || os.contains("aix") || os.contains("debian")) {
				// 21:06:00 up 3 days, 45 min, 1 user, load average: 0,06, 0,06, 0,08
				Process uptimeProc = Runtime.getRuntime().exec("uptime");
				BufferedReader in = new BufferedReader(new InputStreamReader(uptimeProc.getInputStream()));
				String line = in.readLine();
				if (line != null) {
					line = StringUtils.substringAfter(line, " up ");
					if (line.contains("user")) {
						line = StringUtils.substringBeforeLast(line, "user");
						line = StringUtils.substringBeforeLast(line, ",");
					}
					line = line.trim();
					if (line.length() > 0 && CharUtils.isAsciiNumeric(line.charAt(line.length() - 1))) {
						if (line.contains(":")) {
							line = StringUtils.replace(line, ":", " hours, ");
							line = line + " minutes";
						} else {
							line = line + " hours";
						}
					}
					return line;
				}
			}
			return "unknown";
		} catch (Exception e) {
			logger.error("error getting uptime: ", e);
			return "unknown...";
		}
	}
}
