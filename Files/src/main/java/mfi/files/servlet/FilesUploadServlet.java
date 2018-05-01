package mfi.files.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mfi.files.helper.ServletHelper;
import mfi.files.helper.ThreadLocalHelper;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.io.FilesFile;
import mfi.files.logic.Security;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Model;

public class FilesUploadServlet extends HttpServlet {

	private static Logger logger = LoggerFactory.getLogger(FilesUploadServlet.class);

	private static final long serialVersionUID = 1L;

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		response.setContentType("text/html");
		response.setCharacterEncoding(ServletHelper.STRING_ENCODING_UTF8);
		response.setHeader("Content-Encoding", "gzip");
		response.setStatus(500);
		OutputStream out = new GZIPOutputStream(response.getOutputStream());
		out.write("Upload nur via POST Request".getBytes(ServletHelper.STRING_ENCODING_UTF8));
		out.close();
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

		HttpSession session = request.getSession(true);
		Model model = (Model) session.getAttribute(FilesMainServlet.SESSION_ATTRIBUTE_MODEL);

		response.setContentType("text/plain");
		response.setCharacterEncoding("UTF-8");
		boolean formTerminator = false;
		boolean convIdIsSet = false;
		String password = null;
		try {
			int countFiles = 0;
			List<FileItem> items = new ServletFileUpload(new DiskFileItemFactory()).parseRequest(request);
			for (FileItem item : items) {
				// System.out.println(item.getFieldName() + "--" + item.getName() + "--" + (item.isFormField() ? item.getString() :
				// "FILE"));
				// convID--null--1
				// servercrypted[]--Archiv.zip--FILE
				// formTerminator--null--true
				if (item.isFormField()) {
					formTerminator = true;
					if (StringUtils.equals(item.getFieldName(), HTMLUtils.CONVERSATION)) {
						convIdIsSet = true;
						ThreadLocalHelper.setConversationID(item.getString());
						ThreadLocalHelper.setModelPassword(Security.generateModelPasswordForSession(model));
						Security.checkSecurityForRequest(model, request, session.getId(), false);
					}
					if (StringUtils.equals(item.getFieldName(), "hashedPassword")) {
						password = item.getString();
					}
				} else {
					if (!convIdIsSet) {
						throw new IllegalArgumentException("ConvID ist nicht gesetzt");
					}
					if (model == null) {
						throw new IllegalArgumentException("Model ist nicht gesetzt");
					}
					countFiles++;
					String fileName = item.getName();
					InputStream content = item.getInputStream();
					processUpload(item.getFieldName().equals("endtoendcrypted[]"), content, fileName,
							item.getFieldName().equals("servercrypted[]") ? password : null, model, response);
				}
			}
			if (formTerminator) {
				response.setStatus(200);
				if (countFiles == 1) {
					response.getWriter().print(countFiles + " Datei wurde hochgeladen.");
				} else {
					response.getWriter().print(countFiles + " Dateien wurden hochgeladen.");
				}
			} else {
				response.setStatus(500);
				response.getWriter().print("Uebertragungsfehler !!");
			}

		} catch (Exception e) {
			response.setStatus(500);
			response.getWriter().print("Interner Fehler !!");
			boolean convStat = model.lookupConversation() != null;
			boolean fileStat = false;
			if (convStat) {
				fileStat = model.lookupConversation().getEditingFile() != null;
			}
			logger.error("File Upload failed (" + convStat + "/" + fileStat + "):", e);
		} finally {
			try {
				if (model != null && convIdIsSet) {
					if (model.lookupConversation().getEditingFile().isPasswordPending()) {
						model.lookupConversation().getEditingFile().forgetPasswords();
					}
				}
			} catch (Exception ex) {
				logger.error("Fehler bei finally: ", ex);
			}
			ThreadLocalHelper.unset();
			response.flushBuffer();
		}

	}

	private void processUpload(boolean clientCrypto, InputStream inputStream, String fileName, String password, Model model,
			HttpServletResponse response) throws IOException {

		String dir;
		if (model.lookupConversation().getEditingFile().isDirectory()) {
			dir = model.lookupConversation().getEditingFile().getAbsolutePath() + FilesFile.separator;
		} else {
			dir = model.lookupConversation().getEditingFile().getParent() + FilesFile.separator;
		}

		FilesFile newBaseFile = new FilesFile(dir + fileName);
		FilesFile newFile = null;

		String suffix = "";
		boolean isUploadCryptedFilesFile = newBaseFile.isDownloadedCryptoFile();
		if (isUploadCryptedFilesFile) {
			newBaseFile = newBaseFile.lookupCryptoFileNameFromDownloadedCryptoFile();
			suffix = "." + FilesFile.TYPE_SUFFIX_DOWNLOADED_CRYPTO_FILE
					+ StringUtils.substringAfterLast(fileName, "." + FilesFile.TYPE_SUFFIX_DOWNLOADED_CRYPTO_FILE);
		}

		boolean fileNameOK = false;
		int counter = 0;
		do {
			String counterString = counter == 0 ? "" : ("-" + counter);
			counter++;
			String dec = newBaseFile.dateiNameKlartext();
			String end = StringUtils.substringAfterLast(dec, ".");
			String start = StringUtils.removeEnd(dec, "." + end);
			newFile = new FilesFile(newBaseFile.getParent() + "/" + start + counterString + "." + end + suffix);
			if (isUploadCryptedFilesFile) {
				newFile = newFile.lookupCryptoFileNameFromDownloadedCryptoFile();
			}
			if (!newFile.exists()) {
				fileNameOK = true;
			}

		} while (!fileNameOK);

		if (password != null && !isUploadCryptedFilesFile) {
			newFile.prospectivePassword(password);
		}

		boolean forceRawDataWriting = false;
		if (isUploadCryptedFilesFile) {
			forceRawDataWriting = true;
		} else if (clientCrypto) {
			newFile = newFile.clientseitigeVerschluesselungNachbereiten(null);
		} else if (newFile.isPasswordPending()) {
			newFile = newFile.verschluesseleDateiServerseitigHashedPassword(); // hier IMMER HashedPassword
		} else {

		}

		try {
			newFile.writeFromInputStream(inputStream, forceRawDataWriting);
			newFile.aktualisiereAenderer(model.getUser());
		} finally {
			newFile.forgetPasswords();
		}

		// Backup
		String home = KVMemoryMap.getInstance().readValueFromKey("user." + model.getUser() + ".homeDirectory");
		if (StringUtils.startsWith(newFile.getAbsolutePath(), home)) {
			newFile.backupFile(model);
		}
	}
}