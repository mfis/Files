package mfi.files.jobs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import mfi.files.annotation.FilesJob;
import mfi.files.io.FilesFile;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Job;

@Component
@FilesJob(cron = "35 2 * * *", failureCountUntilStartLogging = 0, hasCryptoConfig = false)
public class TempDirCleanUp extends Job {

	private static Logger logger = LoggerFactory.getLogger(TempDirCleanUp.class);

	// ---------------------ms----s----m----h-
	long oneDayInMillies = 1000 * 60 * 60 * 24;

	@Override
	public void runJob() {

		String dirString = KVMemoryMap.getInstance().readValueFromKey("application.tempdirectory");
		if (dirString == null) {
			return;
		}

		File dir = new FilesFile(dirString);
		if (!dir.exists()) {
            logger.debug("TempDir nicht gefunden: {}", dirString);
            return;
		}

		long nowInMillies = System.currentTimeMillis();

		List<File> list = (List<File>) FileUtils.listFiles(dir, FileFilterUtils.trueFileFilter(), FileFilterUtils.trueFileFilter());
		for (File file : list) {
			long fileDateInMillies = file.lastModified();
			if ((fileDateInMillies + oneDayInMillies) < nowInMillies) {
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (IOException e) {
                    logger.warn("Could not delete temp file {}", file.getAbsolutePath(), e);
                }
			}
		}
	}
}
