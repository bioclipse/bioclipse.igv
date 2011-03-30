/*
 * Copyright (c) 2007-2011 by The Broad Institute, Inc. and the Massachusetts Institute of
 * Technology.  All Rights Reserved.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 *
 * THE SOFTWARE IS PROVIDED "AS IS." THE BROAD AND MIT MAKE NO REPRESENTATIONS OR
 * WARRANTES OF ANY KIND CONCERNING THE SOFTWARE, EXPRESS OR IMPLIED, INCLUDING,
 * WITHOUT LIMITATION, WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, NONINFRINGEMENT, OR THE ABSENCE OF LATENT OR OTHER DEFECTS, WHETHER
 * OR NOT DISCOVERABLE.  IN NO EVENT SHALL THE BROAD OR MIT, OR THEIR RESPECTIVE
 * TRUSTEES, DIRECTORS, OFFICERS, EMPLOYEES, AND AFFILIATES BE LIABLE FOR ANY DAMAGES
 * OF ANY KIND, INCLUDING, WITHOUT LIMITATION, INCIDENTAL OR CONSEQUENTIAL DAMAGES,
 * ECONOMIC DAMAGES OR INJURY TO PROPERTY AND LOST PROFITS, REGARDLESS OF WHETHER
 * THE BROAD OR MIT SHALL BE ADVISED, SHALL HAVE OTHER REASON TO KNOW, OR IN FACT
 * SHALL KNOW OF THE POSSIBILITY OF THE FOREGOING.
 */

package org.broad.igv.track;

import org.broad.igv.data.DataSource;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.feature.tribble.CachingFeatureReader;
import org.broad.igv.feature.tribble.CodecFactory;
import org.broad.igv.tdf.TDFDataSource;
import org.broad.igv.tdf.TDFReader;
import org.broad.igv.util.ParsingUtils;
import org.broad.igv.util.RuntimeUtils;
import org.broad.tribble.Feature;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.LocusScore;
import org.broad.tribble.FeatureCodec;
import org.broad.tribble.iterators.CloseableTribbleIterator;
import org.broad.tribble.source.BasicFeatureSource;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jrobinso
 * @date Jun 27, 2010
 */
public class TribbleFeatureSource implements org.broad.igv.track.FeatureSource {

    CachingFeatureReader reader;
    DataSource coverageSource;
    boolean isVCF;

    /**
     * Map of IGV chromosome name -> source name
     */
    Map<String, String> chrNameMap = new HashMap();
    private int featureWindowSize;
    Object header;
    Class featureClass;

    public TribbleFeatureSource(String path) throws IOException {

        FeatureCodec codec = CodecFactory.getCodec(path);
        isVCF = codec.getClass().isAssignableFrom(org.broad.tribble.vcf.VCFCodec.class);       
        featureClass = codec.getFeatureType();
        BasicFeatureSource basicReader = BasicFeatureSource.getFeatureSource(path, codec, true);
        header = basicReader.getHeader();
        initFeatureWindowSize(basicReader);
        reader = new CachingFeatureReader(basicReader, 5, getFeatureWindowSize());


        init();

        initCoverageSource(path + ".tdf");

    }

    private void initCoverageSource(String covPath) {
        if (ParsingUtils.pathExists(covPath)) {
            TDFReader reader = TDFReader.getReader(covPath);
            coverageSource = new TDFDataSource(reader, 0, "");
        }
    }

    private void init() {
        Genome genome = GenomeManager.getInstance().getCurrentGenome();
        if (genome != null) {
            Collection<String> seqNames = reader.getSequenceNames();
            if (seqNames != null)
                for (String seqName : seqNames) {
                    String igvChr = genome.getChromosomeAlias(seqName);
                    if (igvChr != null && !igvChr.equals(seqName)) {
                        chrNameMap.put(igvChr, seqName);
                    }
                }
        }
    }

    public Class getFeatureClass() {
        return featureClass;
    }


    /**
     * Return features overlapping the query interval
     *
     * @param chr
     * @param start
     * @param end
     * @return
     * @throws IOException
     */
    public CloseableTribbleIterator<Feature> getFeatures(String chr, int start, int end) throws IOException {

        String seqName = chrNameMap.get(chr);
        if (seqName == null) seqName = chr;
        return reader.query(seqName, start, end);
    }

    /**
     * Return coverage values overlapping the query interval.   At this time Tribble sources do not provide
     * coverage values
     *
     * @param chr
     * @param start
     * @param end
     * @param zoom
     * @return
     */
    public List<LocusScore> getCoverageScores(String chr, int start, int end, int zoom) {
        return coverageSource == null ? null :
                coverageSource.getSummaryScoresForRange(chr, start, end, zoom);
    }

    public int getFeatureWindowSize() {
        return featureWindowSize;
    }

    public void setFeatureWindowSize(int size) {
        this.featureWindowSize = size;
        reader.setBinSize(size);
    }

    public Object getHeader() {
        return header;
    }


    /**
     * Estimate an appropriate feature window size.
     *
     * @param reader
     */
    private void initFeatureWindowSize(org.broad.tribble.FeatureSource reader) {

        CloseableTribbleIterator<org.broad.tribble.Feature> iter = null;

        try {
            double mem = RuntimeUtils.getAvailableMemory();
            iter = reader.iterator();
            if (iter.hasNext()) {

                int nSamples = isVCF ? 100 : 1000;
                org.broad.tribble.Feature firstFeature = iter.next();
                org.broad.tribble.Feature lastFeature = iter.next();
                String chr = firstFeature.getChr();
                int n = 1;
                long len = 0;
                while (iter.hasNext() && n < nSamples) {
                    org.broad.tribble.Feature f = iter.next();
                    if (f != null) {
                        n++;
                        if (f.getChr().equals(chr)) {
                            lastFeature = f;
                        } else {
                            len += lastFeature.getEnd() - firstFeature.getStart() + 1;
                            firstFeature = f;
                            lastFeature = f;
                            chr = f.getChr();
                        }

                    }
                }
                double dMem = mem - RuntimeUtils.getAvailableMemory();
                double bytesPerFeature = Math.max(100, dMem / n);

                len += lastFeature.getEnd() - firstFeature.getStart() + 1;
                double featuresPerBase = ((double) n) / len;

                double targetBinMemory = 10000000;  // 10  mega bytes
                int maxBinSize = isVCF ? 1000000 : Integer.MAX_VALUE;
                int bs = Math.min(maxBinSize, (int) (targetBinMemory / (bytesPerFeature * featuresPerBase)));
                featureWindowSize = Math.max(100000, bs);
            } else {
                featureWindowSize = Integer.MAX_VALUE;
            }
        } catch (IOException e) {
            featureWindowSize = 1000000;
        }
    }

}
