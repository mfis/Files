package mfi.files.jobs;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import mfi.files.annotation.FilesJob;
import mfi.files.helper.ExternalBackupHelper;
import mfi.files.io.FilesFile;
import mfi.files.io.SFTPClient;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Job;

@Component
@FilesJob(cron = "* * * * *", failureCountUntilStartLogging = 2, hasCryptoConfig = true)
public class ExternalBackupFTP extends Job {

	@Override
	public void runJob() throws Exception {

		List<String> valueList = KVMemoryMap.getInstance().readValueList("temp.backup.key.ftp");

		if (valueList.size() > 0) {

			String backupRoot = StringUtils.trimToNull(KVMemoryMap.getInstance().readValueFromKey("application.properties.backupLocal"));
			String environmentName = KVMemoryMap.getInstance().readValueFromKey("application.environment.name");

			// Getting credentials
			String ftpHost = KVMemoryMap.getInstance().readValueFromKey("secureentry.account.backup.ftp.host");
			String ftpUser = KVMemoryMap.getInstance().readValueFromKey("secureentry.account.backup.ftp.user");
			String ftpPass = KVMemoryMap.getInstance().readValueFromKey("secureentry.account.backup.ftp.pass");

			boolean developmentMode = StringUtils
					.trimToEmpty(KVMemoryMap.getInstance().readValueFromKey("application.properties.developmentMode"))
					.equalsIgnoreCase(Boolean.TRUE.toString());

			SFTPClient ftpClient = new SFTPClient();
			ftpClient.connect(ftpHost, ftpUser, ftpPass, developmentMode);

			try {

				for (String filename : valueList) {

					String kvKey = filename;

					filename = ExternalBackupHelper.lookupFilename(filename);

					String uploadFileName = ExternalBackupHelper.ermittleDateiNamen(backupRoot, environmentName, filename);
					String path = new FilesFile(uploadFileName).getParent();
					FilesFile fileToUpload = new FilesFile(filename);

					ftpClient.uploadFile(fileToUpload, path);

					KVMemoryMap.getInstance().deleteValueFromList("temp.backup.key.ftp", kvKey);

				}

			} finally {
				ftpClient.disconnect();
			}
		}
	}

}
