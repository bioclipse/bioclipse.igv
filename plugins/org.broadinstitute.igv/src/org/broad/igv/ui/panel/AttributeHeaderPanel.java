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


/*
* TrackPanel.java
*
* Created on Sep 5, 2007, 4:09:39 PM
*
* To change this template, choose Tools | Templates
* and open the template in the editor.
*/

package org.broad.igv.ui.panel;

//~--- non-JDK imports --------------------------------------------------------

import org.broad.igv.PreferenceManager;
import org.broad.igv.track.AttributeManager;
import org.broad.igv.ui.FontManager;
import org.broad.igv.ui.IGVMainFrame;

import static org.broad.igv.ui.IGVMainFrame.getInstance;

import org.broad.igv.ui.util.Packable;
import org.broad.igv.ui.util.UIUtilities;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.*;
import java.util.List;

/**
 * @author jrobinso
 */
public class AttributeHeaderPanel extends JPanel {

    final static int MAXIMUM_FONT_SIZE = 10;
    final static int ATTRIBUTE_COLUMN_WIDTH = 10;
    final static int COLUMN_BORDER_WIDTH = 2;

    Map<String, Boolean> sortOrder = new HashMap();


    public AttributeHeaderPanel() {
        setBorder(javax.swing.BorderFactory.createLineBorder(Color.black));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener();
    }


    public int getAttributeColumnWidth() {
        return ATTRIBUTE_COLUMN_WIDTH;
    }


    private String getAttributeHeading(int x) {
        int idx = x / (ATTRIBUTE_COLUMN_WIDTH + COLUMN_BORDER_WIDTH);
        List<String> keys = AttributeManager.getInstance().getVisibleAttributes();
        if (idx < keys.size()) {
            return keys.get(idx);
        } else {
            return null;
        }
    }

    @Override
    protected void paintComponent(final Graphics graphics) {

        super.paintComponent(graphics);

        final List<String> keys = AttributeManager.getInstance().getVisibleAttributes();

        if (keys.size() > 0) {
            final Graphics2D graphics2 = (Graphics2D) graphics.create();

            // Divide the remaining space to get column widths
            int columnWidth = getAttributeColumnWidth();

            // Create font and font size
            int fontSize = (int) (0.9 * columnWidth);
            if (fontSize > MAXIMUM_FONT_SIZE) {
                fontSize = MAXIMUM_FONT_SIZE;
            }
            Font font = FontManager.getScalableFont(fontSize);

            // Change the origin for the text
            AffineTransform transform = AffineTransform.getTranslateInstance(0, getHeight() - COLUMN_BORDER_WIDTH);
            graphics2.transform(transform);

            // Now rotate text counter-clockwise 90 degrees
            transform = AffineTransform.getRotateInstance(-Math.PI / 2);
            graphics2.transform(transform);
            graphics2.setFont(font);
            FontMetrics fm = graphics2.getFontMetrics();
            int fontAscent = fm.getHeight();

            int i = 1;
            int x;
            for (String key : keys) {
                int columnLeftEdge = ((COLUMN_BORDER_WIDTH + ATTRIBUTE_COLUMN_WIDTH) * i++);
                x = columnLeftEdge + ((COLUMN_BORDER_WIDTH + ATTRIBUTE_COLUMN_WIDTH) - fontAscent) / 2;
                graphics2.drawString(key, 0, x);
            }
        }
    }

    private void addMouseListener() {


        setToolTipText("Click attribute heading to sort");

        MouseInputAdapter listener = new MouseInputAdapter() {

            @Override
            public void mouseClicked(MouseEvent e) {

                String attKey = getAttributeHeading(e.getX());
                if (attKey != null) {
                    Boolean tmp = sortOrder.get(attKey);
                    boolean sortAscending = tmp == null ? true : tmp.booleanValue();
                    sortTrackByAttribute(attKey, sortAscending);
                    sortOrder.put(attKey, !sortAscending);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }

            @Override
            public void mouseReleased(MouseEvent e) {
            }
        };
        addMouseMotionListener(listener);
        addMouseListener(listener);
    }


    final public void sortTrackByAttribute(String sortKey, boolean isSortAscending) {

        if (sortKey != null) {
            IGVMainFrame.getInstance().getTrackManager().sortAllTracksByAttributes(new String[]{sortKey}, new boolean[]{isSortAscending});
            IGVMainFrame.getInstance().getContentPane().repaint();
        }
    }

}
