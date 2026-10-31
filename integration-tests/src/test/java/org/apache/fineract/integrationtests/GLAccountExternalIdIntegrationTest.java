/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.integrationtests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.organisation.StaffHelper;
import org.apache.fineract.integrationtests.useradministration.roles.RolesHelper;
import org.apache.fineract.integrationtests.useradministration.users.UserHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Part A, behaviours 1 to 4, verified end to end through the REST API against a running Fineract:
 * <ol>
 * <li>the ERP external reference is persisted on the GL account and is unique;</li>
 * <li>same reference, identical payload returns the existing account (no duplicate);</li>
 * <li>same reference, different payload is rejected with HTTP 409;</li>
 * <li>the endpoint is protected by Fineract's permission model (CREATE_GLACCOUNT).</li>
 * </ol>
 */
public class GLAccountExternalIdIntegrationTest {

    private static final String GL_ACCOUNTS_URL = "/fineract-provider/api/v1/glaccounts?" + Utils.TENANT_IDENTIFIER;

    private RequestSpecification requestSpec;
    private ResponseSpecification ok;
    private ResponseSpecification conflict;
    private ResponseSpecification forbidden;

    @BeforeEach
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.ok = new ResponseSpecBuilder().expectStatusCode(200).build();
        this.conflict = new ResponseSpecBuilder().expectStatusCode(409).build();
        this.forbidden = new ResponseSpecBuilder().expectStatusCode(403).build();
    }

    private static String payload(final String externalId, final String glCode, final String name) {
        final Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("glCode", glCode);
        map.put("type", 1);
        map.put("usage", 1);
        map.put("manualEntriesAllowed", true);
        map.put("description", "ERP-owned account " + externalId);
        map.put("externalId", externalId);
        return new com.google.gson.Gson().toJson(map);
    }

    private static JsonObject json(final String body) {
        return JsonParser.parseString(body).getAsJsonObject();
    }

    @Test
    public void externalReferenceIsPersistedAndIdenticalReplayReturnsExistingAccount() {
        final String suffix = Utils.uniqueRandomStringGenerator("", 6);
        final String externalId = "GL-" + suffix;
        final String glCode = "C" + suffix;

        // 1. create: reference persisted and returned
        final JsonObject created = json(
                Utils.performServerPost(requestSpec, ok, GL_ACCOUNTS_URL, payload(externalId, glCode, "Loan Receivable")));
        final long id = created.get("resourceId").getAsLong();
        assertEquals(externalId, created.get("resourceExternalId").getAsString());

        final JsonObject fetched = json(Utils.performServerGet(requestSpec, ok,
                "/fineract-provider/api/v1/glaccounts/" + id + "?" + Utils.TENANT_IDENTIFIER, null));
        assertEquals(externalId, fetched.get("externalId").getAsString());
        assertEquals(glCode, fetched.get("glCode").getAsString());

        // 2. identical replay (four times, like the ERP sync will do): same id every time, flagged as a replay
        for (int i = 0; i < 3; i++) {
            final JsonObject replay = json(
                    Utils.performServerPost(requestSpec, ok, GL_ACCOUNTS_URL, payload(externalId, glCode, "Loan Receivable")));
            assertEquals(id, replay.get("resourceId").getAsLong(), "replay must return the original account");
            assertTrue(replay.getAsJsonObject("changes").get("idempotentReplay").getAsBoolean());
        }

        // exactly one account carries this reference / code
        final String all = Utils.performServerGet(requestSpec, ok, GL_ACCOUNTS_URL, null);
        long matches = JsonParser.parseString(all).getAsJsonArray().asList().stream().filter(
                e -> e.getAsJsonObject().has("externalId") && externalId.equals(e.getAsJsonObject().get("externalId").getAsString()))
                .count();
        assertEquals(1, matches, "no duplicate GL account for the same external reference");
    }

    @Test
    public void sameReferenceWithDifferentPayloadIsRejectedWith409() {
        final String suffix = Utils.uniqueRandomStringGenerator("", 6);
        final String externalId = "GL-" + suffix;
        final String glCode = "D" + suffix;
        Utils.performServerPost(requestSpec, ok, GL_ACCOUNTS_URL, payload(externalId, glCode, "Interest Income"));

        // different name, same reference
        final JsonObject error = json(
                Utils.performServerPost(requestSpec, conflict, GL_ACCOUNTS_URL, payload(externalId, glCode, "Interest Income (renamed)")));
        assertEquals("error.msg.glaccount.externalid.conflict", error.get("userMessageGlobalisationCode").getAsString());

        // different code, same reference
        final JsonObject error2 = json(
                Utils.performServerPost(requestSpec, conflict, GL_ACCOUNTS_URL, payload(externalId, glCode + "X", "Interest Income")));
        assertEquals("error.msg.glaccount.externalid.conflict", error2.get("userMessageGlobalisationCode").getAsString());
    }

    @Test
    public void createIsRefusedForUserWithoutCreateGlAccountPermission() {
        // a fresh role with no permissions at all, and a user holding only that role
        final Integer roleId = RolesHelper.createRole(requestSpec, ok);
        final Integer staffId = StaffHelper.createStaff(requestSpec, ok);
        final String username = Utils.uniqueRandomStringGenerator("glsync", 8);
        final String password = "A1b2c3d4e5f$";
        UserHelper.createUser(requestSpec, ok, roleId, staffId, username, password, "resourceId");

        final RequestSpecification restricted = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        restricted.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(username, password));

        final String suffix = Utils.uniqueRandomStringGenerator("", 6);
        final JsonObject error = json(
                Utils.performServerPost(restricted, forbidden, GL_ACCOUNTS_URL, payload("GL-" + suffix, "P" + suffix, "Bank")));
        assertTrue(error.toString().contains("CREATE_GLACCOUNT"), "error must name the missing permission: " + error);
    }
}
