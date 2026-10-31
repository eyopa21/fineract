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

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;

/**
 * Thrown when a GL account creation carries an external reference that is already stored on another account whose
 * attributes differ from the request. Mapped to HTTP 409 by {@link GLAccountExternalIdConflictExceptionMapper}.
 */
public class GLAccountExternalIdConflictException extends AbstractPlatformDomainRuleException {

    public GLAccountExternalIdConflictException(final String externalId, final String existingGlCode) {
        super("error.msg.glaccount.externalid.conflict", "General Ledger Account with external reference " + externalId
                + " already exists (GL code " + existingGlCode + ") with a different payload", externalId, existingGlCode);
    }

    public GLAccountExternalIdConflictException(final String externalId) {
        super("error.msg.glaccount.externalid.conflict",
                "General Ledger Account with external reference " + externalId + " was created concurrently; retry the request",
                externalId);
    }
}
