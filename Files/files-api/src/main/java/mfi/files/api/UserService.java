package mfi.files.api;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    @Qualifier(FilesApiConfiguration.FILES_API_REST_TEMPLATE)
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
        return call(user, pass, null, DeviceType.NONE, SecretType.PASSWORD, authURL, false).isCheckOk();
    }

    public boolean checkPin(String user, String pin) {
        return call(user, pin, null, DeviceType.NONE, SecretType.PIN, authURL, false).isCheckOk();
    }

    public TokenResult checkToken(String user, String token, String device, DeviceType deviceTypeboolean, boolean refresh) {
        return call(user, token, device, deviceTypeboolean, SecretType.TOKEN, tokenCheckURL, refresh);
    }

    public boolean deleteToken(String user, String device, DeviceType deviceType) {
        return call(user, null, device, deviceType, SecretType.NONE, tokenDeleteURL, false).isCheckOk();
    }

    public TokenResult createToken(String user, String pass, String device, DeviceType deviceType) {
        return call(user, pass, device, deviceType, SecretType.PASSWORD, tokenCreationURL, false);
    }


    private TokenResult call(String user, String secret, String device, DeviceType deviceType, SecretType type, String url,
            boolean refresh) {

        HttpHeaders headers = createHeaders();
        MultiValueMap<String, String> map = createParameters(user, secret, device, deviceType, type, refresh);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(url, request, String.class);
            boolean ok = responseEntity.getStatusCode().is2xxSuccessful();
            String newToken = null;
            if (ok) {
                newToken = responseEntity.getBody();
            }
            return new TokenResult(ok, newToken);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                return new TokenResult(false, null);
            } else {
                logger.error("Checking authentication not successful.(#1)", e);
            }
        } catch (Exception e) {
            logger.error("Checking authentication not successful.(#2)", e);
        }
        return new TokenResult(false, null);
    }

    private MultiValueMap<String, String> createParameters(String user, String secret, String device, DeviceType deviceType,
            SecretType type,
            boolean refresh) {

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();

        map.add("user", user);

        if (secret != null) {
            map.add(type.parameterName, secret);
        }

        map.add("application", applicationIdentifier);

        if (deviceType == DeviceType.APP) {
            map.add("device", device);
        } else if (deviceType == DeviceType.BROWSER) {
            map.add("user-agent", device);
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
