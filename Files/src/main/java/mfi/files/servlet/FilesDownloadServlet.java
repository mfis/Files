package mfi.files.servlet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import mfi.files.helper.ServletHelper;
import mfi.files.helper.ThreadLocalHelper;
import mfi.files.htmlgen.HTMLUtils;
import mfi.files.io.FilesFile;
import mfi.files.logic.Security;
import mfi.files.maps.KVMemoryMap;
import mfi.files.model.Condition;
import mfi.files.model.Model;

@Controller
public class FilesDownloadServlet {

	public static final String DOWNLOADTOKEN = "xtoken.download.";
	static final int BUFFER_SIZE = 16384;
	public static final String SERVLETPFAD = "/FilesDownloadServlet";
	private static Log logger = LogFactory.getLog(FilesDownloadServlet.class);

	public final static String FORCE_DOWNLOAD = "forceDownload";

	@RequestMapping("/FilesDownloadServlet") // NOSONAR
	public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception { // NOSONAR

		try {

			ThreadLocalHelper.setConversationID(request.getParameter(HTMLUtils.CONVERSATION));
			HttpSession session = request.getSession(false);
			Model model = Security.lookupModelFromSession(session, request);
			Map<String, String> parameters = ServletHelper.parseRequest(request, session, Condition.FILE_DOWNLOAD_DEFAULT);
			Security.checkSecurityForRequest(model, parameters);

			if (model == null || !model.isUserAuthenticated()) {
				streamErrorMessageTo(response, 403);
				return;
			}

			boolean forceDownload = StringUtils.equalsIgnoreCase(request.getParameter(FORCE_DOWNLOAD), "true");
			String token = StringUtils.trimToNull(request.getParameter("token"));
			String search = StringUtils.trimToNull(request.getParameter("search"));

			// [DOWNLOADTOKEN].ABC123XYZ.1273628883 = /my/file.txt
			List<String[]> list = KVMemoryMap.getInstance().readListWithPartKey(DOWNLOADTOKEN + token + ".");

			if (list.size() > 1) {
				throw new IllegalArgumentException("non-unique token:" + token);
			}

			if (list.isEmpty()) {
				// token not existing
				streamErrorMessageTo(response, 404);
				return;
			}

			long expire = Long.parseLong(list.get(0)[0]);

			if ((expire != 0 && expire < System.currentTimeMillis())) {
				// token expired
				streamErrorMessageTo(response, 403);
				return;
			}

			FilesFile file = new FilesFile(list.get(0)[1]);
			if (!file.exists()) {
				// file not existing
				streamErrorMessageTo(response, 404);
				return;
			}

			if (file.isDirectory() && search != null) {
				file = FilesFile.lookupFileIgnoreCase(file.getAbsolutePath() + "/" + search, true, null);
				if (file == null) {
					// file search not successful
					streamErrorMessageTo(response, 404);
					return;
				}
			}

			if (!file.isFile()) {
				// file not a file (e.g. directory)
				streamErrorMessageTo(response, 404);
				return;
			}

			if (file.isServerCryptedDirectPassword() || file.isServerCryptedHashedPassword()) {

				try {
					FilesFile fileFromModel = model.lookupConversation(Integer.parseInt(request.getParameter(HTMLUtils.CONVERSATION)))
							.getEditingFile();
					file.passwordsFromOtherFile(fileFromModel);
					ThreadLocalHelper.setModelPassword(Security.generateModelPasswordForSession(model));

				} catch (Exception e) {
					streamErrorMessageTo(response, 403);
					return;
				}
			}

			streamFileTo(response, file, forceDownload);

		} catch (Exception e) {

			logger.error("Fehler beim Download:", e);
			streamErrorMessageTo(response, 500);

		} finally {

			ThreadLocalHelper.unset();
		}

	}

	private void streamErrorMessageTo(HttpServletResponse response, int errorcode) throws IOException {

		response.setContentType("text/html");
		response.setCharacterEncoding("UTF-8");
		response.setStatus(errorcode);
		PrintWriter out = response.getWriter();

		String msg = errorcode + " - ";
		switch (errorcode) {
		case 403:
			msg = msg + "forbidden";
			break;
		case 404:
			msg = msg + "file not found";
			break;
		case 500:
			msg = msg + "server error";
			break;
		default:
			msg = msg + "something is wrong";
		}

		out.println("<!DOCTYPE html PUBLIC '-//W3C//DTD HTML 4.01//DE' 'http://www.w3.org/TR/html4/strict.dtd'>");
		out.println("<html>");
		out.println("<body><table><tr><td>");
		out.println(msg);
		out.println("</td></tr></table></body>");
		out.println("</html>");

		out.flush();
		out.close();
	}

	private void streamFileTo(HttpServletResponse response, FilesFile file, boolean forceDownload)
			throws IOException, FileNotFoundException {

		long len = 0;
		String suffix = null;
		if (file.isReadable()) {
			len = file.dateiGroesseEntschluesselt();
			suffix = "";
		} else {
			len = file.length();
			suffix = "." + FilesFile.TYPE_SUFFIX_DOWNLOADED_CRYPTO_FILE + StringUtils.substringAfterLast(file.getName(), ".").toLowerCase();
		}

		response.setContentLength((int) len);

		String mimeType = ServletHelper.getMimeTypeForFileSuffix(file.dateinamenSuffix().toLowerCase());
		if (!forceDownload && mimeType != null) {
			response.setContentType(mimeType);
		} else {
			response.setContentType("application/octet-stream");
		}

		response.setHeader("Cache-Control", "no-cache");
		response.setHeader("Accept-Ranges", "none");

		if (forceDownload) {
			response.setHeader("Content-Disposition",
					"attachment; filename=" + URLEncoder.encode(file.dateiNameKlartext() + suffix, "UTF-8"));
			response.setHeader("Content-Description", "File Transfer");
			response.setHeader("Content-Transfer-Encoding", "binary");
			response.setHeader("Expires", "0");
		}

		OutputStream out = response.getOutputStream();
		file.readIntoOutputStream(out);
		out.flush();
		out.close();
	}

}
