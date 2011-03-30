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

package org.broad.igv.sam.reader;

import javax.swing.*;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: jrobinso
 * Date: Dec 6, 2009
 * Time: 7:42:49 PM
 * To change this template use File | Settings | File Templates.
 */
public class DotAlignedIndexer extends AlignmentIndexer {

    int baseOffset = 1;

    public DotAlignedIndexer(File samFile, JProgressBar progressBar, SamIndexCreatorDialog.IndexWorker worker) {
        super(samFile, progressBar, worker);
        if (samFile.getName().endsWith(".bedz") || samFile.getName().endsWith(".bed")) {
            baseOffset = 0;
        }
    }

    int getAlignmentStart(String[] fields) throws NumberFormatException {
        int position = Integer.parseInt(fields[1]) - baseOffset;
        return position;
    }

    int getAlignmentLength(String[] fields) throws NumberFormatException {
        return Integer.parseInt(fields[2]) - Integer.parseInt(fields[1]) + 1;
    }

    String getChromosome(String[] fields) {
        String chr = fields[0];
        return chr;
    }

    @Override
    boolean isMapped(String[] fields) {
        return true;
    }
}
