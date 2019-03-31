package mfi.files.listener;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Iterator;
import java.util.Properties;
import java.util.TimeZone;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.ServletContextEvent;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.sauronsoftware.cron4j.Scheduler;
import mfi.files.annotation.FilesJob;
import mfi.files.helper.ApplicationUtil;
import mfi.files.helper.ReflectionHelper;
import mfi.files.io.FilesFile;
import mfi.files.maps.FileMap;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.CronSchedulers;
import mfi.files.model.Job;

@Component
public class FilesContextListener {

	private static Logger logger = LoggerFactory.getLogger(FilesContextListener.class);

	@PreDestroy
	public void contextDestroyed(ServletContextEvent arg0) {

		CronSchedulers.getInstance().unregisterAndStopAllSchedulers();

		try {
			KVMemoryMap.getInstance().save();
		} catch (IOException e) {
			logger.error("Error saving KVMemoryMap", e);
		}

		try {
			FileMap.getInstance().save();
		} catch (IOException e) {
			logger.error("Error saving StaticResources", e);
		}

		logger.info("Context destroyed.");
	}

	@PostConstruct
	public void contextInitialized() {

		logger.info("Context initializing...");

		Properties properties = ApplicationUtil.getApplicationProperties();

		try {
			KVMemoryMap.getInstance().load(new FilesFile(properties.getProperty("kvMapPerm")));
		} catch (IOException e) {
			logger.error("Error initializing KVMemoryMap", e);
		}

		if (!(StringUtils.trimToEmpty(KVMemoryMap.getInstance().readValueFromKey("application.properties.developmentMode"))
				.equalsIgnoreCase(Boolean.TRUE.toString()))) {
			// PRODUCTIVE MODE - No console logging
			ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger) LoggerFactory
					.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
			if (rootLogger.getAppender("STDOUT") != null) {
				rootLogger.getAppender("STDOUT").stop();
			}
		} else {
			printEnvironmentVars();
		}

		try {
			String pathOfFileMap = KVMemoryMap.getInstance().readValueFromKey("application.properties.backupLocal");
			pathOfFileMap = pathOfFileMap + "/";
			pathOfFileMap = pathOfFileMap + "/fileMap_" + KVMemoryMap.getInstance().readValueFromKey("application.environment.name")
					+ ".map";
			FileMap.getInstance().load(new FilesFile(pathOfFileMap));
		} catch (IOException e) {
			logger.error("Error initializing FileMap", e);
		}

		ApplicationUtil.checkApplicationFiles();
		ApplicationUtil.checkUnlimitedStrengthCryptoIsEnabled();
		ApplicationUtil.storeUptime();

		startCronJobs();

		lookupEnvironment(properties);

		logger.info("Context initialized.");
	}

	private void startCronJobs() {

		try {
			TimeZone timeZone = TimeZone.getTimeZone(KVMemoryMap.getInstance().readValueFromKey("application.properties.timezone"));

			// Add Plugin-JARs to Classloader
			Class<?>[] jobClassesPlugin = new Class<?>[0];
			String pluginPath = StringUtils.trimToNull(KVMemoryMap.getInstance().readValueFromKey("application.properties.jobPlugins"));
			if (pluginPath != null) {
				jobClassesPlugin = ReflectionHelper.loadClassesFromJar(pluginPath);
			}

			// Scan for own Jobs in Webapp
			Class<?>[] jobClassesInternal = ReflectionHelper.getClassesInPackage("mfi.files.jobs");

			Class<?>[] jobClasses = ArrayUtils.addAll(jobClassesInternal, jobClassesPlugin);

			for (Class<?> clazz : jobClasses) {

				Object instance = clazz.newInstance();
				if (instance instanceof Job) {

					Annotation[] annotations = clazz.getDeclaredAnnotations();
					for (Annotation annotation : annotations) {
						if (annotation instanceof FilesJob) {
							String cron = ((FilesJob) annotation).cron();

							Scheduler scheduler = new Scheduler();
							scheduler.setTimeZone(timeZone);
							String schedulerID = scheduler.schedule(cron, (Runnable) instance);
							CronSchedulers.getInstance().registerAndStartScheduler(clazz.getSimpleName(), scheduler, schedulerID,
									(Job) instance);
						}
					}
				}
			}

		} catch (Exception e) {
			throw new RuntimeException("CronJobs konnten nicht gestartet werden.", e);
		}

	}

	private void lookupEnvironment(Properties properties) {

		String severName = "";
		String builddate = "";
		String warfilename = "";

		KVMemoryMap.getInstance().writeKeyValue("application.builddate", builddate, true);
		KVMemoryMap.getInstance().writeKeyValue("application.warfile", warfilename, true);
		KVMemoryMap.getInstance().writeKeyValue("application.server", severName, true);
		KVMemoryMap.getInstance().writeKeyValue("application.kvdblog", properties.getProperty("kvdblog"), true);
	}

	private void printEnvironmentVars() {

		StringBuilder sb = new StringBuilder();
		sb.append("\nprintEnvironmentVars() ");
		Iterator<String> i = System.getProperties().stringPropertyNames().iterator();
		while (i.hasNext()) {
			String a = i.next();
			sb.append("\nSystemProperty:" + a + "=" + StringUtils.trimToEmpty(System.getProperty(a)));
		}
		sb.append("\n");
		logger.info(sb.toString());
	}

}