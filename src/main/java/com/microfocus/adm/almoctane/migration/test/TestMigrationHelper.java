/**
 * Copyright 2018 EntIT Software LLC, a Micro Focus company
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microfocus.adm.almoctane.migration.test;

import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.authentication.Authentication;
import com.hpe.adm.nga.sdk.entities.TestManualEntityList;
import com.hpe.adm.nga.sdk.model.*;
import com.hpe.adm.nga.sdk.network.OctaneHttpClient;
import com.hpe.adm.nga.sdk.network.OctaneHttpRequest;
import com.hpe.adm.nga.sdk.network.OctaneRequest;
import com.hpe.adm.nga.sdk.network.google.GoogleHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

public class TestMigrationHelper {

    private static final Logger logger = LoggerFactory.getLogger(TestMigrationHelper.class);

    private final TestManualEntityList.CreateTestManualEntities createTestManualEntities;
    private final TestManualEntityList.UpdateTestManualEntities updateTestManualEntities;
    private final OctaneHttpClient scriptUploadClient;
    private final int sharedSpace;
    private final int workSpace;
    private final String server;

    public TestMigrationHelper(final Octane octane, int sharedSpace, int workSpace, String server, Authentication authentication) {
        final TestManualEntityList testManualEntityList = octane.entityList(TestManualEntityList.class);
        createTestManualEntities = testManualEntityList.create();
        updateTestManualEntities = testManualEntityList.update();
        this.sharedSpace = sharedSpace;
        this.workSpace = workSpace;
        this.server = server;
        scriptUploadClient = new GoogleHttpClient(server);
        scriptUploadClient.authenticate(authentication);
    }

    public TestManualEntityModel createTestManualEntity(TestManualEntityModel testManualEntityModel) {
        logger.info("Uploading test {}", testManualEntityModel.getName());
        final TestManualEntityModel createdTestManualEntity = createTestManualEntities.entities(Collections.singleton(testManualEntityModel))
                .execute()
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not create test!"));

        logger.info("Uploaded test {} with id {}", testManualEntityModel.getName(), createdTestManualEntity.getId());
        return createdTestManualEntity;
    }

    public void uploadSteps(final String stepsData, final String testId) {
        logger.info("Uploading steps for test id {}", testId);
        final OctaneHttpRequest.PutOctaneHttpRequest putOctaneHttpRequest = new OctaneHttpRequest.PutOctaneHttpRequest(
                String.format("%s/api/shared_spaces/%s/workspaces/%s/tests/%s/script", server, sharedSpace, workSpace, testId),
                OctaneHttpRequest.JSON_CONTENT_TYPE,
                stepsData);
        scriptUploadClient.execute(putOctaneHttpRequest);
        logger.info("Successfully uploaded steps for test id {}", testId);
    }

    public String uploadParametersTable(final String parameters, final String parametersTableName) {
        logger.info("Uploading parameters table with name {}", parametersTableName);
        final OctaneHttpRequest.PostOctaneHttpRequest postOctaneHttpRequest = new OctaneHttpRequest.PostOctaneHttpRequest(
                String.format("%s/api/shared_spaces/%s/workspaces/%s/test_data_tables", server, sharedSpace, workSpace),
                OctaneHttpRequest.JSON_CONTENT_TYPE,
                parameters);
        final EntityModel returnedEntityModel = new OctaneRequest(scriptUploadClient, null)
                .getEntitiesResponse(postOctaneHttpRequest)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Failed to create parameters model!"));

        return returnedEntityModel.getId();
    }

    public void attachParametersTableToTest(final TestManualEntityModel testManualEntityModel, final String parametersTableId) {
        testManualEntityModel.getWrappedEntityModel().setValue(new ReferenceFieldModel("test_data_table", new EntityModel(
                Arrays.stream(
                        new FieldModel[]{
                                new StringFieldModel("id", parametersTableId),
                                new StringFieldModel("type", "test_data_table")}
                ).collect(Collectors.toSet())
        )));
        final long count = updateTestManualEntities.entities(Collections.singleton(testManualEntityModel)).execute().stream().count();
        if (count == 0) {
            throw new RuntimeException("Failed to update test!");
        }
    }

}
