package mfi.files.model;

public class JobModel {

	Class<? extends Job> key;
	long startTime;
	boolean sucessful;
	boolean skipped;
	Throwable thrown;

	public JobModel(Class<? extends Job> clazz) {
		key = clazz;
		startTime = System.currentTimeMillis();
		sucessful = true;
		skipped = false;
		thrown = null;
	}

	public boolean isSucessful() {
		return sucessful;
	}

	public void setSucessful(boolean sucessful) {
		this.sucessful = sucessful;
	}

	public boolean isSkipped() {
		return skipped;
	}

	public void setSkipped(boolean skipped) {
		this.skipped = skipped;
	}

	public Throwable getThrown() {
		return thrown;
	}

	public void setThrown(Throwable thrown) {
		this.thrown = thrown;
	}

	public Class<? extends Job> getKey() {
		return key;
	}

	public long getStartTime() {
		return startTime;
	}

}
