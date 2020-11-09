package mfi.files.jobs;

import java.io.IOException;

import org.springframework.stereotype.Component;

import mfi.files.annotation.FilesJob;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Job;

@Component
@FilesJob(cron = "11 1 1 * *", failureCountUntilStartLogging = 1, hasCryptoConfig = false)
public class DBCleanUpMonth extends Job {

	@Override
	public void runJob() throws IOException {

		KVMemoryMap.getInstance().deleteKeyRangeStartsWith("temporary.month.");
		KVMemoryMap.getInstance().saveAndReload();
	}
}
