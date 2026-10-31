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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.accounting.glaccount.command.GLAccountCommand;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.junit.jupiter.api.Test;

/**
 * The pure payload comparison behind "same reference, identical payload returns the existing account".
 */
class GLAccountExternalIdPolicyTest {

    private static GLAccount existing() {
        final GLAccount account = new GLAccount().setName("Loan Receivable").setGlCode("1200").setType(1).setUsage(1)
                .setManualEntriesAllowed(true).setDisabled(false).setDescription("Loans to SMEs");
        account.setId(7L);
        return account;
    }

    private static GLAccountCommand request(final String name, final String glCode, final Integer type, final Integer usage,
            final Boolean manualEntriesAllowed, final Boolean disabled, final String description) {
        return new GLAccountCommand(null, name, null, glCode, disabled, manualEntriesAllowed, type, usage, description, null, "GL-1200");
    }

    @Test
    void identicalPayloadMatches() {
        assertTrue(GLAccountExternalIdPolicy.isSamePayload(request("Loan Receivable", "1200", 1, 1, true, false, "Loans to SMEs"),
                existing()));
    }

    @Test
    void whitespaceAndOmittedDefaultsDoNotCountAsDifferences() {
        // trailing spaces, disabled omitted (defaults to false on creation)
        assertTrue(GLAccountExternalIdPolicy.isSamePayload(request("Loan Receivable ", " 1200", 1, 1, true, null, "Loans to SMEs"),
                existing()));
        // missing description equals an empty one
        final GLAccount noDescription = existing().setDescription(null);
        assertTrue(GLAccountExternalIdPolicy.isSamePayload(request("Loan Receivable", "1200", 1, 1, true, false, ""), noDescription));
    }

    @Test
    void anyBusinessAttributeDifferenceIsAConflict() {
        assertFalse(GLAccountExternalIdPolicy.isSamePayload(request("Loan Receivable (new)", "1200", 1, 1, true, false, "Loans to SMEs"),
                existing()), "name");
        assertFalse(
                GLAccountExternalIdPolicy.isSamePayload(request("Loan Receivable", "1201", 1, 1, true, false, "Loans to SMEs"), existing()),
                "glCode");
        assertFalse(
                GLAccountExternalIdPolicy.isSamePayload(request("Loan Receivable", "1200", 2, 1, true, false, "Loans to SMEs"), existing()),
                "type");
        assertFalse(
                GLAccountExternalIdPolicy.isSamePayload(request("Loan Receivable", "1200", 1, 2, true, false, "Loans to SMEs"), existing()),
                "usage");
        assertFalse(GLAccountExternalIdPolicy.isSamePayload(request("Loan Receivable", "1200", 1, 1, false, false, "Loans to SMEs"),
                existing()), "manualEntriesAllowed");
        assertFalse(
                GLAccountExternalIdPolicy.isSamePayload(request("Loan Receivable", "1200", 1, 1, true, true, "Loans to SMEs"), existing()),
                "disabled");
        assertFalse(GLAccountExternalIdPolicy.isSamePayload(request("Loan Receivable", "1200", 1, 1, true, false, "Other"), existing()),
                "description");
    }
}
