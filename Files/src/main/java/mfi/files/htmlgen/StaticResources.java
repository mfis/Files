package mfi.files.htmlgen;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mfi.files.helper.ServletHelper;

public class StaticResources {

	private static StaticResources instance;

	private static final Object monitor = new Object();

	private static Map<String, String> map;

	private static Logger logger = LoggerFactory.getLogger(StaticResources.class);

	private static boolean devMode = false;

	static {
		instance = new StaticResources();
	}

	private Map<String, String> buildNewMap() {
		return Collections.synchronizedMap(new TreeMap<String, String>());
	}

	private StaticResources() {
		map = buildNewMap();
	}

	public static StaticResources getInstance() {
		if (instance == null) {
			synchronized (monitor) {
				if (instance == null) {
					instance = new StaticResources();
					logger.info("RE-Initializing StaticResources Singleton");
				}
			}
		}
		return instance;
	}

	public String get(String name) {

		if (isDevMode()) {
			return name;
		} else if (map.containsKey(name)) {
			return "static/" + map.get(name) + "_" + name;
		} else {
			logger.warn("Resource not found: " + name);
			return name;
		}
	}

	public String readIntoString(String name) {

		String staticname = StringUtils.removeStart(get(name), "static/");
		InputStream in = this.getClass().getClassLoader().getResourceAsStream(staticname);

		StringWriter writer = new StringWriter();
		try {
			IOUtils.copy(in, writer, ServletHelper.STRING_ENCODING_UTF8);
		} catch (Exception e) {
			logger.warn("Resource could not be read: " + name + " / " + staticname, e);
			writer = new StringWriter();
		}
		return writer.toString();
	}

	private void reset() {
		synchronized (monitor) {
			map = buildNewMap();
		}
	}

	public void load() {

		reset();
		Properties p = new Properties();

		try {
			URL url = this.getClass().getClassLoader().getResource("staticResourceVersioning.properties");

			URLConnection resConn = url.openConnection();
			resConn.setUseCaches(false);
			InputStream in = resConn.getInputStream();

			p.load(in);
		} catch (IOException e) {
			logger.error("Error loading Resources: ", e);
		}
		Enumeration<?> e = p.propertyNames();
		while (e.hasMoreElements()) {
			String key = (String) e.nextElement();
			// Xystem.out.println(key + " / " + p.getProperty(key));
			map.put(key, p.getProperty(key));
		}
	}

	public boolean isDevMode() {
		return devMode;
	}

	public void setDevMode(boolean devMode) {
		StaticResources.devMode = devMode;
	}

}
