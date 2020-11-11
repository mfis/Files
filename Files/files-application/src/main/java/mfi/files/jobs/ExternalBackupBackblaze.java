package mfi.files.jobs;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.backblaze.b2.client.B2ListFilesIterable;
import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.contentSources.B2ContentSource;
import com.backblaze.b2.client.contentSources.B2ContentTypes;
import com.backblaze.b2.client.contentSources.B2FileContentSource;
import com.backblaze.b2.client.structures.B2FileVersion;
import com.backblaze.b2.client.structures.B2UploadFileRequest;
import com.backblaze.b2.client.webApiHttpClient.B2StorageHttpClientBuilder;
import com.backblaze.b2.client.webApiHttpClient.HttpClientFactory;
import com.backblaze.b2.client.webApiHttpClient.HttpClientFactoryImpl;
import com.backblaze.b2.util.B2ExecutorUtils;

import mfi.files.annotation.FilesJob;
import mfi.files.helper.ExternalBackupHelper;
import mfi.files.io.FilesFile;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Job;

@Component
@FilesJob(cron = "* * * * *", failureCountUntilStartLogging = 2, hasCryptoConfig = true)
public class ExternalBackupBackblaze extends Job {

	private ExecutorService executor;

	@Override
	public void runJob() throws Exception {

		List<String> valueList = KVMemoryMap.getInstance().readValueList("temp.backup.key.backblaze");

		if (valueList.size() > 0) {

			String backupRoot = StringUtils.trimToNull(KVMemoryMap.getInstance().readValueFromKey("application.properties.backupLocal"));
			String environmentName = KVMemoryMap.getInstance().readValueFromKey("application.environment.name");

			// Getting credentials
			String accountid = KVMemoryMap.getInstance().readValueFromKey("secureentry.account.backup.backblaze.accountid");
			String applicationkey = KVMemoryMap.getInstance().readValueFromKey("secureentry.account.backup.backblaze.applicationkey");
			String bucketid = KVMemoryMap.getInstance().readValueFromKey("secureentry.account.backup.backblaze.bucketid");

			HttpClientFactory httpClientFactory = HttpClientFactoryImpl.builder().build();
			B2StorageClient client = B2StorageHttpClientBuilder.builder(accountid, applicationkey, "Files")
					.setHttpClientFactory(httpClientFactory).build();

			try {

				for (String filename : valueList) {

					String kvKey = filename;
					filename = ExternalBackupHelper.lookupFilename(filename);

					String uploadFileName = ExternalBackupHelper.ermittleDateiNamen(backupRoot, environmentName, filename);
					String path = new FilesFile(uploadFileName).getParent();
					path = StringUtils.removeStart(path, "/");
					FilesFile fileToUpload = new FilesFile(filename);
					B2ContentSource source = B2FileContentSource.build(fileToUpload);
					B2UploadFileRequest request = B2UploadFileRequest.builder(bucketid, path, B2ContentTypes.B2_AUTO, source).build();

					final long contentLength = request.getContentSource().getContentLength();
					B2FileVersion file;
					if (client.getFilePolicy().shouldBeLargeFile(contentLength)) {
						file = client.uploadLargeFile(request, getExecutor());
					} else {
						file = client.uploadSmallFile(request);
					}

					B2ListFilesIterable unfinishedFiles = client.unfinishedLargeFiles(bucketid);

					if (file != null && StringUtils.isNotBlank(file.getFileId())) {
						while (unfinishedFiles.iterator().hasNext()) {
							if (unfinishedFiles.iterator().next().getFileId().equals(file.getFileId())) {
								throw new IllegalStateException("Upload is unfinished:" + file.getFileName());
							}
						}
						KVMemoryMap.getInstance().deleteValueFromList("temp.backup.key.backblaze", kvKey);
					}
				}
			} finally {
				client.close();
			}
		}
	}

	private ExecutorService getExecutor() {
		if (executor == null) {
			executor = Executors.newFixedThreadPool(2, B2ExecutorUtils.createThreadFactory("FilesBackblazeBackup" + "-%d"));
		}
		return executor;
	}

}
