/**
 * 
 */
package org.opencb.opencga.storage.hadoop.variant.index;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.ColumnPrefixFilter;
import org.apache.hadoop.hbase.filter.PrefixFilter;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.hadoop.mapreduce.Mapper;
import org.opencb.biodata.models.variant.StudyEntry;
import org.opencb.biodata.models.variant.Variant;
import org.opencb.biodata.models.variant.avro.VariantType;
import org.opencb.biodata.models.variant.protobuf.VcfMeta;
import org.opencb.biodata.models.variant.protobuf.VcfSliceProtos.VcfSlice;
import org.opencb.biodata.tools.variant.converter.VcfSliceToVariantListConverter;
import org.opencb.datastore.core.ObjectMap;
import org.opencb.datastore.core.QueryResult;
import org.opencb.opencga.storage.core.StudyConfiguration;
import org.opencb.opencga.storage.hadoop.variant.archive.ArchiveFileMetadataManager;
import org.opencb.opencga.storage.hadoop.variant.index.models.protobuf.VariantCallProtos.VariantCallProt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Matthias Haimel mh719+git@cam.ac.uk
 *
 */
public class VariantTableMapper extends TableMapper<ImmutableBytesWritable, Put>{
    private static final String _TABLE_COUNT_COLUMN = "0/0";

    private final Logger LOG = LoggerFactory.getLogger(VariantTableDriver.class);

    private final AtomicReference<VariantTableHelper> helper = new AtomicReference<>();
    private StudyConfiguration studyConfiguration = null;
    private final Map<Integer, VcfMeta> vcfMetaMap = new ConcurrentHashMap<>();
    private Connection dbConnection = null;
    
    public Logger getLog() {
        return LOG;
    }

    @Override
    protected void setup(Mapper<ImmutableBytesWritable, Result, ImmutableBytesWritable, Put>.Context context) throws IOException,
            InterruptedException {
        getLog().debug("Setup configuration");
        // Setup configuration
        helper.set(loadHelper(context));
        this.studyConfiguration = loadStudyConf(); // Variant meta

        // Open DB connection
        dbConnection = ConnectionFactory.createConnection(context.getConfiguration());

        // Load VCF meta data for columns
        initVcfMetaMap(context.getConfiguration()); // Archive meta

        super.setup(context);
    }

    @Override
    protected void cleanup(Mapper<ImmutableBytesWritable, Result, ImmutableBytesWritable, Put>.Context context) throws IOException,
            InterruptedException {
        if (null != this.dbConnection) {
            dbConnection.close();
        }
    }

    protected StudyConfiguration loadStudyConf() throws IOException {
        return getHelper().loadMeta();
    }

    protected VariantTableHelper loadHelper(Mapper<ImmutableBytesWritable, Result, ImmutableBytesWritable, Put>.Context context) {
        return new VariantTableHelper(context.getConfiguration());
    }

    /**
     * Load VCF Meta data from input table and create table index
     * @param conf
     * @throws IOException
     */
    protected void initVcfMetaMap(Configuration conf) throws IOException {
        String tableName = Bytes.toString(getHelper().getIntputTable());
        getLog().debug("Load VcfMETA from {}", tableName);
        try (ArchiveFileMetadataManager metadataManager = new ArchiveFileMetadataManager(tableName, conf, null)) {
            QueryResult<VcfMeta> allVcfMetas = metadataManager.getAllVcfMetas(new ObjectMap());
            for (VcfMeta vcfMeta : allVcfMetas.getResult()) {
                vcfMetaMap.put(Integer.parseInt(vcfMeta.getVariantSource().getFileId()), vcfMeta);
            }
        }
        getLog().info(String.format("Loaded %s VcfMETA data!!!", vcfMetaMap.size()));
    }

    public VariantTableHelper getHelper() {
        return helper.get();
    }

    protected Connection getDbConnection() {
        return dbConnection;
    }

    @Override
    protected void map(ImmutableBytesWritable key, Result value, 
            Mapper<ImmutableBytesWritable, Result, ImmutableBytesWritable, Put>.Context context) throws IOException, InterruptedException {
        context.getCounter("OPENCGA.HBASE", "VCF_BLOCK_READ").increment(1);
        // TODO count and look for reference blocks if there is one variant
        if(value.isEmpty()){
            context.getCounter("OPENCGA.HBASE", "VCF_RESULT_EMPTY").increment(1);
            return; // TODO search backwards?
        }
        Map<String, Map<String, List<Integer>>> summary = new HashMap<>();
        try{
            if(Bytes.equals(key.get(), getHelper().getMetaRowKey()))
                return; // ignore metadata column

            // Calculate various positions
            String blockId = Bytes.toString(key.get());
            VariantTableHelper h = getHelper();
            String chr = h.extractChromosomeFromBlockId(blockId );
            Long sliceReg = h.extractPositionFromBlockId(blockId);
            Long startPos = h.getStartPositionFromSlice(sliceReg);
            Long nextStartPos = h.getStartPositionFromSlice(sliceReg + 1);
            String nextSliceKey = h.generateBlockId(chr, nextStartPos);

            // Load Archive data (selection only
            Map<Integer, List<Pair<List<String>, Variant>>> position2GtVariantMap = 
                    unpack(value, context,startPos.intValue(), nextStartPos.intValue(),false);

            Set<Integer> positionWithVariant = filterForVariant(value);

            // Load Variant data (For study)
            Map<Integer,Result> vartableMap = loadCurrentVariantsRegion(context,key.get(), nextSliceKey);
            
            // Done per position -> 1 position can have > 1 variant
            // TODO check for new variants
            // TODO update current variants

            
//            NavigableMap<byte[], byte[]> fm = value.getFamilyMap(h.getColumnFamily());
//            for (Entry<byte[], byte[]> x : fm.entrySet()) {
//                Integer id = getMeta().getIdFromColumn(Bytes.toString(x.getKey()));
//                VcfSlice vcfSlice = asSlice(x.getValue());
//                context.getCounter("OPENCGA.HBASE", "VCF_SLICE_READ").increment(1);
//                List<VcfRecord> lst = vcfSlice.getRecordsList();
//                context.getCounter("OPENCGA.HBASE", "VCF_Record_Count").increment(vcfSlice.getRecordsCount());
//                for(VcfRecord rec : lst){
//                    VariantTransformWrapper wrapper = new VariantTransformWrapper(id);
//                    context.getCounter("OPENCGA.HBASE", "VCF_REC_READ").increment(1);
//                    String gt = extractGt(id,rec);
//                    wrapper.setGenotype(gt);
//                    String alternate = rec.getAlternates();
//                    wrapper.setStatus(CallStatus.VARIANT);
//                    if(isReference(gt)){
//                        wrapper.setStatus(CallStatus.REF);
//                        context.getCounter("OPENCGA.HBASE", "VCF_REC_CALL_REF").increment(1);
//                    } else if(isNoCall(gt)){
//                        wrapper.setStatus(CallStatus.NOCALL);
//                        context.getCounter("OPENCGA.HBASE", "VCF_REC_CALL_NO").increment(1);
//                    } else if( ! isVariant(gt)){ // should not be called (if not ref or no-call -> should be variant)
//                        context.getCounter("OPENCGA.HBASE", "VCF_REC_CALL_OTHER-unknown").increment(1);
//                        getLog().warn(String.format("Found Genotype with '%s'",gt));
//                        continue; // TODO monitor if this happens
//                    }   
//                    
//                    int vcfPos = vcfSlice.getPosition() + rec.getRelativeStart();
//                    wrapper.setPosition(vcfPos);
//                    wrapper.setReference(rec.getReference());
//                    wrapper.setChromosome(chr);
//                    
//                    String row_key = generateKey(chr,vcfPos,rec.getReference(),alternate);
//                    context.getCounter("OPENCGA.HBASE", "VCF_REC_UPDATE").increment(1);
//                    updateSummary(summary, id, row_key, gt);
//                }
//            }
            Map<String, Result> currRes = new HashMap<String, Result>();
//                    fetchCurrentValues(context, summary.keySet());
            updateOutputTable(context,summary, currRes);
        }catch(InvalidProtocolBufferException e){
            throw new IOException(e);
        }
    }

    private Set<Integer> filterForVariant(Result value) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * 
     * @param value   {@link Result} object from Archive table
     * @param context
     * @param sliceStartPos 
     * @param nextSliceStartPos 
     * @param reload  TRUE, if values are reloaded from Archive table on demand to fill gaps - otherwise FALSE
     * @return
     * @throws IOException 
     */
    private Map<Integer, List<Pair<List<String>, Variant>>> unpack(Result value, Context context, int sliceStartPos, 
            int nextSliceStartPos, boolean reload) throws IOException {
        Map<Integer, List<Pair<List<String>, Variant>>> resMap = new HashMap<Integer, List<Pair<List<String>,Variant>>>();
        NavigableMap<byte[], byte[]> fm = value.getFamilyMap(getHelper().getColumnFamily());
        for (Entry<byte[], byte[]> x : fm.entrySet()) {
            int fileId = Bytes.toInt(x.getKey());
            VcfSliceToVariantListConverter converter = new VcfSliceToVariantListConverter(this.getVcfMeta(fileId));
            VcfSlice vcfSlice = asSlice(x.getValue());
            context.getCounter("OPENCGA.HBASE", "VCF_SLICE_READ").increment(1);
            
            List<Variant> varList = converter.convert(vcfSlice);
            int minStartPos = addVariants(context, varList, resMap, sliceStartPos, nextSliceStartPos, reload);
            if(minStartPos > sliceStartPos){ // more information in previous region
                context.getCounter("OPENCGA.HBASE", "VCF_SLICE-break-region").increment(1);
                querySliceBreak(context,resMap,previousArchiveSliceRK(value.getRow()),x.getKey(),sliceStartPos,nextSliceStartPos);
            }
        }
        return resMap;
    }

    private void querySliceBreak(Context context,Map<Integer, List<Pair<List<String>, Variant>>> resMap, 
            byte[] row, byte[] col, int sliceStartPos, int nextSliceStartPos) throws IOException {
        Get get = new Get(row);
        get.addColumn(getHelper().getColumnFamily(), col);
        Result res = getHelper().getHBaseManager().act(getDbConnection(),getHelper().getIntputTable(), table -> {return table.get(get);});
        // TODO check if sufficient 
    }

    private byte[] previousArchiveSliceRK(byte[] currRk){
        return null; //TODO
    }

    private int addVariants(Context context, List<Variant> varList, Map<Integer, List<Pair<List<String>, Variant>>> resMap, 
            int sliceStartPos, int nextSliceStartPos, boolean reload) {
        int minStartPos = nextSliceStartPos;
        for(Variant var : varList){
            VariantType vtype = var.getType();
            if(! reload){
                logCount(context, vtype); // update Count for output
            }
            List<String> gtLst = extractGts(var);
            Pair<List<String>, Variant> pair = new Pair<List<String>, Variant>(gtLst,var);
            int start = Math.max(sliceStartPos, var.getStart()); // get max start pos (in case of backwards search)
            int end = Math.min(nextSliceStartPos, var.getEnd()); // Don't go over the edge of the slice
            // One entry per position in region
            for(int vcfPos = start; vcfPos <= end; ++vcfPos){
                List<Pair<List<String>, Variant>> list = resMap.get(vcfPos);
                if(list == null){
                    list = new ArrayList<Pair<List<String>, Variant>>();
                    resMap.put(vcfPos, list);
                }
                list.add(pair);
            }
            minStartPos = Math.min(minStartPos,start); // update overall min
        }
        return minStartPos;
    }


    private List<String> extractGts(Variant var) {
      StudyEntry se = var.getStudies().get(0);
      int gtpos = se.getFormatPositions().get("GT");
      List<String> gtList = new ArrayList<String>();
      for (List<String> sd : se.getSamplesData()) {
          gtList.add(sd.get(gtpos));
      }
        return gtList;
    }

    private void logCount(Context ctx, VariantType vtype) {
        ctx.getCounter("OPENCGA.HBASE", "VCF_Record_Count").increment(1);
        switch (vtype) {
        case NO_VARIATION:
            ctx.getCounter("OPENCGA.HBASE", "VCF_REC_CALL-no-variant    ").increment(1);
            break;
        case SNV:
        case SNP:
        case MNV:
        case MNP:
            ctx.getCounter("OPENCGA.HBASE", "VCF_REC_CALL-variant").increment(1);
            break;
        case SYMBOLIC:
        case MIXED:
        case INDEL:
        case SV:
        case INSERTION:
        case DELETION:
        case TRANSLOCATION:
        case INVERSION:
        case CNV:
            ctx.getCounter("OPENCGA.HBASE", "VCF_REC_CALL-ignore").increment(1);
            break;
        default:
            break;
        }
    }

    protected Map<Integer, Result> loadCurrentVariantsRegion(Context context, byte[] sliceKey, String endKey) throws IOException {
        Map<Integer, Result> resMap = new HashMap<Integer, Result>();
        try (
                Table table = getDbConnection().getTable(TableName.valueOf(getHelper().getOutputTable()));
        ) {
            VariantTableHelper h = getHelper();
            Scan scan = new Scan(sliceKey, Bytes.toBytes(endKey));
            scan.setFilter(new PrefixFilter(sliceKey));
            scan.setFilter(new ColumnPrefixFilter(Bytes.toBytes(getHelper().getStudyId() + "_")));
            ResultScanner rs = table.getScanner(scan); 
            for(Result r : rs){
                Long pos = h.extractPositionFromVariantRowkey(Bytes.toString(r.getRow()));
                context.getCounter("OPENCGA.HBASE", "VCF_TABLE_SCAN-result").increment(1);
                if(!r.isEmpty()){ // only non empty rows
                    resMap.put(pos.intValue(), r);
                }
            }
        }
        return resMap ;
    }

    /**
     * Load (if available) current data, merge information and store new object in DB
     * @param context
     * @param summary
     * @param currentResults
     * @throws IOException 
     * @throws InterruptedException 
     */
    private void updateOutputTable(Context context, Map<String, Map<String, List<Integer>>> summary, Map<String, Result> currentResults) 
            throws IOException, InterruptedException {
        for(String rowKey : summary.keySet()){
            byte[] rkBytes = Bytes.toBytes(rowKey);
            // Wrap data
            Put put = wrapData(context, summary, rowKey, rkBytes);
            // Load possible Row
            Result res = currentResults.get(rowKey); 
            if(null == res || res.isEmpty()){
                context.write(new ImmutableBytesWritable(rkBytes), put);
                context.getCounter("OPENCGA.HBASE", "VCF_ROW_NEW").increment(1);
            } else {
                Put mergedPut = mergetData(res,put);
                context.write(new ImmutableBytesWritable(rkBytes), mergedPut);
                context.getCounter("OPENCGA.HBASE", "VCF_ROW_MERGED").increment(1);
            }
        }
    }

    /**
     * Merge Result set with Put object
     * @param res
     * @param put
     * @return Put object with merged data
     * @throws IOException
     */
    private Put mergetData(Result res, Put put) throws IOException {
        byte[] columnFamily = getHelper().getColumnFamily();
        Put nPut = new Put(put.getRow());
        
        NavigableMap<byte[], byte[]> resMap = res.getFamilyMap(columnFamily);
        Set<String> mergedColumns = new HashSet<>();
        for(Entry<byte[], byte[]> e : resMap.entrySet()){
            byte[] colQual = e.getKey();
            mergedColumns.add(Bytes.toString(colQual));
            if(! put.has(columnFamily, colQual)){
                nPut.addColumn(columnFamily, colQual, e.getValue());
            }else {
                byte[] valueArray = put.get(columnFamily, colQual).get(0).getValueArray();
                if(StringUtils.equals(Bytes.toString(colQual), _TABLE_COUNT_COLUMN)){
                    // Update count
                    int dbVal = Bytes.toInt(e.getValue());
                    int putVal = Bytes.toInt(valueArray);
                    int newVal = dbVal + putVal;
                    nPut.addColumn(columnFamily, colQual, Bytes.toBytes(newVal));
                } else {
                    // Merge lists
                    VariantCallProt a = VariantCallProt.parseFrom(e.getValue());
                    VariantCallProt b = VariantCallProt.parseFrom(valueArray);
                    List<Integer> lst = new ArrayList<Integer>(a.getSampleIdsList());
                    lst.addAll(b.getSampleIdsList());
                    Collections.sort(lst);
                    Set<Integer> set = new HashSet<Integer>(lst);
                    if(set.size() < lst.size()) // Check for duplicates!!!
                        throw new IllegalStateException(String.format("Duplicated samples in %s!!!", Arrays.toString(lst.toArray())));
                    byte[] nval = VariantCallProt.newBuilder().addAllSampleIds(lst).build().toByteArray();
                    nPut.addColumn(columnFamily, colQual, nval);
                }
            }
        }
        for(List<Cell> cl : put.getFamilyCellMap().values()){
            for(Cell c : cl){
                if(!mergedColumns.contains(Bytes.toString(c.getQualifierArray()))){
                    nPut.add(c);
                }
            }
        }
        return nPut;
    }

    private Put wrapData(Context context, Map<String, Map<String, List<Integer>>> summary, String rowKey, byte[] rkBytes)
            throws IOException, InterruptedException {
        Put put = new Put(rkBytes);
        for(Entry<String, List<Integer>> e : summary.get(rowKey).entrySet()){
            byte[] col = Bytes.toBytes(e.getKey());
            List<Integer> val = e.getValue();
            // to byte array (count or list of IDs)
            byte[] data = persist(e.getKey(), val);
            put.addColumn(getHelper().getColumnFamily(), col, data);
        }
        return put;
    }

    /**
     * Persist data depending on Column type (count or {@link VariantCallProt} )
     * @param key
     * @param val
     * @return byte[] of data
     */
    private byte[] persist(String key, List<Integer> val) {
        byte[] data = Bytes.toBytes(val.size());
        if(!StringUtils.equals(key, _TABLE_COUNT_COLUMN)){ //TODO change
            Collections.sort(val);
            VariantCallProt.Builder b = VariantCallProt.newBuilder();
            b.addAllSampleIds(val);
            data = b.build().toByteArray();
        }
        return data;
    }

    private void updateSummary(Map<String, Map<String, List<Integer>>> summary, Integer id, String row_key, String gt) {
        Map<String, List<Integer>> rkmap = summary.get(row_key);
        if(null == rkmap){
            rkmap = new HashMap<>();
            summary.put(row_key, rkmap);
        }
        List<Integer> idlst = rkmap.get(gt);
        
        if(null == idlst){
            idlst = new ArrayList<Integer>();
            rkmap.put(gt, idlst);
        }
        idlst.add(id);
    }

    private String generateKey(String chr, int vcfPos, String reference, String alternate) {
        return getHelper().generateVcfRowId(chr, (long) vcfPos, reference, alternate);
    }

    private String generatePositionKey(String chrom, int pos){
        return getHelper().generateRowPositionKey(chrom, (long) pos);
    }

    private VcfMeta getVcfMeta(Integer id) {
        return this.vcfMetaMap.get(id);
    }

    private VcfSlice asSlice(byte[] data) throws InvalidProtocolBufferException {
        return VcfSlice.parseFrom(data);
    }

}
