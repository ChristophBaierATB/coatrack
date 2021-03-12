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
import eu.coatrack.proxy.security.LocalApiKeyValidityVerifier;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class LocalApiKeyValidityVerifierTest {

    private final String someValidApiKeyValue = "someValidApiKeyValue";

    private ApiKey apiKey;
    private List<String> apiKeyValueList;
    private ResponseEntity<ApiKey> responseEntity;
    private LocalApiKeyValidityVerifier verifier = new LocalApiKeyValidityVerifier();

    private final long oneHourInMillis = 1000 * 60 * 60;
    private final long oneDayInMillis = oneHourInMillis * 24;
    private final Timestamp now = new Timestamp(System.currentTimeMillis());
    private final Timestamp tomorrow = new Timestamp(now.getTime() + oneDayInMillis);
    private final Timestamp yesterday = new Timestamp(now.getTime() - oneDayInMillis);
    private final Timestamp twoHoursAgo = new Timestamp(now.getTime() - oneHourInMillis * 2);
    private final Timestamp halfAnHourAgo = new Timestamp(now.getTime() - oneHourInMillis / 2);

    @BeforeEach
    public void createAnAcceptingDefaultSetup(){
        buildUpApiKey();
        buildUpVerifier();
        buildUpResponseEntity();
    }

    private void buildUpApiKey() {
        apiKey = new ApiKey();
        apiKey.setKeyValue(someValidApiKeyValue);
        apiKey.setDeletedWhen(null);
        apiKey.setValidUntil(tomorrow);
    }

    private void buildUpVerifier() {
        apiKeyValueList = new ArrayList<>();
        apiKeyValueList.add(someValidApiKeyValue);
        verifier.setApiKeyList(apiKeyValueList);
        verifier.setAdminsLocalTime(now);
        verifier.setLastApiKeyValueListUpdate(now);
    }

    private void buildUpResponseEntity() {
        responseEntity = new ResponseEntity<>(apiKey, HttpStatus.OK);
    }

    @Test
    public void isDefaultKeyAccepted(){
        assertTrue(verifier.doesResultValidateApiKey(responseEntity, someValidApiKeyValue));
    }

    @Test
    public void testIfDeletedKeyIsDenied(){
        apiKey.setDeletedWhen(yesterday);
        assertFalse(verifier.doesResultValidateApiKey(responseEntity, someValidApiKeyValue));
        apiKey.setDeletedWhen(halfAnHourAgo);
        assertFalse(verifier.doesResultValidateApiKey(responseEntity, someValidApiKeyValue));
    }

    @Test
    public void testIfExpiredKeyIsDenied(){
        apiKey.setValidUntil(yesterday);
        assertFalse(verifier.doesResultValidateApiKey(responseEntity, someValidApiKeyValue));
        apiKey.setValidUntil(halfAnHourAgo);
        assertFalse(verifier.doesResultValidateApiKey(responseEntity, someValidApiKeyValue));
    }

    @Test
    public void testIfAKeyWhichWasRejectedByAdminIsDenied(){
        apiKey = null;
        responseEntity = new ResponseEntity<>(apiKey, HttpStatus.OK);
        assertFalse(verifier.doesResultValidateApiKey(responseEntity, someValidApiKeyValue));
    }

    @Test
    public void testIfApiKeyIsAcceptedSinceItIsInTheLocalApiKeyList(){
        activateTestOfLocalApiKeyList();
        assertTrue(verifier.doesResultValidateApiKey(responseEntity, someValidApiKeyValue));
    }

    @Test
    public void testIfApiKeyIsDeniedBecauseOfEmptyLocalApiKeyList(){
        activateTestOfLocalApiKeyList();
        verifier.setApiKeyList(new ArrayList<>());
        assertFalse(verifier.doesResultValidateApiKey(responseEntity, someValidApiKeyValue));
    }

    @Test
    public void testIfApiKeyIsDeniedBecauseAdminIsNotReachableForMoreThanOneHour(){
        activateTestOfLocalApiKeyList();
        verifier.setLastApiKeyValueListUpdate(yesterday);
        assertFalse(verifier.doesResultValidateApiKey(responseEntity, someValidApiKeyValue));
        verifier.setLastApiKeyValueListUpdate(twoHoursAgo);
        assertFalse(verifier.doesResultValidateApiKey(responseEntity, someValidApiKeyValue));
    }

    @Test
    public void testIfApiKeyIsAcceptedBecauseAdminIsNotReachableForLessThanOneHour(){
        activateTestOfLocalApiKeyList();
        verifier.setLastApiKeyValueListUpdate(halfAnHourAgo);
        assertTrue(verifier.doesResultValidateApiKey(responseEntity, someValidApiKeyValue));
    }

    public void activateTestOfLocalApiKeyList(){
        responseEntity = null;
    }
}
