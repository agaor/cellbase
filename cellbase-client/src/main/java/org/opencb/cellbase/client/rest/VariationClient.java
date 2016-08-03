package org.opencb.cellbase.client.rest;

import org.opencb.biodata.models.core.RegulatoryFeature;
import org.opencb.biodata.models.core.Xref;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.VariantAnnotation;
import org.opencb.cellbase.client.config.ClientConfiguration;
import org.opencb.commons.datastore.core.Query;
import org.opencb.commons.datastore.core.QueryOptions;
import org.opencb.commons.datastore.core.QueryResponse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;

/**
 * Created by swaathi on 23/05/16.
 */
public class VariationClient extends FeatureClient<Variant> {

    public VariationClient(ClientConfiguration configuration) {
        super(configuration);
        this.clazz = Variant.class;

        this.category = "feature";
        this.subcategory = "variation";
    }

    public QueryResponse<String> getAllConsequenceTypes(Query query) throws IOException {
        return execute("consequence_types", query, new QueryOptions(), String.class);
    }

    public QueryResponse<String> getConsequenceTypeById(String id, QueryOptions options) throws IOException {
        return execute(id, "consequence_type", options, String.class);
    }
//    check data model returned
    public QueryResponse<RegulatoryFeature> getRegulatory(String id, QueryOptions options) throws IOException {
        return execute(id, "regulatory", options, RegulatoryFeature.class);
    }

    public QueryResponse<String> getPhenotype(String id, QueryOptions options) throws IOException {
        return execute(id, "phenotype", options, String.class);
    }

    public QueryResponse<String> getSequence(String id, QueryOptions options) throws IOException {
        return execute(id, "sequence", options, String.class);
    }

    public QueryResponse<String> getPopulationFrequency(String id, QueryOptions options) throws IOException {
        return execute(id, "population_frequency", options, String.class);
    }

    public QueryResponse<Xref> getXref(String id, QueryOptions options) throws IOException {
        return execute(id, "xref", options, Xref.class);
    }

    public QueryResponse<VariantAnnotation> getAnnotations(String id, QueryOptions options) throws IOException {
        this.category = "genomic";
        this.subcategory = "variant";
        return execute(id, "annotation", options, VariantAnnotation.class);
    }

    public QueryResponse<VariantAnnotation> getAnnotations(List<String> ids, QueryOptions options) throws IOException {
        this.category = "genomic";
        this.subcategory = "variant";


        Future<QueryResponse<VariantAnnotation>> future = null;
        ExecutorService executorService = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 4; i++) {
            future = executorService.submit(new AnnotatorRunnable(ids, options));
        }

        QueryResponse<VariantAnnotation> response = null;
        try {
            response = future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }


        executorService.shutdown();

        return response;
    }

    class AnnotatorRunnable implements Callable<QueryResponse<VariantAnnotation>> {

        private List<String> ids;
        private QueryOptions options;

        public AnnotatorRunnable(List<String> ids, QueryOptions options) {
            this.ids = ids;
            this.options = options;
        }

        @Override
        public QueryResponse<VariantAnnotation> call() throws Exception {
            QueryResponse<VariantAnnotation> annotation = execute(ids, "annotation", options, VariantAnnotation.class);
            return annotation;
        }
    }
}
