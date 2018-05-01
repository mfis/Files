package mfi.files.helper;

import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StopWatchHelper {

	private class Point {

		long timestamp;
		String name;

		public Point(Long timestamp, String name) {
			this.timestamp = timestamp;
			this.name = name;
		}

		public long getTimestamp() {
			return timestamp;
		}

		public String getName() {
			return name;
		}

	}

	LinkedList<Point> points;
	boolean isStopped;
	private final static String ENDPOINT = "mfi.files.helper.StopWatchHelper_INTERN_END_POINT";
	String className;

	public StopWatchHelper(String clazzName) {
		points = new LinkedList<Point>();
		isStopped = true;
		className = clazzName;
	}

	public void timePoint(boolean isActive, String nameForNextPointStartingNow) {
		if (isActive) {
			isStopped = false;
			points.add(new Point((System.nanoTime() / 1000000L), nameForNextPointStartingNow));
		}
	}

	public void stop(boolean isActive) {
		if (isActive) {
			isStopped = true;
			points.add(new Point((System.nanoTime() / 1000000L), ENDPOINT));
		}
	}

	public void logPoints(boolean isActive) {
		if (isActive) {
			if (!isStopped) {
				stop(isActive);
			}
			Logger logger = LoggerFactory.getLogger(this.getClass());
			String frmt = "for " + className + ": \n";
			if (points.size() > 1) {
				for (int i = 0; i < points.size() - 1; i++) {
					long timespan = ((points.get(i + 1).getTimestamp() - points.get(i).getTimestamp()));
					frmt = frmt + points.get(i).getName() + ": " + String.valueOf(timespan) + " ";
				}
				frmt = frmt + " ==> " + (points.get(points.size() - 1).getTimestamp() - points.get(0).getTimestamp()) + " ms";
			} else {
				frmt = frmt + "no measurement";
			}
			logger.info(frmt);
		}
	}
}
