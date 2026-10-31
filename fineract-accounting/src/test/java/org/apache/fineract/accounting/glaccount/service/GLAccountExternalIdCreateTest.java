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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.util.Optional;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.glaccount.exception.GLAccountDuplicateException;
import org.apache.fineract.accounting.glaccount.exception.GLAccountExternalIdConflictException;
import org.apache.fineract.accounting.glaccount.serialization.GLAccountCommandFromApiJsonDeserializer;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryRepository;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMappingRepository;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Part A: ERP-owned GL accounts. Exercises the idempotent create path of the write service in isolation (repository
 * mocked) so the decision logic is verified without a database.
 */
@ExtendWith(MockitoExtension.class)
class GLAccountExternalIdCreateTest {

    private static final String EXTERNAL_ID = "GL-1200";
    private static final String PAYLOAD = """
            {"name":"Loan Receivable","glCode":"1200","type":1,"usage":1,"manualEntriesAllowed":true,
             "description":"Loans to SMEs","externalId":"GL-1200"}""";
    private static final String DIFFERENT_PAYLOAD = """
            {"name":"Loan Receivable (renamed)","glCode":"1200","type":1,"usage":1,"manualEntriesAllowed":true,
             "description":"Loans to SMEs","externalId":"GL-1200"}""";

    @Mock
    private GLAccountRepository glAccountRepository;
    @Mock
    private JournalEntryRepository glJournalEntryRepository;
    @Mock
    private ProductToGLAccountMappingRepository productToGLAccountMappingRepository;
    @Mock
    private CodeValueRepositoryWrapper codeValueRepositoryWrapper;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Spy
    private GLAccountCommandFromApiJsonDeserializer fromApiJsonDeserializer = new GLAccountCommandFromApiJsonDeserializer(
            new FromJsonHelper());
    @InjectMocks
    private GLAccountWritePlatformServiceJpaRepositoryImpl service;

    private GLAccount existing;

    @BeforeEach
    void setUp() {
        existing = new GLAccount().setName("Loan Receivable").setGlCode("1200").setType(1).setUsage(1).setManualEntriesAllowed(true)
                .setDisabled(false).setDescription("Loans to SMEs").setExternalId(ExternalIdFactory.produce(EXTERNAL_ID));
        existing.setId(7L);
    }

    private static JsonCommand command(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), new FromJsonHelper(), "GLACCOUNT", null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    @Test
    void newExternalReferenceCreatesTheAccountAndPersistsTheReference() {
        when(glAccountRepository.findByExternalId(ExternalIdFactory.produce(EXTERNAL_ID))).thenReturn(Optional.empty());
        when(glAccountRepository.saveAndFlush(any(GLAccount.class))).thenAnswer(invocation -> {
            final GLAccount saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        final CommandProcessingResult result = service.createGLAccount(command(PAYLOAD));

        assertEquals(42L, result.getResourceId());
        assertEquals(EXTERNAL_ID, result.getResourceExternalId().getValue());
        verify(glAccountRepository, times(2)).saveAndFlush(any(GLAccount.class));
    }

    @Test
    void sameReferenceWithIdenticalPayloadReturnsExistingAccountWithoutSaving() {
        when(glAccountRepository.findByExternalId(ExternalIdFactory.produce(EXTERNAL_ID))).thenReturn(Optional.of(existing));

        final CommandProcessingResult result = service.createGLAccount(command(PAYLOAD));

        assertEquals(7L, result.getResourceId());
        assertEquals(EXTERNAL_ID, result.getResourceExternalId().getValue());
        assertNotNull(result.getChanges());
        assertEquals(Boolean.TRUE, result.getChanges().get(GLAccountWritePlatformServiceJpaRepositoryImpl.IDEMPOTENT_REPLAY));
        verify(glAccountRepository, never()).saveAndFlush(any(GLAccount.class));
    }

    @Test
    void sameReferenceWithDifferentPayloadIsRejectedWithConflictBeforeAnyWrite() {
        when(glAccountRepository.findByExternalId(ExternalIdFactory.produce(EXTERNAL_ID))).thenReturn(Optional.of(existing));

        final GLAccountExternalIdConflictException ex = assertThrows(GLAccountExternalIdConflictException.class,
                () -> service.createGLAccount(command(DIFFERENT_PAYLOAD)));

        assertEquals("error.msg.glaccount.externalid.conflict", ex.getGlobalisationMessageCode());
        assertTrue(ex.getDefaultUserMessage().contains(EXTERNAL_ID));
        verify(glAccountRepository, never()).saveAndFlush(any(GLAccount.class));
    }

    @Test
    void uniqueIndexViolationOnExternalIdIsMappedToConflict() {
        // concurrent creation: lookup saw nothing, the database constraint is the last line of defence
        when(glAccountRepository.findByExternalId(ExternalIdFactory.produce(EXTERNAL_ID))).thenReturn(Optional.empty());
        when(glAccountRepository.saveAndFlush(any(GLAccount.class))).thenThrow(new DataIntegrityViolationException("insert failed",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"acc_gl_account_external_id_UNIQUE\"")));

        assertThrows(GLAccountExternalIdConflictException.class, () -> service.createGLAccount(command(PAYLOAD)));
    }

    @Test
    void uniqueIndexViolationOnGlCodeIsStillReportedAsDuplicateCodeOnPostgresConstraintName() {
        final String json = PAYLOAD.replace("GL-1200", "GL-OTHER");
        when(glAccountRepository.findByExternalId(ExternalIdFactory.produce("GL-OTHER"))).thenReturn(Optional.empty());
        when(glAccountRepository.saveAndFlush(any(GLAccount.class))).thenThrow(new DataIntegrityViolationException("insert failed",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"acc_gl_account_gl_code_key\"")));

        assertThrows(GLAccountDuplicateException.class, () -> service.createGLAccount(command(json)));
    }

    @Test
    void accountWithoutExternalReferenceIsCreatedWithEmptyReferenceAndNoLookup() {
        final String json = """
                {"name":"Cash","glCode":"1100","type":1,"usage":1,"manualEntriesAllowed":true}""";
        when(glAccountRepository.saveAndFlush(any(GLAccount.class))).thenAnswer(invocation -> {
            final GLAccount saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        final CommandProcessingResult result = service.createGLAccount(command(json));

        assertEquals(1L, result.getResourceId());
        assertNull(result.getResourceExternalId(), "empty external id is reported as null");
        verify(glAccountRepository, never()).findByExternalId(any());
    }
}
