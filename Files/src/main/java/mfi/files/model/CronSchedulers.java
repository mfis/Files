package mfi.files.model;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.SchedulingPattern;

public class CronSchedulers {

	private static Object monitor = new Object();

	private static CronSchedulers instance = null;

	private final Set<Class<? extends Job>> jobs;

	private final Map<String, Scheduler> schedulers;
	private final Map<String, String> schedulersIDs;
	private final Map<String, Job> schedulersInstances;

	private CronSchedulers() {
		schedulers = new ConcurrentHashMap<String, Scheduler>();
		schedulersIDs = new ConcurrentHashMap<String, String>();
		schedulersInstances = new ConcurrentHashMap<String, Job>();
		jobs = new HashSet<>();
	}

	public static CronSchedulers getInstance() {
		synchronized (monitor) {
			if (instance == null) {
				instance = new CronSchedulers();
			}
		}
		return instance;
	}

	public synchronized Map<String, Scheduler> getSchedulers() {
		return schedulers;
	}

	public void registerAndStartScheduler(String name, Scheduler scheduler, String id, Job job) {
		synchronized (monitor) {
			scheduler.start();
			getSchedulers().put(name, scheduler);
			getSchedulersIDs().put(name, id);
			getSchedulersInstances().put(name, job);
		}
	}

	public void unregisterAndStopScheduler(String name) {
		synchronized (monitor) {
			if (isSchedulerRegistered(name)) {
				getSchedulers().get(name).stop();
				getSchedulers().remove(name);
				getSchedulersIDs().remove(name);
				getSchedulersInstances().remove(name);
			}
		}
	}

	public void unregisterAndStopAllSchedulers() {
		synchronized (monitor) {
			for (Map.Entry<String, Scheduler> entry : getSchedulers().entrySet()) {
				getSchedulers().get(entry.getKey()).stop();
				getSchedulers().remove(entry.getKey());
				getSchedulersIDs().remove(entry.getKey());
				getSchedulersInstances().remove(entry.getKey());
			}
		}
	}

	public String lookupCronStringOfScheduler(String schedulerName) {

		if (isSchedulerRegistered(schedulerName)) {
			SchedulingPattern pattern = getSchedulers().get(schedulerName).getSchedulingPattern(getSchedulersIDs().get(schedulerName));
			if (pattern != null) {
				return pattern.toString();
			}
		}
		return "";
	}

	public boolean isSchedulerRegistered(String name) {
		return getSchedulers().containsKey(name);
	}

	public Map<String, String> getSchedulersIDs() {
		return schedulersIDs;
	}

	public Map<String, Job> getSchedulersInstances() {
		return schedulersInstances;
	}

	public Set<Class<? extends Job>> getJobs() {
		return jobs;
	}

}
