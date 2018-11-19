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

package com.microfocus.adm.almoctane.migration.dbs;

import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.authentication.Authentication;
import com.hpe.adm.nga.sdk.enums.Phases;
import com.hpe.adm.nga.sdk.model.TestManualEntityModel;
import com.microfocus.adm.almoctane.migration.test.TestMigrationHelper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class DBSManualTestMigration {

    private static final Logger logger = LoggerFactory.getLogger(DBSManualTestMigration.class);

    private final Reader csvReader;
    private final Octane octane;
    private final TestMigrationHelper testMigrationHelper;

    public DBSManualTestMigration(final Authentication authentication, final String server, final int sharedSpace, final int workSpace, final String defaultUserName, final String defaultUserId,
                                  final File dbsCSV) throws Exception {
        logger.info("Init Migration Tool");

        csvReader = new FileReader(dbsCSV);

        octane = new Octane.Builder(authentication)
                .Server(server)
                .sharedSpace(sharedSpace)
                .workSpace(workSpace)
                .build();
        testMigrationHelper = new TestMigrationHelper(octane, sharedSpace, workSpace, server, authentication);
    }

    public void migrateDBS(final int startRecordNumber) throws IOException {

        final Iterable<CSVRecord> csvRecordIterator = CSVFormat.DEFAULT.parse(csvReader);

        for (CSVRecord csvRecord : csvRecordIterator) {
            if (csvRecord.getRecordNumber() < 3 || csvRecord.getRecordNumber() < startRecordNumber) {
                continue;
            }

            final TestManualEntityModel testManualEntityModel = new TestManualEntityModel(csvRecord.get(0), Phases.TestManualPhase.NEW);
            testManualEntityModel.setDescription(csvRecord.get(1));
            testManualEntityModel.setExpectedResultUdf(csvRecord.get(8));

            final TestManualEntityModel createdTestManualEntity = testMigrationHelper.createTestManualEntity(testManualEntityModel);

            final String buildStepsJson = buildStepsJson(csvRecord);
            testMigrationHelper.uploadSteps(buildStepsJson, createdTestManualEntity.getId());
            final String parametersTableName = "paramTable" + createdTestManualEntity.getId();
            final String parameters = buildParameters(csvRecord, parametersTableName);
            final String parametersTableId = testMigrationHelper.uploadParametersTable(parameters, parametersTableName);
            testMigrationHelper.attachParametersTableToTest(createdTestManualEntity, parametersTableId);

        }
    }

    private String buildStepsJson(CSVRecord csvRecord) {
        final String[] steps = csvRecord.get(3).split("\n");
        final StringBuilder currentStepsStringBuilder = new StringBuilder();
        for (String step : steps) {
            currentStepsStringBuilder.append("- ");
            currentStepsStringBuilder.append(escapeMetaCharacters(step));
            currentStepsStringBuilder.append("\\n");
        }

        return buildStepsJson(currentStepsStringBuilder.toString());
    }

    private String escapeMetaCharacters(String inputString) {
        final String[] metaCharacters = {/*"\\", "^", "$", "{", "}", "[", "]", "(", ")", ".", "*", "+", "?", "|", "<", ">", "-", "&", "%",*/  "\"", "'"};

        for (String metaCharacter : metaCharacters) {
            if (inputString.contains(metaCharacter)) {
                inputString = inputString.replace(metaCharacter, "\\" + metaCharacter);
            }
        }
        return inputString.replaceAll("(\\r|\\n|\\r\\n)+", "\\\\n");
    }

    private String buildStepsJson(final String manualSteps) {
        return String.format("{\"script\":\"%s\",\"comment\":\"\",\"revision_type\":\"Minor\"}", manualSteps);
    }

    private String buildParameters(final CSVRecord csvRecord, final String tableName) {
        //{"data":[{"name":"param1","data":{"iterations":[{"card":"dssd","pin":"ewe","acc":"weq"},{"card":"ew","pin":"ew","acc":"ew"}],"parameters":["card","pin","acc"]}}]}
        final StringBuilder paramsBuilder = new StringBuilder("{\"data\":[{\"name\":\"");
        paramsBuilder.append(tableName);
        paramsBuilder.append("\",\"data\":{\"iterations\":[");
        //"{\"card\":\"dssd\",\"pin\":\"ewe\",\"acc\":\"weq\"},{\"card\":\"ew\",\"pin\":\"ew\",\"acc\":\"ew\"}"

        final List<String> paramHeaders = new ArrayList<>();
        IntStream.range(4, 8).forEach(i -> extractParameters(csvRecord, paramsBuilder, paramHeaders, i));
        paramsBuilder.append("],\"parameters\":[");
        boolean firstTime = true;
        for (String paramHeader : paramHeaders) {
            if (!firstTime) {
                paramsBuilder.append(",");
            }
            firstTime = false;
            paramsBuilder.append("\"");
            paramsBuilder.append(paramHeader);
            paramsBuilder.append("\"");
        }
        paramsBuilder.append("]}}]}");

        return paramsBuilder.toString();
    }

    private void extractParameters(CSVRecord csvRecord, StringBuilder paramsBuilder, List<String> paramHeaders, int columnNumber) {
        final boolean firstPass = paramHeaders.isEmpty();
        if (!firstPass) {
            paramsBuilder.append(",");
        }
        paramsBuilder.append("{");
        final String[] parameterPairs = csvRecord.get(columnNumber).split("\n");
        for (int i = 0; i < parameterPairs.length; ++i) {
            final String parameters = parameterPairs[i];

            final String[] params = parameters.trim().split("\\:");
            if (params.length != 2) {
                continue;
            }
            if (i != 0) {
                paramsBuilder.append(",");
            }
            if (firstPass) {
                paramHeaders.add(params[0]);
            }
            paramsBuilder.append("\"");
            paramsBuilder.append(params[0]);
            paramsBuilder.append("\":\"");
            paramsBuilder.append(params[1]);
            paramsBuilder.append("\"");
        }
        paramsBuilder.append("}");
    }
}
