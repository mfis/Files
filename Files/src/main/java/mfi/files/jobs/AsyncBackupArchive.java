package mfi.files.jobs;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mfi.files.annotation.FilesJob;
import mfi.files.io.FilesFile;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Job;

@FilesJob(cron = "3,13,23,33,43,53 * * * *", failureCountUntilStartLogging = 2, hasCryptoConfig = false)
public class AsyncBackupArchive extends Job {

	@Override
	public void runJob() throws Exception {

		List<String> valueList = KVMemoryMap.getInstance().readValueList("temp.asyncbackuparchive.key");
		Set<String> deduplicatedValueList = new HashSet<String>();
		String logName = null;

		for (String string : valueList) {
			if (!deduplicatedValueList.contains(string)) {
				deduplicatedValueList.add(string);
			}
		}

		if (deduplicatedValueList.size() > 0) {

			try {

				for (String filename : deduplicatedValueList) {

					logName = filename;
					FilesFile file = new FilesFile(filename);

					if (file.exists()) {
						file.backupFile(null);
					}

					KVMemoryMap.getInstance().deleteValueFromList("temp.asyncbackuparchive.key", filename);

				}

			} catch (Throwable t) {
				throw new IllegalStateException(logName, t);
			}
		}
	}

}
