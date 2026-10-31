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

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.ApiGlobalErrorResponse;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.exceptionmapper.FineractExceptionMapper;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Maps {@link GLAccountExternalIdConflictException} to HTTP 409 Conflict. Registered like every other Fineract mapper:
 * Jersey picks up all Spring beans annotated with {@link Provider}, and {@link ErrorHandler} resolves the most specific
 * {@link FineractExceptionMapper} for batch and command error reporting.
 */
@Provider
@Component
@Scope("singleton")
@Slf4j
public class GLAccountExternalIdConflictExceptionMapper
        implements FineractExceptionMapper, ExceptionMapper<GLAccountExternalIdConflictException> {

    @Override
    public Response toResponse(final GLAccountExternalIdConflictException exception) {
        log.warn("GL account external reference conflict", ErrorHandler.findMostSpecificException(exception));
        final ApiGlobalErrorResponse error = ApiGlobalErrorResponse.create(Response.Status.CONFLICT.getStatusCode(),
                exception.getGlobalisationMessageCode(), exception.getDefaultUserMessage(), exception.getDefaultUserMessage());
        return Response.status(Response.Status.CONFLICT).entity(error).type(MediaType.APPLICATION_JSON).build();
    }

    @Override
    public int errorCode() {
        return 4091;
    }
}
