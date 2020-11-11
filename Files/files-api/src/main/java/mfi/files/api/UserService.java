package mfi.files.api;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
public class UserService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${authenticationURL}")
    private String authURL;

    @Value("${tokenCreationURL}")
    private String tokenCreationURL;

    @Value("${tokenCheckURL}")
    private String tokenCheckURL;

    @Value("${tokenDeleteURL}")
    private String tokenDeleteURL;

    @Value("${application.identifier}")
    private String applicationIdentifier;

    private Log logger = LogFactory.getLog(UserService.class);

    public String userNameFromLoginCookie(String value) {
        return StringUtils.substringBefore(value, "*");
    }

    public boolean checkAuthentication(String user, String pass) {
        return checkCall(user, pass, null, SecretType.PASSWORD, authURL, false);
    }

    public boolean checkPin(String user, String pin) {
        return checkCall(user, pin, null, SecretType.PIN, authURL, false);
    }

    public boolean checkToken(String user, String token, String device, boolean refresh) {
        return checkCall(user, token, device, SecretType.TOKEN, tokenCheckURL, refresh);
    }

    public boolean deleteToken(String user, String device) {
        return checkCall(user, null, device, SecretType.NONE, tokenDeleteURL, false);
    }

    public String createAppToken(String user, String pass, String device) {

        HttpHeaders headers = createHeaders();
        MultiValueMap<String, String> map = createParameters(user, pass, device, SecretType.PASSWORD, false);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(tokenCreationURL, request, String.class);
            if(responseEntity.getStatusCode().is2xxSuccessful()) {
                return responseEntity.getBody();
            }
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return null;
            } else {
                logger.error("Checking authentication not successful.(#1)", e);
            }
        } catch (Exception e) {
            logger.error("Checking authentication not successful.(#2)", e);
        }
        return null;
    }

    private boolean checkCall(String user, String secret, String device, SecretType type, String url, boolean refresh) {

        HttpHeaders headers = createHeaders();
        MultiValueMap<String, String> map = createParameters(user, secret, device, type, refresh);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(url, request, String.class);
            return responseEntity.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return false;
            } else {
                logger.error("Checking authentication not successful.(#1)", e);
            }
        } catch (Exception e) {
            logger.error("Checking authentication not successful.(#2)", e);
        }
        return false;
    }

    private MultiValueMap<String, String> createParameters(String user, String secret, String device, SecretType type,
            boolean refresh) {

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("user", user);
        if (secret != null) {
            map.add(type.parameterName, secret);
        }
        map.add("application", applicationIdentifier);
        if (device != null) {
            map.add("device", device);
        }
        if (refresh) {
            map.add("refresh", Boolean.TRUE.toString());
        }
        return map;
    }

    private HttpHeaders createHeaders() {

        HttpHeaders headers = new HttpHeaders();
        headers.add("Accept", "*/*");
        headers.add("Cache-Control", "no-cache");
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    private enum SecretType {
        PASSWORD("pass"), PIN("pin"), TOKEN("token"), NONE("");

        private SecretType(String n) {
            parameterName = n;
        }

        private String parameterName;
    }
}
