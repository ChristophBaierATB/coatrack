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

import eu.coatrack.api.GatewayUpdate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
/**
 *  This bean regularly requests the admin server to provide the latest list of service APIs
 *  belonging to the gateway and their referring API keys. After new update content was
 *  received from the admin server, it is redirect to the GatewayUpdater.
 *
 *  @author Christoph Baier
 */

@EnableAsync
@EnableScheduling
@RestController
public class GatewayUpdateRequester {

    private static final Logger log = LoggerFactory.getLogger(GatewayUpdateRequester.class);

    private final int fiveMinutesInMillis = 1000 * 60 * 5;

    @Autowired
    private GatewayUpdater gatewayUpdater;

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

    private String url = "";

    @PostConstruct
    private void initUrlAndRequestApiKeyList(){
        String urlWithoutGatewayId = adminBaseUrl + adminResourceToSearchForApiKeyList;
        url = securityUtil.attachGatewayApiKeyToUrl(urlWithoutGatewayId);
        requestGatewayUpdateFromAdmin();
    }

    @Async
    @Scheduled(fixedRate = fiveMinutesInMillis)
    public void requestGatewayUpdateFromAdmin() {
        ResponseEntity<GatewayUpdate> responseEntity;
        try {
            responseEntity = restTemplate.getForEntity(url, GatewayUpdate.class, gatewayId);
        } catch (Exception e){
            log.info("Connection to admin server failed. Probably the server is temporarily down.");
            return;
        }
        checkAndLogHttpStatus(responseEntity.getStatusCode());

        GatewayUpdate gatewayUpdate = responseEntity.getBody();
        ifGatewayUpdateIsPresentExtractDataAndPerformUpdates(gatewayUpdate);
    }

    private void checkAndLogHttpStatus(HttpStatus statusCode) {
        if (statusCode == HttpStatus.OK)
            log.debug("GatewayUpdate was successfully requested and delivered.");
        else
            log.warn("Gateway is probably not recognized by admin, maybe it is deprecated. " +
                    "Please download and run a new one from coatrack.eu");
    }

    private void ifGatewayUpdateIsPresentExtractDataAndPerformUpdates(GatewayUpdate gatewayUpdate) {
        if(gatewayUpdate != null){
            gatewayUpdater.extractDataAndPerformUpdates(gatewayUpdate);
        }
    }
}
