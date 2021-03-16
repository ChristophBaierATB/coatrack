package eu.coatrack.proxy.security;

/*-
 * #%L
 * coatrack-proxy
 * %%
 * Copyright (C) 2013 - 2021 Corizon | Institut für angewandte Systemtechnik Bremen GmbH (ATB)
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import eu.coatrack.api.ApiKey;
import eu.coatrack.api.ServiceApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Service
public class AdminCommunicator {

    private static final Logger log = LoggerFactory.getLogger(eu.coatrack.proxy.security.AdminCommunicator.class);

    @Autowired
    private LocalApiKeyAndServiceApiManager localApiKeyAndServiceApiManager;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private SecurityUtil securityUtil;

    @Value("${proxy-id}")
    private String gatewayId = "";

    @Value("${ygg.admin.api-base-url}")
    private String adminBaseUrl;

    @Value("${ygg.admin.resources.search-api-key-list}")
    private String adminResourceToSearchForApiKeyList;

    @Value("${ygg.admin.resources.search-api-keys-by-token-value}")
    private String adminResourceToSearchForApiKeys;

    String apiKeyListRequestUrl;
    String apiKeyUrlWithoutApiKeyValue;

    @PostConstruct
    private void initUrls(){
        apiKeyListRequestUrl = securityUtil.attachGatewayApiKeyToUrl(adminBaseUrl + adminResourceToSearchForApiKeyList);
        apiKeyUrlWithoutApiKeyValue = securityUtil.attachGatewayApiKeyToUrl(adminBaseUrl + adminResourceToSearchForApiKeys);
    }

    //TODO: Provide serviceApi from admin

    @Async
    @PostConstruct
    @Scheduled(fixedRate = 5000)
    public void requestLatestApiKeyListFromAdmin(){
        log.debug("Trying to receive an update of local API key list by requesting admin.");
        ResponseEntity<ApiKey[]> responseEntity = null;
        try {
            responseEntity = restTemplate.getForEntity(apiKeyListRequestUrl, ApiKey[].class, gatewayId);
        } catch (Exception e) {
            log.info("Connection to admin server failed. Probably the server is temporarily down.");
        }

        if (responseEntity == null)
            return;
        checkAndLogHttpStatus(responseEntity.getStatusCode());

        List<ApiKey> apiKeyList = Arrays.asList(responseEntity.getBody());
        if (apiKeyList != null)
            localApiKeyAndServiceApiManager.updateLocalApiKeyList(apiKeyList, LocalDateTime.now());
    }

    private void checkAndLogHttpStatus(HttpStatus statusCode) {
        if (statusCode == HttpStatus.OK)
            log.debug("Successfully requested API key list from admin.");
        else
            log.warn("Received http status " + statusCode + " from admin. This should not have happened.");
    }

    public boolean isApiKeyVerifiedByAdmin(String apiKeyValue) throws Exception {
        log.debug("Requesting API key with the value " + apiKeyValue + " from admin and checking its validity.");
        ResponseEntity<ApiKey> responseEntity = restTemplate.getForEntity(apiKeyUrlWithoutApiKeyValue + apiKeyValue, ApiKey.class);

        if (responseEntity.getStatusCode() == HttpStatus.OK)
            return true;
        else {
            log.info("This API key value is declared invalid by admin: " + apiKeyValue);
            return false;
        }
    }

    public ServiceApi requestServiceApiFromAdmin(){
        return null;
    }

}
