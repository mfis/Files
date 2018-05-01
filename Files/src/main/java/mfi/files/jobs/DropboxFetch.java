package mfi.files.jobs;

import java.io.FileOutputStream;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;

import mfi.files.io.FilesFile;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Job;

// @FilesJob(cron = "0 6,8,10,12,14,16,18,20,22 * * *", failureCountUntilStartLogging = 2, hasCryptoConfig = true)
public class DropboxFetch extends Job {

	public static void main(String[] args) {
		new DropboxFetch().runJob();
	}

	@Override
	public void runJob() {

		String accessToken = KVMemoryMap.getInstance().readValueFromKey("secureentry.account.dropbox.accessToken");
		String remotePath = KVMemoryMap.getInstance().readValueFromKey("application.dropboxfetch.remotePath");
		String localPath = KVMemoryMap.getInstance().readValueFromKey("application.dropboxfetch.localPath");
		int minFileStaytime = Integer.parseInt(KVMemoryMap.getInstance().readValueFromKey("application.dropboxfetch.minFileStaytime"));

		if (StringUtils.isEmpty(accessToken)) {
			return;
		}

		try {

			DbxRequestConfig config = new DbxRequestConfig("Files");
			DbxClientV2 clientV2 = new DbxClientV2(config, accessToken);

			processFolderV2API(remotePath, remotePath, clientV2, localPath, minFileStaytime);

		} catch (Exception e) {
			throw new RuntimeException("DropboxFetch failed:", e);
		}

	}

	private void processFolderV2API(String targetRoot, String path, DbxClientV2 client, String destRoot, int minFileStaytime)
			throws Exception {

		ListFolderResult result = client.files().listFolder(path);

		while (true) {

			for (Metadata metadata : result.getEntries()) {
				if (metadata instanceof FolderMetadata) {

					FolderMetadata folder = (FolderMetadata) metadata;
					processFolderV2API(targetRoot, folder.getPathLower(), client, destRoot, minFileStaytime);

				} else if (metadata instanceof FileMetadata) {

					FileMetadata file = (FileMetadata) metadata;
					Date lastModified = file.getClientModified().after(file.getServerModified()) ? file.getClientModified()
							: file.getServerModified();
					if (System.currentTimeMillis() - lastModified.getTime() > 1000 * 60 * minFileStaytime) {
						// min 30 minutes

						String destPath = StringUtils.removeEnd(file.getPathDisplay(), file.getName());
						destPath = StringUtils.removeStart(destPath, targetRoot);
						destPath = destRoot + destPath;
						FilesFile destPathFile = new FilesFile(destPath);
						destPathFile.mkdirs();
						FilesFile destFile = new FilesFile(destPath + file.getName());
						FileOutputStream outputStream = new FileOutputStream(destFile);
						client.files().downloadBuilder(file.getPathLower()).download(outputStream);
						outputStream.flush();
						outputStream.close();
						client.files().delete(file.getPathLower());
					}
				}
			}

			if (!result.getHasMore()) {
				break;
			}

			result = client.files().listFolderContinue(result.getCursor());
		}
	}
}
