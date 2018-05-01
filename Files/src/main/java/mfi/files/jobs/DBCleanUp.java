package mfi.files.jobs;

import java.io.IOException;
import java.util.Collection;

import mfi.files.annotation.FilesJob;
import mfi.files.io.FilesFile;
import mfi.files.logic.DateiBackup;
import mfi.files.maps.FileMap;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Job;

@FilesJob(cron = "20 0 * * *", failureCountUntilStartLogging = 1, hasCryptoConfig = false)
public class DBCleanUp extends Job {

	@Override
	public void runJob() throws IOException {

		FileMap.getInstance().deleteOrphans();
		Collection<FilesFile> backupDateien = DateiBackup.erstelleFileListeAllerBackupDateien();
		FileMap.getInstance().updateList(backupDateien);
		FileMap.getInstance().save();

		KVMemoryMap.getInstance().deleteKeyRangeStartsWith("temporary.day.");
		KVMemoryMap.getInstance().saveAndReload();

	}
}
