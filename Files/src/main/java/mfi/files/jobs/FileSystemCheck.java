package mfi.files.jobs;

import java.io.IOException;

import mfi.files.annotation.FilesJob;
import mfi.files.helper.ApplicationUtil;
import mfi.files.model.Job;

@FilesJob(cron = "58 * * * *", failureCountUntilStartLogging = 0, hasCryptoConfig = false)
public class FileSystemCheck extends Job {

	@Override
	public void runJob() throws IOException {

		ApplicationUtil.checkApplicationFiles();

	}
}
