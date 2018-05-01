package mfi.files.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.PrintCommandListener;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

public class SFTPClient {

	private FTPSClient client = null;

	public void uploadFile(String host, String user, String pass, File file, String remotedir, boolean debugMode) throws IOException {

		connect(host, user, pass, debugMode);

		uploadFile(file, remotedir);

		disconnect();
	}

	public void uploadFile(File file, String remotedir) throws IOException {

		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);

			cwd("/");
			dirs(remotedir);

			stor(fis, file.getName(), file.length());

		} finally {
			if (fis != null) {
				fis.close();
			}
		}
	}

	public void connect(String host, String user, String pass, boolean debugMode) throws IOException {

		if (client != null) {
			throw new IOException("already connected. Disconnect first.");
		}

		client = new FTPSClient("SSL");
		if (debugMode) {
			client.addProtocolCommandListener(new PrintCommandListener(new PrintWriter(System.out)));
		}
		client.connect(host);

		int replyConnect = client.getReplyCode();
		if (!FTPReply.isPositiveCompletion(replyConnect)) {
			client.disconnect();
			throw new IOException("FTP server refused connection: " + replyConnect);
		}

		int replyUser = client.user(user);
		if (!FTPReply.isPositiveCompletion(replyConnect)) {
			client.disconnect();
			throw new IOException("Error set user: " + replyUser);
		}

		int replyPass = client.pass(pass);
		if (!FTPReply.isPositiveCompletion(replyPass)) {
			client.disconnect();
			throw new IOException("Error set pass: " + replyPass);
		}

	}

	public boolean disconnect() throws IOException {
		try {
			client.disconnect();
		} catch (Exception e) {
			return false;
		} finally {
			client = null;
		}
		return true;
	}

	private void dirs(String dirs) throws IOException {

		String[] parts = StringUtils.splitByWholeSeparator(dirs, "/");
		for (String part : parts) {
			if (StringUtils.isNotBlank(part)) {
				mkdir(part);
				cwd(part);
			}
		}
	}

	private void cwd(String dir) throws IOException {

		int reply = client.cwd(dir);

		if (reply != 250) {
			throw new IOException("unexcepted response code for 'cwd': " + reply);
		}
	}

	private void stor(InputStream inputStream, String filename, long size) throws IOException {

		client.enterLocalPassiveMode();

		boolean completed = client.storeFile(filename, inputStream);
		if (!completed) {
			throw new IOException("unexcepted response code for 'stor'");
		}
		inputStream.close();
	}

	private void mkdir(String dir) throws IOException {

		int reply = client.mkd(dir);

		if (reply != 257 && reply != 550) {
			throw new IOException("unexcepted response code for 'mkdir': " + reply);
		}
	}

}