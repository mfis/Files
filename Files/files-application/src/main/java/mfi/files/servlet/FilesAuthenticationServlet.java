package mfi.files.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import mfi.files.api.TokenResult;
import mfi.files.helper.ServletHelper;
import mfi.files.logic.Security;
import mfi.files.maps.KVMemoryMap;

@Controller
public class FilesAuthenticationServlet {

    private static final String PARAM_USERAGENT = "user-agent";
	private static final String PARAM_SECRET = "getSecretForUser";
	private static final String PARAM_PIN = "pin";
	private static final String PARAM_REFRESH = "refresh";
	private static final String PARAM_TOKEN = "token";
	private static final String PARAM_DEVICE = "device";
	private static final String PARAM_APPLICATION = "application";
	private static final String PARAM_PASS = "pass";
	private static final String PARAM_USER = "user";

	private static Log logger = LogFactory.getLog(FilesAuthenticationServlet.class);

	@PostMapping("/FilesCreateToken")
	public void filesCreateToken(HttpServletRequest request, HttpServletResponse response) throws IOException {

		String user = StringUtils.trimToEmpty(request.getParameter(PARAM_USER));
		String pass = StringUtils.trimToEmpty(request.getParameter(PARAM_PASS));
		String application = StringUtils.trimToEmpty(request.getParameter(PARAM_APPLICATION));
		String device = lookupDevice(request);

		setResponseParameters(request, response);

		TokenResult tokenResult = createToken(user, pass, application, device);

		if (tokenResult.isCheckOk()) {
			response.setStatus(200); // OK
			PrintWriter out = response.getWriter();
			out.write(tokenResult.getNewToken());
			out.flush();
		} else {
			StringBuilder sbLog = new StringBuilder();
			sbLog.append("FilesCreateToken:" + user + StringUtils.rightPad("", pass.length(), '*') + ": ");
			sbLog.append(" wrong password! - response 401");
			logger.info(sbLog.toString());
			response.setStatus(401); // Unauthorized
		}
	}

	@PostMapping("/FilesCheckToken")
	public void filesCheckToken(HttpServletRequest request, HttpServletResponse response) throws IOException {

		String user = StringUtils.trimToEmpty(request.getParameter(PARAM_USER));
		String token = StringUtils.trimToEmpty(request.getParameter(PARAM_TOKEN));
		String application = StringUtils.trimToEmpty(request.getParameter(PARAM_APPLICATION));
		String device = lookupDevice(request);
		boolean refresh = BooleanUtils.toBoolean(StringUtils.trimToEmpty(request.getParameter(PARAM_REFRESH)));

		setResponseParameters(request, response);

		TokenResult checkTokenResult = ckeckToken(user, token, application, device, refresh);

		if (checkTokenResult.isCheckOk()) {
			response.setStatus(200); // OK
			if (refresh) {
				PrintWriter out = response.getWriter();
				out.write(checkTokenResult.getNewToken());
				out.flush();
			}
		} else {
			StringBuilder sbLog = new StringBuilder();
            sbLog.append("FilesCheckToken:" + user + " / " + StringUtils.substring(token, 0, 80));
			sbLog.append(" wrong token! - response 401");
			logger.info(sbLog.toString());
			response.setStatus(401); // Unauthorized
		}
	}

	@PostMapping("/FilesDeleteToken")
	public void filesDeleteToken(HttpServletRequest request, HttpServletResponse response) throws IOException {

		String user = StringUtils.trimToEmpty(request.getParameter(PARAM_USER));
		String application = StringUtils.trimToEmpty(request.getParameter(PARAM_APPLICATION));
		String device = lookupDevice(request);

		setResponseParameters(request, response);

		deleteToken(user, application, device);

		response.setStatus(200); // OK
	}

	@PostMapping("/FilesAuthentication")
	public void filesAuthentication(HttpServletRequest request, HttpServletResponse response) throws IOException {

		String user = StringUtils.trimToEmpty(request.getParameter(PARAM_USER));
		String pass = StringUtils.trimToEmpty(request.getParameter(PARAM_PASS));
		String pin = StringUtils.trimToEmpty(request.getParameter(PARAM_PIN));
		String getSecretForUser = StringUtils.trimToEmpty(request.getParameter(PARAM_SECRET));
		String application = StringUtils.trimToEmpty(request.getParameter(PARAM_APPLICATION));

		boolean isPin = StringUtils.isNotBlank(pin) && StringUtils.isBlank(pass);

		setResponseParameters(request, response);

		boolean userAuthenticated;
		if (isPin) {
			userAuthenticated = checkPin(user, pin);
		} else {
			userAuthenticated = checkPass(user, pass, application);
		}

		if (userAuthenticated) {
			response.setStatus(200); // OK
			if (!isPin && StringUtils.isNotBlank(getSecretForUser)) {
                String canReadSecretFor =
                    KVMemoryMap.getInstance().readValueFromKey(KVMemoryMap.KVDB_USER_IDENTIFIER + user + ".canReadSecretFor");
				if (StringUtils.isNotBlank(canReadSecretFor)) {
					List<String> allowedSecrets = Arrays.asList(StringUtils.split(canReadSecretFor, ","));
					if (allowedSecrets.contains(getSecretForUser)) {
						String secret = KVMemoryMap.getInstance()
                            .readValueFromKey(KVMemoryMap.PREFIX_CRYPTO_ENTRY_DEC + KVMemoryMap.KVDB_USER_IDENTIFIER
                                + getSecretForUser + ".secret");
						response.setContentLength(secret.length());
						PrintWriter out = response.getWriter();
						out.write(secret);
						out.flush();
					}
				}
			}
		} else {
			StringBuilder sbLog = new StringBuilder();
			sbLog.append("request for:" + user + StringUtils.rightPad("", pass.length(), '*') + ": ");
			sbLog.append(" wrong password! - response 401");
			logger.info(sbLog.toString());
			response.setStatus(401); // Unauthorized
		}
	}

	public String lookupDevice(HttpServletRequest request) {

		String device = StringUtils.trimToEmpty(request.getParameter(PARAM_DEVICE));
		if (StringUtils.isNotBlank(device)) {
			return device;
		}
        return Security.deviceFromUserAgent(request.getParameter(PARAM_USERAGENT));
	}

	public void setResponseParameters(HttpServletRequest request, HttpServletResponse response) throws UnsupportedEncodingException {

		response.setContentType("text/plain");
		response.setContentType(ServletHelper.CONTENT_TYPE_HTML);
		request.setCharacterEncoding(ServletHelper.STRING_ENCODING_UTF8);
		response.setCharacterEncoding(ServletHelper.STRING_ENCODING_UTF8);
		response.setHeader("Cache-Control", "no-cache");
	}

	private boolean checkPass(String user, String pass, String application) {

		if (StringUtils.isBlank(user) || StringUtils.isBlank(pass)) {
			return false;
		}

		try {
			boolean credentialsOk = Security.checkUserCredentials(user, pass);

			if (credentialsOk && StringUtils.isNotBlank(application)) {
                return Security.checkAllowedApplication(user, application);
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

	public TokenResult createToken(String user, String pass, String application, String device) {

		if (StringUtils.isBlank(user) || StringUtils.isBlank(pass) || StringUtils.isBlank(application) || StringUtils.isBlank(device)) {
			return new TokenResult(false, null);
		}

		if (!checkPass(user, pass, application)) {
			return new TokenResult(false, null);
		}

		return Security.createToken(user, pass, application, device);
	}

	private TokenResult ckeckToken(String user, String token, String application, String device, boolean refresh) {

		if (StringUtils.isBlank(user) || StringUtils.isBlank(token) || StringUtils.isBlank(application) || StringUtils.isBlank(device)) {
			return new TokenResult(false, null);
		}

		try {
			TokenResult checkTokenResult = Security.checkToken(user, token, application, device, refresh);
            if (checkTokenResult.isCheckOk() && refresh && StringUtils.isBlank(checkTokenResult.getNewToken())) {
				throw new IllegalStateException("failed creating new token");
			}
			return checkTokenResult;

		} catch (Exception e) {
			logger.error("Error while TOKEN check: ", e);
			return new TokenResult(false, null);
		}
	}

	private boolean deleteToken(String user, String application, String device) {

		if (StringUtils.isBlank(user) || StringUtils.isBlank(application) || StringUtils.isBlank(device)) {
			return false;
		}

		Security.deleteToken(user, application, device);
		return true;

	}
}
