package mfi.files.servlet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

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

public class FilesDownloadServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	static final int BUFFER_SIZE = 16384;
	public static final String SERVLETPFAD = "/Files/FilesDownloadServlet";
	private static Logger logger = LoggerFactory.getLogger(FilesDownloadServlet.class);

	public static String FORCE_DOWNLOAD = "forceDownload";

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			handleRequest(request, response);
		} catch (Exception e) {
			throw new ServletException("Fehler bei Request-Verarbeitung in FilesDownloadServlet:", e);
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			handleRequest(request, response);
		} catch (Exception e) {
			throw new ServletException("Fehler bei Request-Verarbeitung in FilesDownloadServlet:", e);
		}
	}

	private void handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {

		try {

			boolean forceDownload = StringUtils.equalsIgnoreCase(request.getParameter(FORCE_DOWNLOAD), "true");
			String token = StringUtils.trimToNull(request.getParameter("token"));
			String search = StringUtils.trimToNull(request.getParameter("search"));

			// temporary.downloadtoken.ABC123XYZ.1273628883 = /my/file.txt
			List<String[]> list = KVMemoryMap.getInstance().readListWithPartKey("temporary.downloadtoken." + token + ".");

			if (list.size() > 1) {
				throw new IllegalArgumentException("non-unique token:" + token);
			}

			if (list.size() == 0) {
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
					HttpSession session = request.getSession(true);
					Model model = (Model) session.getAttribute(FilesMainServlet.SESSION_ATTRIBUTE_MODEL);
					FilesFile fileFromModel = model.lookupConversation(Integer.parseInt(request.getParameter(HTMLUtils.CONVERSATION)))
							.getEditingFile();
					file.passwordsFromOtherFile(fileFromModel);
					ThreadLocalHelper.setModelPassword(Security.generateModelPasswordForSession(model));

				} catch (Exception e) {
					streamErrorMessageTo(response, 403);
					return;
				}

				// if (!file.isReadable()) {
				// streamErrorMessageTo(response, 403);
				// return;
				// }

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
			suffix = "." + FilesFile.TYPE_SUFFIX_DOWNLOADED_CRYPTO_FILE
					+ StringUtils.substringAfterLast(file.getName(), ".").toLowerCase();
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
