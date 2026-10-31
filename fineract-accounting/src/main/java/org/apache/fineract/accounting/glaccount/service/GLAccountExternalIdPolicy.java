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
package org.apache.fineract.accounting.glaccount.service;

import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.accounting.glaccount.command.GLAccountCommand;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;

/**
 * Decides what a create request carrying an ERP external reference means when an account with that reference already
 * exists. Pure function: no I/O, no Spring, so it is unit tested in isolation and reused by the write service.
 * <p>
 * Contract (Part A of the integration):
 * <ul>
 * <li>same reference, identical payload: the existing account is returned, nothing is created;</li>
 * <li>same reference, different payload: the request is rejected with a conflict.</li>
 * </ul>
 * "Identical" compares every business attribute a caller can set on creation. Textual fields are compared trimmed, a
 * missing description equals an empty one, and {@code disabled} defaults to {@code false} as it does on creation.
 */
public final class GLAccountExternalIdPolicy {

    private GLAccountExternalIdPolicy() {}

    public static boolean isSamePayload(final GLAccountCommand request, final GLAccount existing) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(existing, "existing");
        final Long existingParentId = existing.getParent() == null ? null : existing.getParent().getId();
        final Long existingTagId = existing.getTagId() == null ? null : existing.getTagId().getId();
        return sameText(request.name(), existing.getName()) //
                && sameText(request.glCode(), existing.getGlCode()) //
                && Objects.equals(request.type(), existing.getType()) //
                && Objects.equals(request.usage(), existing.getUsage()) //
                && Objects.equals(Boolean.TRUE.equals(request.manualEntriesAllowed()), existing.isManualEntriesAllowed()) //
                && Objects.equals(Boolean.TRUE.equals(request.disabled()), existing.isDisabled()) //
                && sameText(request.description(), existing.getDescription()) //
                && Objects.equals(request.parentId(), existingParentId) //
                && Objects.equals(request.tagId(), existingTagId);
    }

    private static boolean sameText(final String a, final String b) {
        return StringUtils.trimToEmpty(a).equals(StringUtils.trimToEmpty(b));
    }
}
