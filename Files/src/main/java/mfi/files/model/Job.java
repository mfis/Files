package mfi.files.model;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import mfi.files.annotation.FilesJob;
import mfi.files.helper.Hilfsklasse;
import mfi.files.helper.ThreadLocalHelper;
import mfi.files.maps.KVMemoryMap;

public abstract class Job implements Runnable {

	private static Set<Class<? extends Job>> running;
	private static Map<Class<? extends Job>, Long> lastrun;
	private static Map<Class<? extends Job>, Integer> failurecount;
	private static Map<Class<? extends Job>, Long> suspended;

	private static final Object monitor = new Object();

	private static final long twoHoursInMillies = 1000 * 60 * 60 * 2;
	private static final long twelveHoursInMillies = 1000 * 60 * 60 * 12;

	static {
		running = Collections.synchronizedSet((new HashSet<Class<? extends Job>>())); // entry only when running
		lastrun = Collections.synchronizedMap((new HashMap<Class<? extends Job>, Long>())); // entry always, value last run
		failurecount = Collections.synchronizedMap((new HashMap<Class<? extends Job>, Integer>())); // entry always, regular value 0
		suspended = Collections.synchronizedMap((new HashMap<Class<? extends Job>, Long>())); // entry only when suspended
	}

	public final String status() {

		Class<? extends Job> key = this.getClass();
		String status = "";

		if (!failurecount.containsKey(key)) {
			return "still not run";
		}

		if (failurecount.get(key) == 0) {
			status = status + "ok";
			if (lastrun.containsKey(key)) {
				status = status + ", last run " + Hilfsklasse.zeitstempelAlsString(lastrun.get(key));
			}
		} else {
			status = status + failurecount.get(key) + " failures";
			if (suspended.containsKey(key)) {
				if (suspended.get(key) > System.currentTimeMillis()) {
					status = status + ", suspended until " + Hilfsklasse.zeitstempelAlsString(suspended.get(key));
				} else {
					suspended.remove(key);
				}

			}
		}

		if (running.contains(key)) {
			status = status + ", currently running";
		}

		return status;
	}

	public abstract void runJob() throws Exception;

	@Override
	public final void run() {

		JobModel jobModel = new JobModel(this.getClass());

		try {

			ThreadLocalHelper.setConversationID(null);
			ThreadLocalHelper
					.setModelPassword(KVMemoryMap.getInstance().readValueFromKey("application.properties.cipherFileNameCryptoKey"));

			controlEntriesBeforeRun(jobModel);

			if (jobModel.isSkipped()) {
				return;
			}

			runJob();

		} catch (Throwable t) {

			jobModel.setSucessful(false);
			jobModel.setThrown(t);

		} finally {

			try {
				controlEntriesAfterRun(jobModel);
			} catch (Throwable t) {
				LoggerFactory.getLogger(jobModel.getKey().toString()).error("controlEntriesAfterRun:", t);
			}
			ThreadLocalHelper.unset();
		}

	}

	private final void controlEntriesBeforeRun(JobModel jobModel) {

		synchronized (monitor) {

			if (running.contains(jobModel.getKey())) {
				jobModel.setSkipped(true);
			}

			if (!jobModel.isSkipped()) {

				if (!failurecount.containsKey(jobModel.getKey())) {
					failurecount.put(jobModel.getKey(), 0);
				}

				if (suspended.containsKey(jobModel.getKey())) {
					if (suspended.get(jobModel.getKey()) > System.currentTimeMillis()) {
						jobModel.setSkipped(true);
					} else {
						suspended.remove(jobModel.getKey());
					}
				}
			}

			if (!jobModel.isSkipped()) {
				running.add(jobModel.getKey());
			}
		}
	}

	private final void controlEntriesAfterRun(JobModel jobModel) {

		synchronized (monitor) {

			if (jobModel.isSucessful() && !jobModel.isSkipped()) {

				failurecount.put(jobModel.getKey(), 0);
				if (suspended.containsKey(jobModel.getKey())) {
					suspended.remove(jobModel.getKey());
				}
				lastrun.put(jobModel.getKey(), jobModel.getStartTime());

			} else if (!jobModel.isSkipped()) {

				failurecount.put(jobModel.getKey(), failurecount.get(jobModel.getKey()) + 1);
				// example for execution every minute:
				// 5 trials with errors -> 2 hours skipping.
				// 3 (8-5) further trials with errors -> 12 hours skipping.
				// from then on every 12 hours 1 trial.
				if (failurecount.get(jobModel.getKey()) >= 8) {
					suspended.put(jobModel.getKey(), jobModel.getStartTime() + twelveHoursInMillies - 10);
				} else if (failurecount.get(jobModel.getKey()) == 5) {
					suspended.put(jobModel.getKey(), jobModel.getStartTime() + twoHoursInMillies - 10);
				}
			}

			if (!jobModel.isSkipped()) {
				if (running.contains(jobModel.getKey())) {
					running.remove(jobModel.getKey());
				}
			}

			infoLogging(jobModel);
		}
	}

	private final void infoLogging(JobModel jobModel) {

		synchronized (monitor) {
			int failureCount = failurecount.get(jobModel.getKey());

			String susp = suspended.containsKey(jobModel.getKey()) ? Hilfsklasse.zeitstempelAlsString(suspended.get(jobModel.getKey()))
					: "none";
			String out = " succesful=" + (jobModel.isSkipped() ? "n/a" : jobModel.isSucessful()) + " skipped=" + jobModel.isSkipped()
					+ " failurecount=" + failureCount + " suspended=" + susp + " duration="
					+ (System.currentTimeMillis() - jobModel.getStartTime()) + "ms";

			if (jobModel.getThrown() == null) {
				if (StringUtils.trimToEmpty(KVMemoryMap.getInstance().readValueFromKey("application.properties.developmentMode"))
						.equalsIgnoreCase(Boolean.TRUE.toString())) {

					LoggerFactory.getLogger(jobModel.getKey().toString()).info("Job run:" + out);
				}
			} else {

				int c = 0;
				Annotation[] annotations = jobModel.getKey().getDeclaredAnnotations();
				for (Annotation annotation : annotations) {
					if (annotation instanceof FilesJob) {
						c = ((FilesJob) annotation).failureCountUntilStartLogging();
					}
				}

				if (failureCount >= c) {
					LoggerFactory.getLogger(jobModel.getKey().toString()).error("Job crashed:" + out + " - ", jobModel.getThrown());
				}
			}
		}
	}

}
