package mfi.files.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class FilesApiConfiguration {

    public static final String FILES_API_REST_TEMPLATE = "filesApiRestTemplate";

    @Bean(name = FILES_API_REST_TEMPLATE)
    public RestTemplate restTemplateFilesApi() {
        HttpComponentsClientHttpRequestFactory httpRequestFactory = new HttpComponentsClientHttpRequestFactory();
        httpRequestFactory.setConnectionRequestTimeout(2000);
        httpRequestFactory.setConnectTimeout(2000);
        httpRequestFactory.setReadTimeout(2000);
        return new RestTemplate(httpRequestFactory);
    }
}
