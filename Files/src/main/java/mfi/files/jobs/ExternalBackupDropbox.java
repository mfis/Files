package mfi.files.jobs;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.CommitInfo;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.UploadSessionCursor;
import com.dropbox.core.v2.files.WriteMode;

import mfi.files.annotation.FilesJob;
import mfi.files.helper.ExternalBackupHelper;
import mfi.files.io.FilesFile;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Job;

@Component
@FilesJob(cron = "* * * * *", failureCountUntilStartLogging = 2, hasCryptoConfig = true)
public class ExternalBackupDropbox extends Job {

	private static final long CHUNKSIZE = 1024 * 1024; // 1 MB

	@Override
	public void runJob() {

		List<String> valueList = KVMemoryMap.getInstance().readValueList("temp.backup.key.dropbox");

		if (valueList.size() == 0) {
			return;
		}

		String accessToken = KVMemoryMap.getInstance().readValueFromKey("secureentry.account.dropbox.accessToken");
		String backupRoot = StringUtils.trimToNull(KVMemoryMap.getInstance().readValueFromKey("application.properties.backupLocal"));
		String environmentName = KVMemoryMap.getInstance().readValueFromKey("application.environment.name");
		boolean devMode = StringUtils.trimToEmpty(KVMemoryMap.getInstance().readValueFromKey("application.properties.developmentMode"))
				.equalsIgnoreCase(Boolean.TRUE.toString());

		DbxRequestConfig config = new DbxRequestConfig("FilesBackupClient");
		DbxClientV2 clientV2 = new DbxClientV2(config, accessToken);

		try {

			for (String filename : valueList) {
				String kvKey = filename;
				filename = ExternalBackupHelper.lookupFilename(filename);

				String uploadFileName = ExternalBackupHelper.ermittleDateiNamen(backupRoot, environmentName, filename);
				String path = new FilesFile(uploadFileName).getParent();
				File uploadFile = new File(filename);
				InputStream in = new FileInputStream(uploadFile);

				long progress = Math.min(uploadFile.length(), CHUNKSIZE);
				String sid = clientV2.files().uploadSessionStart().uploadAndFinish(in, progress).getSessionId();
				UploadSessionCursor uploadSessionCursor = new UploadSessionCursor(sid, progress);

				while ((uploadFile.length() - progress) > CHUNKSIZE) {
					clientV2.files().uploadSessionAppendV2(uploadSessionCursor).uploadAndFinish(in, CHUNKSIZE);
					progress += CHUNKSIZE;
					uploadSessionCursor = new UploadSessionCursor(sid, progress);
				}

				long diff = uploadFile.length() - progress;
				CommitInfo commitInfo = CommitInfo.newBuilder(path).withMode(WriteMode.ADD)
						.withClientModified(new Date(uploadFile.lastModified())).build();

				FileMetadata metadata = clientV2.files().uploadSessionFinish(uploadSessionCursor, commitInfo).uploadAndFinish(in, diff);

				if (metadata != null) {
					KVMemoryMap.getInstance().deleteValueFromList("temp.backup.key.dropbox", kvKey);
				}
			}

		} catch (Throwable e) {
			if (devMode) {
				System.err.println("ExternalBackupDropbox: " + e.getMessage());
			}
			throw new RuntimeException("ExternalBackupDropbox:" + e.getMessage());
		}
	}

}
