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
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *  Locally stores and periodically refreshes API keys and their associated service APIs
 *  belonging to the gateway. Provides API key verification services for incoming consumer
 *  calls which work even if the CoatRack admin server is down.
 *
 *  @author Christoph Baier
 */

@Service
public class LocalApiKeyAndServiceApiManager {

    private static final Logger log = LoggerFactory.getLogger(LocalApiKeyAndServiceApiManager.class);
    private final int fiveMinutesInMillis = 1000 * 60 * 5;

    protected List<ApiKey> localApiKeyList = new ArrayList<>();
    protected LocalDateTime latestLocalApiKeyListUpdate = LocalDateTime.now();

    @Value("${minutes-the-gateway-works-without-connection-to-admin}")
    private long minutesTheGatewayWorksWithoutConnectionToAdmin;

    @Autowired
    private AdminCommunicator adminCommunicator;

    public boolean isApiKeyValidConsideringLocalApiKeyList(String apiKeyValue) {
        if(apiKeyValue == null){
            log.debug("The passed API key value is null and can therefore not be checked for validity.");
            return false;
        }

        ApiKey apiKey = findApiKeyFromLocalApiKeyList(apiKeyValue);
        if (apiKey == null){
            log.info("The API key with the value " + apiKeyValue + " could not be found within the local API key list.");
            return false;
        } else
            log.info("The API key with the value " + apiKeyValue + " matches an API key within the local API key list.");

        return isApiKeyNotDeleted(apiKey) && isApiKeyNotExpired(apiKey) && wasLatestUpdateOfLocalApiKeyListWithinTheGivenDeadline();
    }

    private boolean isApiKeyNotDeleted(ApiKey apiKey) {
        boolean isNotDeleted = apiKey.getDeletedWhen() == null;
        if (isNotDeleted){
            return true;
        } else {
            log.info("The API key with the value " + apiKey.getKeyValue() + " is deleted and therefore rejected.");
            return false;
        }
    }

    private boolean isApiKeyNotExpired(ApiKey apiKey) {
        boolean isNotExpired = apiKey.getValidUntil().getTime() > System.currentTimeMillis();
        if (isNotExpired){
            return true;
        } else {
            log.info("The API key with the value " + apiKey.getKeyValue() + " is expired and therefore rejected.");
            return false;
        }
    }

    private boolean wasLatestUpdateOfLocalApiKeyListWithinTheGivenDeadline() {
        boolean check = latestLocalApiKeyListUpdate.plusMinutes(minutesTheGatewayWorksWithoutConnectionToAdmin).isAfter(LocalDateTime.now());
        if (check == false)
            log.info("The CoatRack admin server was not reachable for longer than " + minutesTheGatewayWorksWithoutConnectionToAdmin
                    + " minutes. Since this predefined threshold was exceeded, this and all subsequent service " +
                    "API requests are rejected until a connection to CoatRack admin could be re-established.");
        return check;
    }

    public boolean isApiKeyReceivedFromAdminValid(ApiKey apiKey){
        return isApiKeyNotDeleted(apiKey) && isApiKeyNotExpired(apiKey);
    }

    public ServiceApi getServiceApiFromLocalList(String apiKeyValue){
        ApiKey apiKey = findApiKeyFromLocalApiKeyList(apiKeyValue);
        if (apiKey != null){
            return apiKey.getServiceApi();
        } else
            return null;
    }

    private ApiKey findApiKeyFromLocalApiKeyList(String apiKeyValue) {
        ApiKey apiKey;
        try {
            apiKey = localApiKeyList.stream().filter(apiKeyFromLocalList -> apiKeyFromLocalList.getKeyValue()
                    .equals(apiKeyValue)).findFirst().get();
        } catch (Exception e){
            log.info("The API key with the value " + apiKeyValue + " can not be found within the local API key list " +
                    "and is therefore rejected.");
            return null;
        }
        return apiKey;
    }

    @Async
    @PostConstruct
    @Scheduled(fixedRate = fiveMinutesInMillis)
    public void updateLocalApiKeyList() {
        ApiKey[] apiKeys;
        try {
            apiKeys = adminCommunicator.requestLatestApiKeyListFromAdmin();
        } catch (Exception e){
            log.info("Connection to admin server failed. Probably the server is temporarily down.");
            return;
        }
        localApiKeyList = Arrays.asList(apiKeys);
        latestLocalApiKeyListUpdate = LocalDateTime.now();
    }
}
