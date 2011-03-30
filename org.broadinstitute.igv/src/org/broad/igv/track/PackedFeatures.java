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

import org.apache.log4j.Logger;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.tribble.Feature;

import java.util.*;

/**
 * dhmay changing 20110213.  I've changed a few things to make it possible to subclass this class. The
 * changes are mostly innocuous, but some are odd, e.g., the iter argument to packFeatures() doesn't declare
 * its type.  This is necessary so that packFeatures() can be overridden.
 *
 * @author jrobinso
 * @date Oct 7, 2010
 */
public class PackedFeatures<T extends Feature> {
    protected String trackName;
    protected String chr;
    protected int start;
    protected int end;
    protected List<T> features;
    protected List<FeatureRow> rows;
    private static Logger log = Logger.getLogger(PackedFeatures.class);
    protected int maxFeatureLength = 0;
    protected static int maxLevels = 200;

    /**
     * No-arg constructor to allow subclassing
     */
    PackedFeatures(){
    }

    PackedFeatures(String chr, int start, int end) {
        this.chr = chr;
        this.start = start;
        this.end = end;
        features = Collections.emptyList();
        rows = Collections.emptyList();
    }

    PackedFeatures(String chr, int start, int end, Iterator<T> iter, String trackName) {
        this.trackName = trackName;
        this.chr = chr;
        this.start = start;
        this.end = end;
        features = new ArrayList(1000);
        rows = packFeatures(iter);
    }


     /**
     * Some types of Features (splice junctions) should be packed on the same row even if start and end overlap.
     * This can be overridden in a subclass
     * @param feature
     * @return
     */
    protected int getFeatureStartForPacking(Feature feature)
    {
        return feature.getStart(); 
    }


    /**
     * Some types of Features (splice junctions) should be packed on the same row even if start and end overlap.
     * This can be overridden in a subclass
     * @param feature
     * @return
     */
    protected int getFeatureEndForPacking(Feature feature)
    {
        return feature.getEnd();
    }

    int getRowCount() {
        return getRows().size();
    }

    public boolean containsInterval(String chr, int start, int end) {
        return this.getChr().equals(chr) && start >= this.getStart() && end <= this.getEnd();
    }

    /**
     * Allocates each feature to the rows such that there is no overlap.
     *
     * @param iter TabixLineReader wrapping the collection of alignments. Note that this should
     * really be an Iterator<T>, but it can't be subclassed if that's the case.
     */
    List<FeatureRow> packFeatures(Iterator iter) {

        List<FeatureRow> rows = new ArrayList(10);
        if (iter == null || !iter.hasNext()) {
            return rows;
        }

        maxFeatureLength = 0;
        int totalCount = 0;

        LinkedHashMap<Integer, PriorityQueue<T>> bucketArray = new LinkedHashMap();
        Comparator pqComparator = new Comparator<T>() {
            public int compare(Feature row1, Feature row2) {
                return (row2.getEnd() - row2.getStart()) - (row1.getEnd() - row2.getStart());
            }
        };

        while (iter.hasNext()) {
            T feature = (T) iter.next();
            maxFeatureLength = Math.max(maxFeatureLength,
                    getFeatureEndForPacking(feature) - getFeatureStartForPacking(feature));
            features.add(feature);

            int bucketNumber = getFeatureStartForPacking(feature);

            PriorityQueue<T> bucket = bucketArray.get(bucketNumber);
            if (bucket == null) {
                bucket = new PriorityQueue<T>(5, pqComparator);
                bucketArray.put(bucketNumber, bucket);
            }
            bucket.add(feature);
            totalCount++;

        }

        // Allocate alignments to rows
        FeatureRow currentRow = new FeatureRow();
        int allocatedCount = 1;
        int nextStart = currentRow.end + FeatureTrack.MINIMUM_FEATURE_SPACING;


        int lastKey = 0;
        int lastAllocatedCount = 0;
        while (allocatedCount < totalCount && rows.size() < maxLevels) {

            // Check to prevent infinite loops
            if (lastAllocatedCount == allocatedCount) {
                String msg = "Infinite loop detected while packing features for track: " + getTrackName() +
                        ".<br>Not all features will be shown." +
                        "<br>Please contact igv-help@broadinstitute.org";

                log.error(msg);
                MessageUtils.showMessage(msg);
                break;
            }
            lastAllocatedCount = allocatedCount;

            // Next row Loop through alignments until we reach the end of the interval

            PriorityQueue<T> bucket = null;
            // Advance to nextLine occupied bucket

            ArrayList<Integer> emptyBucketKeys = new ArrayList();
            for (Integer key : bucketArray.keySet()) {
                //if (key < lastKey) {
                //    String msg = "Features from track: " + trackName + " are not sorted.  Some features might not be shown.<br>" +
                //            "Please notify igv-help@broadinstitute.org";
                //    MessageUtils.showMessage(msg);
                //}
                lastKey = key;
                if (key >= nextStart) {
                    bucket = bucketArray.get(key);

                    T feature = bucket.poll();

                    if (bucket.isEmpty()) {
                        emptyBucketKeys.add(key);
                    }
                    currentRow.addFeature(feature);
                    nextStart = currentRow.end + FeatureTrack.MINIMUM_FEATURE_SPACING;
                    allocatedCount++;
                }
            }
            for (Integer key : emptyBucketKeys) {
                bucketArray.remove(key);
            }


            // We've reached the end of the interval,  start a new row
            if (currentRow.features.size() > 0) {
                rows.add(currentRow);
                lastAllocatedCount = 0;
            }
            currentRow = new FeatureRow();
            nextStart = 0;
            lastKey = 0;


        }
        // Add the last row
        if (currentRow.features.size() > 0) {
            rows.add(currentRow);
        }

        return rows;
    }

    public String getTrackName() {
        return trackName;
    }

    public String getChr() {
        return chr;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public List<T> getFeatures() {
        return features;
    }

    public List<FeatureRow> getRows() {
        return rows;
    }

    public int getMaxFeatureLength() {
        return maxFeatureLength;
    }

    class FeatureRow {
        int start;
        int end;
        List<T> features;

        public FeatureRow() {
            this.features = new ArrayList(100);
        }

        public void addFeature(T feature) {
            if (features.isEmpty()) {
                this.start = getFeatureStartForPacking(feature);
            }
            features.add(feature);
            end = getFeatureEndForPacking(feature);
        }

        public List<T> getFeatures() {
            return features;
        }
    }
}