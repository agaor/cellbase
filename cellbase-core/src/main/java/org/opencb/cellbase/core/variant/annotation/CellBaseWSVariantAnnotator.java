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

package org.opencb.cellbase.core.variant.annotation;

import org.apache.commons.lang3.StringUtils;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.VariantAnnotation;
import org.opencb.cellbase.core.client.CellBaseClient;
import org.opencb.datastore.core.QueryOptions;
import org.opencb.datastore.core.QueryResponse;
import org.opencb.datastore.core.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Created by fjlopez on 02/03/15.
 */
public class CellBaseWSVariantAnnotator implements VariantAnnotator {

    private CellBaseClient cellBaseClient;
    private List<VariantAnnotation> variantAnnotationList;

    private Logger logger;

    public CellBaseWSVariantAnnotator(CellBaseClient cellBaseClient) {
        this.cellBaseClient = cellBaseClient;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    public boolean open() {
        return true;
    }

    public boolean close() {
        return false;
    }

    public List<VariantAnnotation> run(List<Variant> variantList) {
        logger.debug("Annotator sends {} new variants for annotation. Waiting for the result", variantList.size());
        QueryResponse<QueryResult<VariantAnnotation>> response;
        try {
            response = cellBaseClient.getFullAnnotation(CellBaseClient.Category.genomic,
                    CellBaseClient.SubCategory.variant, variantList, new QueryOptions("post", true));
        } catch (IOException e) {
            return null;
        }

        //TODO: assuming CellBase annotation will always be the first and therefore variantAnnotationList will be empty
//        variantAnnotationList = new ArrayList<>(variantList.size());
        for (QueryResult<VariantAnnotation> queryResult : response.getResponse()) {
            if (queryResult.getResult().size() > 0) {
                variantAnnotationList.add(queryResult.getResult().get(0));
            } else {
                logger.warn("Emtpy result for '{}'", queryResult.getId());
            }
        }
        return variantAnnotationList;
    }


    // TODO: use a external class for this (this method could be added to GenomicVariant class)
    private Variant getGenomicVariant(Variant variant) {
        if(variant.getAlternate().equals(".")) {  // reference positions are not variants
            return null;
        } else {
            String ref;
            if (variant.getAlternate().equals("<DEL>")) {  // large deletion
                int end = Integer.valueOf(variant.getSourceEntries().get("_").getAttributes().get("END"));  // .get("_") because studyId and fileId are empty strings when VariantSource is initialized at readInputFile
                ref = StringUtils.repeat("N", end - variant.getStart());
                return new Variant(variant.getChromosome(), variant.getStart(),
                        ref, variant.getAlternate().equals("") ? "-" : variant.getAlternate());
                // TODO: structural variants are not yet properly handled. Implement and remove this patch asap
            } else if(variant.getAlternate().startsWith("<") || (variant.getAlternate().length()>1 && variant.getReference().length()>1)) {
                return null;
            } else {
                ref = variant.getReference().equals("") ? "-" : variant.getReference();
                return new Variant(variant.getChromosome(), variant.getStart(),
                        ref, variant.getAlternate().equals("") ? "-" : variant.getAlternate());
            }
        }
    }

    public void setVariantAnnotationList(List<VariantAnnotation> variantAnnotationList) {
        this.variantAnnotationList = variantAnnotationList;
    }
}