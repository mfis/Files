package mfi.files.helper;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReflectionHelper {

	private ReflectionHelper() {
		// noop
	}

	private static Logger logger = LoggerFactory.getLogger(ReflectionHelper.class);

	public static Class<?>[] getClassesInPackage(String packageName) {

		ArrayList<Class<?>> classes = new ArrayList<Class<?>>();

		try {
			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			String path = packageName.replace('.', '/');
			Enumeration<URL> resources = classLoader.getResources(path);
			List<File> dirs = new ArrayList<File>();

			while (resources.hasMoreElements()) {
				URL resource = resources.nextElement();
				String res = StringUtils.replace(resource.getFile(), "%20", " ");
				dirs.add(new File(res));
			}

			for (File directory : dirs) {
				classes.addAll(findClasses(directory, packageName));
			}

		} catch (Exception e) {
			throw new RuntimeException("Fehler beim Lesen der Klassen im Package: " + packageName, e);
		}

		return classes.toArray(new Class[classes.size()]);
	}

	private static List<Class<?>> findClasses(File directory, String packageName) throws ClassNotFoundException {

		List<Class<?>> classes = new ArrayList<Class<?>>();
		if (!directory.exists()) {
			return classes;
		}

		File[] files = directory.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				assert !file.getName().contains(".");
				classes.addAll(findClasses(file, packageName + "." + file.getName()));
			} else if (file.getName().endsWith(".class")) {
				try {
					classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
				} catch (NoClassDefFoundError ncdfe) {
					logger.warn("Class ignored: " + packageName + '.' + file.getName());
				}
			}
		}

		return classes;
	}

}
