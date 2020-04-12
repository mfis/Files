package mfi.files.servlet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import mfi.files.helper.ServletHelper;
import mfi.files.logic.Security;
import mfi.files.maps.KVMemoryMap;

@Controller
public class FilesAuthenticationServlet {

	private static Log logger = LogFactory.getLog(FilesAuthenticationServlet.class);

	@RequestMapping("/FilesAuthenticationServlet")
	public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws FileNotFoundException, IOException {

		String user = StringUtils.trimToEmpty(request.getParameter("user"));
		String pass = StringUtils.trimToEmpty(request.getParameter("pass"));
		String pin = StringUtils.trimToEmpty(request.getParameter("pin"));
		String getSecretForUser = StringUtils.trimToEmpty(request.getParameter("getSecretForUser"));
		String application = StringUtils.trimToEmpty(request.getParameter("application"));

		boolean isPin = StringUtils.isNotBlank(pin) && StringUtils.isBlank(pass);

		StringBuilder sbLog = new StringBuilder();
		sbLog.append("request for:" + user + StringUtils.rightPad("", pass.length(), '*') + ": ");

		response.setContentType("text/plain");
		response.setContentType(ServletHelper.CONTENT_TYPE_HTML);
		request.setCharacterEncoding(ServletHelper.STRING_ENCODING_UTF8);
		response.setCharacterEncoding(ServletHelper.STRING_ENCODING_UTF8);
		response.setHeader("Cache-Control", "no-cache");

		boolean userAuthenticated;
		if (isPin) {
			userAuthenticated = checkPin(user, pin);
		} else {
			userAuthenticated = checkPass(user, pass, application);
		}

		if (userAuthenticated) {
			response.setStatus(200); // OK
			if (!isPin && StringUtils.isNotBlank(getSecretForUser)) {
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

	private boolean checkPass(String user, String pass, String application) {

		if (StringUtils.isBlank(user) || StringUtils.isBlank(pass)) {
			return false;
		}

		try {
			boolean credentialsOk = Security.checkUserCredentials(user, pass);

			if (credentialsOk && StringUtils.isNotBlank(application)) {
				String allowedApplications = StringUtils
						.trimToEmpty(KVMemoryMap.getInstance().readValueFromKey("user." + user + ".allowedApplications"));
				return Arrays.asList(StringUtils.split(allowedApplications, ",")).contains(application);
			}

			return credentialsOk;
		} catch (Exception e) {
			logger.error("Error while Authentication: ", e);
			return false;
		}
	}

	private boolean checkPin(String user, String pin) {

		if (StringUtils.isBlank(user) || StringUtils.isBlank(pin)) {
			return false;
		}

		try {
			return Security.checkPin(user, pin);
		} catch (Exception e) {
			logger.error("Error while PIN check: ", e);
			return false;
		}
	}
}
