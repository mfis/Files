package mfi.files.jobs;

import java.util.List;
import java.util.StringTokenizer;

import org.springframework.stereotype.Component;

import mfi.files.annotation.FilesJob;
import mfi.files.io.FilesFile;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Job;

@Component
@FilesJob(cron = "4,14,24,34,44,54 5-23 * * *", failureCountUntilStartLogging = 2, hasCryptoConfig = false)
public class DownloadTokenCleanUp extends Job {

	@Override
	public void runJob() {

		long now = System.currentTimeMillis();
		// temporary.downloadtoken.ABC123XYZ.1273628883 = /my/file.txt
		List<String[]> list = KVMemoryMap.getInstance().readListWithPartKey("temporary.downloadtoken.");
		for (String[] strings : list) {
			StringTokenizer tokenizer = new StringTokenizer(strings[0], ".", false);
			tokenizer.nextToken(); // token selbst wird hier nicht benoetigt
			long expire = Long.parseLong(tokenizer.nextToken());
			if ((expire != 0 && expire < now) || !(new FilesFile(strings[1])).exists()) {
				KVMemoryMap.getInstance().deleteKey("temporary.downloadtoken." + strings[0]);
			}
		}

	}
}
