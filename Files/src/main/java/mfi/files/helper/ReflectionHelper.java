package mfi.files.helper;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.lang3.StringUtils;

public class ReflectionHelper {

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
				classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
			}
		}

		return classes;
	}

	public static Class<?>[] loadClassesFromJar(String jarfile) throws IOException, ClassNotFoundException {

		URLClassLoader sysloader = (URLClassLoader) Thread.currentThread().getContextClassLoader();
		Class<?> sysclass = URLClassLoader.class;
		try {
			Class<?>[] parameters = new Class[] { URL.class };
			Method method = sysclass.getDeclaredMethod("addURL", parameters);
			method.setAccessible(true);
			method.invoke(sysloader, new Object[] { new File(jarfile).toURI().toURL() });
		} catch (Throwable t) {
			throw new IOException("Error, could not add URL to system classloader");
		}

		JarFile jarFile = new JarFile(jarfile);
		List<Class<?>> jobClassesPlugin = new LinkedList<Class<?>>();

		final Enumeration<JarEntry> entries = jarFile.entries();
		while (entries.hasMoreElements()) {
			final JarEntry entry = entries.nextElement();
			if (entry.getName().contains(".")) {
				String name = entry.getName();
				if (StringUtils.endsWith(name, ".class")) {
					name = StringUtils.removeEnd(name, ".class");
					name = StringUtils.replace(name, "/", ".");
					Class<?> cs = Thread.currentThread().getContextClassLoader().loadClass(name);
					jobClassesPlugin.add(cs);
				}
			}
		}
		jarFile.close();
		return jobClassesPlugin.toArray(new Class<?>[jobClassesPlugin.size()]);
	}

}
