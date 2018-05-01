package mfi.files.servlet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mfi.files.helper.ServletHelper;
import mfi.files.logic.Security;
import mfi.files.maps.KVMemoryMap;

public class FilesAuthenticationServlet extends HttpServlet {

	private static Logger logger = LoggerFactory.getLogger(FilesAuthenticationServlet.class);

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		handleRequest(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		handleRequest(request, response);
	}

	private void handleRequest(HttpServletRequest request, HttpServletResponse response) throws FileNotFoundException, IOException {

		String user = StringUtils.trimToEmpty(request.getParameter("user"));
		String pass = StringUtils.trimToEmpty(request.getParameter("pass"));
		String getSecretForUser = StringUtils.trimToEmpty(request.getParameter("getSecretForUser"));

		StringBuilder sbLog = new StringBuilder();
		sbLog.append("request for:" + user + StringUtils.rightPad("", pass.length(), '*') + ": ");

		response.setContentType("text/plain");
		response.setContentType(ServletHelper.CONTENT_TYPE_HTML);
		request.setCharacterEncoding(ServletHelper.STRING_ENCODING_UTF8);
		response.setCharacterEncoding(ServletHelper.STRING_ENCODING_UTF8);
		response.setHeader("Cache-Control", "no-cache");

		boolean userAuthenticated = checkUser(user, pass);

		if (userAuthenticated) {
			response.setStatus(200); // OK
			if (StringUtils.isNotBlank(getSecretForUser)) {
				String canReadSecretFor = KVMemoryMap.getInstance().readValueFromKey("user." + user + ".canReadSecretFor");
				if (StringUtils.isNotBlank(canReadSecretFor)) {
					List<String> allowedSecrets = Arrays.asList(StringUtils.split(canReadSecretFor, ","));
					if (allowedSecrets.contains(getSecretForUser)) {
						String secret = KVMemoryMap.getInstance()
								.readValueFromKey(KVMemoryMap.PREFIX_CRYPTO_ENTRY_DEC + "user." + getSecretForUser + ".secret");
						response.setContentLength(secret.length());
						PrintWriter out = response.getWriter();
						out.write(secret);
						out.flush();
					}
				}
			}
		} else {
			sbLog.append(" wrong password! - response 401");
			logger.info(sbLog.toString());
			response.setStatus(401); // Unauthorized
		}
	}

	private boolean checkUser(String user, String pass) {

		if (StringUtils.isBlank(user) || StringUtils.isBlank(pass)) {
			return false;
		}

		try {
			return Security.checkUserCredentials(user, pass);
		} catch (Exception e) {
			logger.error("Error while Authentication: ", e);
			return false;
		}
	}
}
