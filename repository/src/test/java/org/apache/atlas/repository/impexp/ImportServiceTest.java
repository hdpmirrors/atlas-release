/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.atlas.repository.impexp;

import com.google.inject.Inject;
import org.apache.atlas.AtlasErrorCode;
import org.apache.atlas.RequestContextV1;
import org.apache.atlas.TestModules;
import org.apache.atlas.TestUtilsV2;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.impexp.AtlasImportRequest;
import org.apache.atlas.store.AtlasTypeDefStore;
import org.apache.atlas.type.AtlasClassificationType;
import org.apache.atlas.type.AtlasTypeRegistry;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.apache.atlas.repository.impexp.ZipFileResourceTestUtils.getDefaultImportRequest;
import static org.apache.atlas.repository.impexp.ZipFileResourceTestUtils.getZipSource;
import static org.apache.atlas.repository.impexp.ZipFileResourceTestUtils.loadModelFromJson;
import static org.apache.atlas.repository.impexp.ZipFileResourceTestUtils.loadModelFromResourcesJson;
import static org.apache.atlas.repository.impexp.ZipFileResourceTestUtils.runAndVerifyQuickStart_v1_Import;
import static org.apache.atlas.repository.impexp.ZipFileResourceTestUtils.runImportWithNoParameters;
import static org.apache.atlas.repository.impexp.ZipFileResourceTestUtils.runImportWithNoParametersUsingBackingDirectory;
import static org.apache.atlas.repository.impexp.ZipFileResourceTestUtils.runImportWithParameters;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Guice(modules = TestModules.TestOnlyModule.class)
public class ImportServiceTest {
    private static final Logger LOG = LoggerFactory.getLogger(ImportServiceTest.class);
    private final ImportService importService;

    @Inject
    AtlasTypeRegistry typeRegistry;

    @Inject
    private AtlasTypeDefStore typeDefStore;

    @Inject
    public ImportServiceTest(ImportService importService) {
        this.importService = importService;
    }

    @BeforeTest
    public void setupTest() {
        RequestContextV1.clear();
        RequestContextV1.get().setUser(TestUtilsV2.TEST_USER);
    }

    @DataProvider(name = "sales")
    public static Object[][] getDataFromQuickStart_v1_Sales(ITestContext context) throws IOException, AtlasBaseException
    {
        return getZipSource("sales-v1-full.zip");
    }

    @Test(dataProvider = "sales")
    public void importDB1(InputStream inputStream) throws AtlasBaseException, IOException {
        loadBaseModel();
        runAndVerifyQuickStart_v1_Import(importService, inputStream);
    }

    @DataProvider(name = "reporting")
    public static Object[][] getDataFromReporting() throws IOException, AtlasBaseException
    {
        return getZipSource("reporting-v1-full.zip");
    }

    @Test(dataProvider = "reporting")
    public void importDB2(InputStream inputStream) throws AtlasBaseException, IOException {
        loadBaseModel();
        runAndVerifyQuickStart_v1_Import(importService, inputStream);
    }

    @DataProvider(name = "logging")
    public static Object[][] getDataFromLogging(ITestContext context) throws IOException, AtlasBaseException
    {
        return getZipSource("logging-v1-full.zip");
    }

    @Test(dataProvider = "logging")
    public void importDB3(InputStream inputStream) throws AtlasBaseException, IOException {
        loadBaseModel();
        runAndVerifyQuickStart_v1_Import(importService, inputStream);
    }

    @DataProvider(name = "salesNewTypeAttrs")
    public static Object[][] getDataFromSalesNewTypeAttrs(ITestContext context) throws IOException, AtlasBaseException
    {
        return getZipSource("salesNewTypeAttrs.zip");
    }

    @Test(dataProvider = "salesNewTypeAttrs", dependsOnMethods = "importDB1")
    public void importDB4(InputStream inputStream) throws AtlasBaseException, IOException {
        loadBaseModel();
        runImportWithParameters(importService, getDefaultImportRequest(), inputStream);
    }

    @DataProvider(name = "salesNewTypeAttrs-next")
    public static Object[][] getDataFromSalesNewTypeAttrsNext(ITestContext context) throws IOException, AtlasBaseException
    {
        return getZipSource("salesNewTypeAttrs-next.zip");
    }


    @Test(dataProvider = "salesNewTypeAttrs-next", dependsOnMethods = "importDB4")
    public void importDB5(InputStream inputStream) throws AtlasBaseException, IOException {
        final String newEnumDefName = "database_action";

        assertNotNull(typeDefStore.getEnumDefByName(newEnumDefName));

        AtlasImportRequest request = getDefaultImportRequest();
        Map<String, String> options = new HashMap<>();
        options.put("updateTypeDefinition", "false");
        request.setOptions(options);

        runImportWithParameters(importService, request, inputStream);
        assertNotNull(typeDefStore.getEnumDefByName(newEnumDefName));
        assertEquals(typeDefStore.getEnumDefByName(newEnumDefName).getElementDefs().size(), 4);
    }

    @Test(dataProvider = "salesNewTypeAttrs-next", dependsOnMethods = "importDB4")
    public void importDB6(InputStream inputStream) throws AtlasBaseException, IOException {
        final String newEnumDefName = "database_action";

        assertNotNull(typeDefStore.getEnumDefByName(newEnumDefName));

        AtlasImportRequest request = getDefaultImportRequest();
        Map<String, String> options = new HashMap<>();
        options.put("updateTypeDefinition", "true");
        request.setOptions(options);

        runImportWithParameters(importService, request, inputStream);
        assertNotNull(typeDefStore.getEnumDefByName(newEnumDefName));
        assertEquals(typeDefStore.getEnumDefByName(newEnumDefName).getElementDefs().size(), 8);
    }

    @DataProvider(name = "ctas")
    public static Object[][] getDataFromCtas(ITestContext context) throws IOException, AtlasBaseException
    {
        return getZipSource("ctas.zip");
    }

    @Test(dataProvider = "ctas")
    public void importCTAS(InputStream inputStream) throws IOException, AtlasBaseException {
        loadBaseModel();
        loadHiveModel();

        runImportWithNoParameters(importService, inputStream);
    }

    @DataProvider(name = "hdfs_path1")
    public static Object[][] getDataFromHdfsPath1(ITestContext context) throws IOException, AtlasBaseException
    {
        return getZipSource("hdfs_path1.zip");
    }


    @Test(dataProvider = "hdfs_path1", expectedExceptions = AtlasBaseException.class)
    public void importHdfs_path1(InputStream inputStream) throws IOException, AtlasBaseException {
        loadBaseModel();
        loadFsModel();
        loadModelFromResourcesJson("tag1.json", typeDefStore, typeRegistry);

        try {
            runImportWithNoParameters(importService, inputStream);
        } catch (AtlasBaseException e) {
            assertEquals(e.getAtlasErrorCode(), AtlasErrorCode.INVALID_IMPORT_ATTRIBUTE_TYPE_CHANGED);
            AtlasClassificationType tag1 = typeRegistry.getClassificationTypeByName("tag1");
            assertNotNull(tag1);
            assertEquals(tag1.getAllAttributes().size(), 2);
            throw e;
        }
    }

    @Test(dataProvider = "ctas")
    public void importWithBackingDirectory(InputStream inputStream) throws IOException, AtlasBaseException {
        loadBaseModel();
        loadFsModel();

        runImportWithNoParametersUsingBackingDirectory(importService, inputStream);
    }

    @Test
    public void importServiceProcessesIOException() {
        ImportService importService = new ImportService(typeDefStore, typeRegistry, null, null);
        AtlasImportRequest req = mock(AtlasImportRequest.class);

        Answer<Map> answer = new Answer<Map>() {
            @Override
            public Map answer(InvocationOnMock invocationOnMock) throws Throwable {
                throw new IOException("file is read only");
            }
        };

        when(req.getFileName()).thenReturn("some-file.zip");
        when(req.getOptions()).thenAnswer(answer);

        try {
            importService.run(req, "a", "b", "c");
        }
        catch (AtlasBaseException ex) {
            assertEquals(ex.getAtlasErrorCode().getErrorCode(), AtlasErrorCode.INVALID_PARAMETERS.getErrorCode());
        }
    }

    private void loadBaseModel() throws IOException, AtlasBaseException {
        loadModelFromJson("0010-base_model.json", typeDefStore, typeRegistry);
    }

    private void loadFsModel() throws IOException, AtlasBaseException {
        loadModelFromJson("0020-fs_model.json", typeDefStore, typeRegistry);
    }

    private void loadHiveModel() throws IOException, AtlasBaseException {
        loadModelFromJson("0030-hive_model.json", typeDefStore, typeRegistry);
    }
}
