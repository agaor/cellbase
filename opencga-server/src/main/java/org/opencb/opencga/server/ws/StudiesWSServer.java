/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.server.ws;

import com.wordnik.swagger.annotations.*;
import org.opencb.biodata.models.feature.Region;
import org.opencb.datastore.core.ObjectMap;
import org.opencb.datastore.core.Query;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.analysis.files.FileScanner;
import org.opencb.opencga.analysis.storage.AnalysisFileIndexer;
import org.opencb.opencga.catalog.db.api.CatalogFileDBAdaptor;
import org.opencb.opencga.catalog.exceptions.CatalogException;
import org.opencb.opencga.catalog.models.DataStore;
import org.opencb.opencga.catalog.models.File;
import org.opencb.opencga.catalog.models.Index;
import org.opencb.opencga.catalog.models.Study;
import org.opencb.opencga.core.exception.VersionException;
import org.opencb.opencga.storage.core.StorageManagerException;
import org.opencb.opencga.storage.core.alignment.AlignmentStorageManager;
import org.opencb.opencga.storage.core.alignment.adaptors.AlignmentDBAdaptor;
import org.opencb.opencga.storage.core.variant.adaptors.VariantDBAdaptor;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;


@Path("/{version}/studies")
@Produces(MediaType.APPLICATION_JSON)
@Api(value = "Studies", position = 3, description = "Methods for working with 'studies' endpoint")
public class StudiesWSServer extends OpenCGAWSServer {


    public StudiesWSServer(@PathParam("version") String version, @Context UriInfo uriInfo,
                           @Context HttpServletRequest httpServletRequest) throws IOException, VersionException {
        super(version, uriInfo, httpServletRequest);
    }

    @GET
    @Path("/create")
    @ApiOperation(value = "Create study with GET method", position = 1)
    public Response createStudy(@ApiParam(value = "projectId",    required = true)  @QueryParam("projectId") String projectIdStr,
                                @ApiParam(value = "name",         required = true)  @QueryParam("name") String name,
                                @ApiParam(value = "alias",        required = true)  @QueryParam("alias") String alias,
                                @ApiParam(value = "type",         required = false) @DefaultValue("CASE_CONTROL") @QueryParam("type") Study.Type type,
                                @ApiParam(value = "creatorId",    required = false) @QueryParam("creatorId") String creatorId,
                                @ApiParam(value = "creationDate", required = false) @QueryParam("creationDate") String creationDate,
                                @ApiParam(value = "description",  required = false) @QueryParam("description") String description,
                                @ApiParam(value = "status",       required = false) @QueryParam("status") String status,
                                @ApiParam(value = "cipher",       required = false) @QueryParam("cipher") String cipher) {
        try {
            int projectId = catalogManager.getProjectId(projectIdStr);
            QueryResult queryResult = catalogManager.createStudy(projectId, name, alias, type, creatorId,
                    creationDate, description, status, cipher, null, null, null, null, null, queryOptions, sessionId);
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @POST
    @Path("/create")
    @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Create a file with POST method", response = QueryResult.class, position = 1, notes =
            "Wont't accept files, jobs, experiments, samples.<br>" +
                    "Will accept (but not yet): acl, uri, cohorts, datasets.<br>" +
//            "Work in progress.<br>" +
//            "Only nested files parameter accepted, and only a few parameters.<br>" +
//            "<b>{ files:[ { format, bioformat, path, description, type, jobId, attributes } ] }</b><br>" +
                    "<ul>" +
                    "<il><b>id</b>, <b>lastActivity</b> and <b>diskUsage</b> parameters will be ignored.<br></il>" +
                    "<il><b>type</b> accepted values: [<b>'CASE_CONTROL', 'CASE_SET', 'CONTROL_SET', 'FAMILY', 'PAIRED', 'TRIO'</b>].<br></il>" +
                    "<il><b>creatorId</b> should be the same as que sessionId user (unless you are admin) </il>" +
                    "<ul>")
    public Response createStudyPOST(@ApiParam(value = "projectId", required = true) @QueryParam("projectId") String projectIdStr,
                                    @ApiParam(value="studies", required = true) List<Study> studies) {
//        List<Study> catalogStudies = new LinkedList<>();
        List<QueryResult<Study>> queryResults = new LinkedList<>();
        int projectId;
        try {
            projectId = catalogManager.getProjectId(projectIdStr);
        } catch (CatalogException e) {
            e.printStackTrace();
            return createErrorResponse(e);
        }
        for (Study study : studies) {
            System.out.println("study = " + study);
            try {
                QueryResult<Study> queryResult = catalogManager.createStudy(projectId, study.getName(),
                        study.getAlias(), study.getType(), study.getCreatorId(), study.getCreationDate(),
                        study.getDescription(), study.getStatus(), study.getCipher(), null, null, null, study.getStats(),
                        study.getAttributes(), queryOptions, sessionId);
                Study studyAdded = queryResult.getResult().get(0);
                queryResults.add(queryResult);
//                List<File> files = study.getFiles();
//                if(files != null) {
//                    for (File file : files) {
//                        QueryResult<File> fileQueryResult = catalogManager.createFile(studyAdded.getId(), file.getType(), file.getFormat(),
//                                file.getBioformat(), file.getPath(), file.getOwnerId(), file.getCreationDate(),
//                                file.getDescription(), file.getStatus(), file.getDiskUsage(), file.getExperimentId(),
//                                file.getSampleIds(), file.getJobId(), file.getStats(), file.getAttributes(), true, sessionId);
//                        file = fileQueryResult.getResult().get(0);
//                        System.out.println("fileQueryResult = " + fileQueryResult);
//                        studyAdded.getFiles().add(file);
//                    }
//                }
            } catch (Exception e) {
//                queryResults.add(new QueryResult<>("createStudy", 0, 0, 0, "", e, Collections.<Study>emptyList()));
                return createErrorResponse(e);
            }
        }
        return createOkResponse(queryResults);
    }

    @GET
    @Path("/{studyId}/info")
    @ApiOperation(value = "Study information", position = 2)
    public Response info(@ApiParam(value = "studyId", required = true) @PathParam("studyId") String studyIdsStr) {
        try {
            String[] studyIdArray = studyIdsStr.split(",");
            List<QueryResult<Study>> queryResults = new LinkedList<>();
            for (String studyIdStr : studyIdArray) {
                int studyId = catalogManager.getStudyId(studyIdStr);
                queryResults.add(catalogManager.getStudy(studyId, sessionId, queryOptions));
            }
            return createOkResponse(queryResults);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{studyId}/files")
    @ApiOperation(value = "Study files information", position = 3)
    public Response getAllFiles(@ApiParam(value = "studyId", required = true) @PathParam("studyId") String studyIdStr) {
        try {
            int studyId = catalogManager.getStudyId(studyIdStr);
            QueryResult queryResult = catalogManager.getAllFiles(studyId, queryOptions, sessionId);
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{studyId}/jobs")
    @ApiOperation(value = "Get all jobs", position = 4)
    public Response getAllJobs(@ApiParam(value = "studyId", required = true) @PathParam("studyId") String studyIdStr) {
        try {
            int studyId = catalogManager.getStudyId(studyIdStr);
            return createOkResponse(catalogManager.getAllJobs(studyId, sessionId));
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{studyId}/samples")
    @ApiOperation(value = "Study samples information", position = 5)
    public Response getAllSamples(@ApiParam(value = "studyId", required = true) @PathParam("studyId") String studyIdStr) {
        try {
            int studyId = catalogManager.getStudyId(studyIdStr);
            QueryResult queryResult = catalogManager.getAllSamples(studyId, queryOptions, sessionId);
            return createOkResponse(queryResult);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{studyId}/variants")
    @ApiOperation(value = "Study samples information", position = 6)
    public Response getVariants(@ApiParam(value = "studyId", required = true) @PathParam("studyId") String studyIdStr,
                                @ApiParam(value = "region", required = false) @DefaultValue("") @QueryParam("region") String region,
                                @ApiParam(value = "gene", required = false) @DefaultValue("") @QueryParam("gene") String gene,
                                @ApiParam(value = "id", required = false) @DefaultValue("") @QueryParam("id") String id,
                                @ApiParam(value = "merge", required = false) @DefaultValue("false") @QueryParam("merge") boolean merge,
                                @ApiParam(value = "histogram", required = false) @DefaultValue("false") @QueryParam("histogram") boolean histogram,
                                @ApiParam(value = "GroupBy: [ct, gene, ensemblGene]", required = false) @DefaultValue("") @QueryParam("groupBy") String groupBy,
                                @ApiParam(value = "interval", required = false) @DefaultValue("2000") @QueryParam("interval") int interval) {

        Query query = new Query();
//        query.put(VariantDBAdaptor.VariantQueryParams.STUDIES.key(), studyIdStr);
        query.put(VariantDBAdaptor.VariantQueryParams.REGION.key(), region);
        query.put(VariantDBAdaptor.VariantQueryParams.GENE.key(), gene);

        DataStore dataStore;
        try {
            dataStore = AnalysisFileIndexer.getDataStore(catalogManager, (studyIdStr.contains(","))
                    ? Integer.parseInt(studyIdStr.split(",")[0])
                    : Integer.parseInt(studyIdStr),
                    File.Bioformat.VARIANT, sessionId);
        } catch (CatalogException e) {
            e.printStackTrace();
            return createErrorResponse(e);
        }
        String storageEngine = dataStore.getStorageEngine();
        String dbName = dataStore.getDbName();

        VariantDBAdaptor dbAdaptor;
        try {
            dbAdaptor = storageManagerFactory.getVariantStorageManager(storageEngine).getDBAdaptor(dbName);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | StorageManagerException e) {
            return createErrorResponse(e);
        }

        if (histogram) {
            QueryResult result = dbAdaptor.getFrequency(query, Region.parseRegion(region), interval);
            return createOkResponse(Collections.singletonList(result));
        } else if (!groupBy.isEmpty()) {
            QueryResult result = dbAdaptor.groupBy(query, groupBy, queryOptions);
            return createOkResponse(Collections.singletonList(result));
        } else if(merge) {
            query.put(VariantDBAdaptor.VariantQueryParams.STUDIES.key(), studyIdStr);
            QueryResult result = dbAdaptor.get(query, queryOptions);
            return createOkResponse(Collections.singletonList(result));
        } else {
            String[] studies = studyIdStr.split(",");
            List<QueryResult> results = new ArrayList<>(studies.length);
            for (String study : studies) {
                query.put(VariantDBAdaptor.VariantQueryParams.STUDIES.key(), study);
                QueryResult result = dbAdaptor.get(query, queryOptions);
                results.add(result);
            }
            return createOkResponse(results);
        }
    }

    @GET
    @Path("/{studyId}/alignments")
    @ApiOperation(value = "Study samples information", position = 7)
    public Response getAlignments(@ApiParam(value = "studyId", required = true) @PathParam("studyId") String studyIdStr,
                                  @ApiParam(value = "sampleId", required = true) @DefaultValue("") @QueryParam("sampleId") String sampleIds,
                                  @ApiParam(value = "fileId", required = true) @DefaultValue("") @QueryParam("fileId") String fileIds,
                                  @ApiParam(value = "region", required = true) @DefaultValue("") @QueryParam("region") String region,
                                  @ApiParam(value = "view_as_pairs", required = false) @DefaultValue("false") @QueryParam("view_as_pairs") boolean view_as_pairs,
                                  @ApiParam(value = "include_coverage", required = false) @DefaultValue("true") @QueryParam("include_coverage") boolean include_coverage,
                                  @ApiParam(value = "process_differences", required = false) @DefaultValue("true") @QueryParam("process_differences") boolean process_differences,
                                  @ApiParam(value = "histogram", required = false) @DefaultValue("false") @QueryParam("histogram") boolean histogram,
                                  @ApiParam(value = "interval", required = false) @DefaultValue("2000") @QueryParam("interval") int interval) {

        Query query = new Query();
        query.put(VariantDBAdaptor.VariantQueryParams.STUDIES.key(), studyIdStr);
        List<Region> regions = Region.parseRegions(region);

        List<QueryResult> results = new ArrayList<>();
        QueryResult alignmentsByRegion;

        // TODO if SampleIds are passed we need to get the BAM files for them and execute the code below

        try {
            int studyId = 4;
            int sampleId = 33;
            File file = catalogManager.getAllFiles(studyId, new QueryOptions()
                    .append(CatalogFileDBAdaptor.FileFilterOption.bioformat.toString(), File.Bioformat.ALIGNMENT)
                    .append(CatalogFileDBAdaptor.FileFilterOption.sampleIds.toString(), sampleId)
                    .append(CatalogFileDBAdaptor.FileFilterOption.index.toString() + ".status", Index.Status.READY), sessionId).first();
        } catch (CatalogException e) {
            e.printStackTrace();
        }

        for (String fileId : fileIds.split(",")) {
            int fileIdNum;
            File file;
            URI fileUri;
            try {
                fileIdNum = catalogManager.getFileId(fileId);
                QueryResult<File> queryResult = catalogManager.getFile(fileIdNum, sessionId);
                file = queryResult.getResult().get(0);
                fileUri = catalogManager.getFileUri(file);
            } catch (CatalogException e) {
                e.printStackTrace();
                return createErrorResponse(e);
            }

//            if (!file.getType().equals(File.Type.INDEX)) {
            if (file.getIndex() == null || file.getIndex().getStatus() != Index.Status.READY) {
                return createErrorResponse("", "File {id:" + file.getId() + " name:'" + file.getName() + "'} " +
                        " is not an indexed file.");
            }
            ObjectMap indexAttributes = new ObjectMap(file.getIndex().getAttributes());
            DataStore dataStore;
            try {
                dataStore = AnalysisFileIndexer.getDataStore(catalogManager, Integer.parseInt(studyIdStr), File.Bioformat.VARIANT, sessionId);
            } catch (CatalogException e) {
                e.printStackTrace();
                return createErrorResponse(e);
            }
            String storageEngine = dataStore.getStorageEngine();
            String dbName = dataStore.getDbName();

            int chunkSize = indexAttributes.getInt("coverageChunkSize", 200);
            QueryOptions queryOptions = new QueryOptions();
            queryOptions.put(AlignmentDBAdaptor.QO_FILE_ID, Integer.toString(fileIdNum));
            queryOptions.put(AlignmentDBAdaptor.QO_BAM_PATH, fileUri.getPath());     //TODO: Make uri-compatible
            queryOptions.put(AlignmentDBAdaptor.QO_VIEW_AS_PAIRS, view_as_pairs);
            queryOptions.put(AlignmentDBAdaptor.QO_INCLUDE_COVERAGE, include_coverage);
            queryOptions.put(AlignmentDBAdaptor.QO_PROCESS_DIFFERENCES, process_differences);
            queryOptions.put(AlignmentDBAdaptor.QO_INTERVAL_SIZE, interval);
            queryOptions.put(AlignmentDBAdaptor.QO_HISTOGRAM, histogram);
            queryOptions.put(AlignmentDBAdaptor.QO_COVERAGE_CHUNK_SIZE, chunkSize);

            if (indexAttributes.containsKey("baiFileId")) {
                File baiFile = null;
                try {
                    baiFile = catalogManager.getFile(indexAttributes.getInt("baiFileId"), sessionId).getResult().get(0);
                    URI baiUri = catalogManager.getFileUri(baiFile);
                    queryOptions.put(AlignmentDBAdaptor.QO_BAI_PATH, baiUri.getPath());  //TODO: Make uri-compatible
                } catch (CatalogException e) {
                    e.printStackTrace();
                    logger.error("Can't obtain bai file for file " + fileIdNum, e);
                }
            }

            AlignmentDBAdaptor dbAdaptor;
            try {
                AlignmentStorageManager alignmentStorageManager = storageManagerFactory.getAlignmentStorageManager(storageEngine);
                dbAdaptor = alignmentStorageManager.getDBAdaptor(dbName);
            } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | StorageManagerException e) {
                return createErrorResponse(e);
            }

            if (histogram) {
                if (regions.size() != 1) {
                    return createErrorResponse("", "Histogram fetch only accepts one region.");
                }
                alignmentsByRegion = dbAdaptor.getAllIntervalFrequencies(regions.get(0), queryOptions);
            } else {
                alignmentsByRegion = dbAdaptor.getAllAlignmentsByRegion(regions, queryOptions);
            }
            results.add(alignmentsByRegion);
        }

        return createOkResponse(results);
    }

    @GET
    @Path("/{studyId}/status")
    @ApiOperation(value = "Scans the study folder to find untracked or missing files", position = 8)
    public Response status(@ApiParam(value = "studyId", required = true) @PathParam("studyId") String studyIdStr) {
        try {
            int studyId = catalogManager.getStudyId(studyIdStr);
            Study study = catalogManager.getStudy(studyId, sessionId).first();
            FileScanner fileScanner = new FileScanner(catalogManager);

            /** First, run CheckStudyFiles to find new missing files **/
            List<File> checkStudyFiles = fileScanner.checkStudyFiles(study, false, sessionId);
            List<File> found = checkStudyFiles.stream().filter(f -> f.getStatus().equals(File.Status.READY)).collect(Collectors.toList());

            /** Get untracked files **/
            Map<String, URI> untrackedFiles = fileScanner.untrackedFiles(study, sessionId);

            /** Get missing files **/
            List<File> missingFiles = catalogManager.getAllFiles(studyId, new QueryOptions("status", File.Status.MISSING), sessionId).getResult();

            ObjectMap fileStatus = new ObjectMap("untracked", untrackedFiles).append("found", found).append("missing", missingFiles);

            return createOkResponse(new QueryResult<>("status", 0, 1, 1, null, null, Collections.singletonList(fileStatus)));
//            /** Print pretty **/
//            int maxFound = found.stream().map(f -> f.getPath().length()).max(Comparator.<Integer>naturalOrder()).orElse(0);
//            int maxUntracked = untrackedFiles.keySet().stream().map(String::length).max(Comparator.<Integer>naturalOrder()).orElse(0);
//            int maxMissing = missingFiles.stream().map(f -> f.getPath().length()).max(Comparator.<Integer>naturalOrder()).orElse(0);
//
//            String format = "\t%-" + Math.max(Math.max(maxMissing, maxUntracked), maxFound) + "s  -> %s\n";
//
//            if (!untrackedFiles.isEmpty()) {
//                System.out.println("UNTRACKED files");
//                untrackedFiles.forEach((s, u) -> System.out.printf(format, s, u));
//                System.out.println("\n");
//            }
//
//            if (!missingFiles.isEmpty()) {
//                System.out.println("MISSING files");
//                for (File file : missingFiles) {
//                    System.out.printf(format, file.getPath(), catalogManager.getFileUri(file));
//                }
//                System.out.println("\n");
//            }
//
//            if (!found.isEmpty()) {
//                System.out.println("FOUND files");
//                for (File file : found) {
//                    System.out.printf(format, file.getPath(), catalogManager.getFileUri(file));
//                }
//            }
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{studyId}/update")
    @ApiOperation(value = "Study modify", position = 9)
    public Response update(@ApiParam(value = "studyId", required = true) @PathParam("studyId") String studyIdStr,
                           @ApiParam(value = "name", required = false) @DefaultValue("") @QueryParam("name") String name,
                           @ApiParam(value = "type", required = false) @DefaultValue("") @QueryParam("type") String type,
                           @ApiParam(value = "description", required = false) @DefaultValue("") @QueryParam("description") String description,
                           @ApiParam(value = "status", required = false) @DefaultValue("") @QueryParam("status") String status)
//            @ApiParam(defaultValue = "attributes", required = false) @QueryParam("attributes") String attributes,
//            @ApiParam(defaultValue = "stats", required = false) @QueryParam("stats") String stats)
            throws IOException {
        try {
            int studyId = catalogManager.getStudyId(studyIdStr);
            ObjectMap objectMap = new ObjectMap();
            if(!name.isEmpty()) {
                objectMap.put("name", name);
            }
            if(!type.isEmpty()) {
                objectMap.put("type", type);
            }
            if(!description.isEmpty()) {
                objectMap.put("description", description);
            }
            if(!status.isEmpty()) {
                objectMap.put("status", status);
            }
//            objectMap.put("attributes", attributes);
//            objectMap.put("stats", stats);
            System.out.println(objectMap.toJson());
            QueryResult result = catalogManager.modifyStudy(studyId, objectMap, sessionId);
            return createOkResponse(result);
        } catch (Exception e) {
            return createErrorResponse(e);
        }
    }

    @GET
    @Path("/{studyId}/delete")
    @ApiOperation(value = "Delete a study [PENDING]", position = 10)
    public Response delete(@ApiParam(value = "studyId", required = true) @PathParam("studyId") String studyId) {
        return createOkResponse("PENDING");
    }

}