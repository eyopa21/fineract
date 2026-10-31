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
package org.apache.fineract.accounting.glaccount.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.core.Response;
import org.apache.fineract.infrastructure.core.data.ApiGlobalErrorResponse;
import org.junit.jupiter.api.Test;

class GLAccountExternalIdConflictExceptionMapperTest {

    @Test
    void conflictIsMappedToHttp409WithFineractErrorBody() {
        final GLAccountExternalIdConflictExceptionMapper mapper = new GLAccountExternalIdConflictExceptionMapper();

        final Response response = mapper.toResponse(new GLAccountExternalIdConflictException("GL-1200", "1200"));

        assertEquals(409, response.getStatus());
        final ApiGlobalErrorResponse body = (ApiGlobalErrorResponse) response.getEntity();
        assertEquals("409", body.getHttpStatusCode());
        assertEquals("error.msg.glaccount.externalid.conflict", body.getUserMessageGlobalisationCode());
        assertTrue(body.getDefaultUserMessage().contains("GL-1200"));
        assertEquals(4091, mapper.errorCode());
    }
}
