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

import org.apache.log4j.Logger;
import org.broad.igv.PreferenceManager;
import org.broad.igv.track.AttributeManager;
import org.broad.igv.track.Track;
import org.broad.igv.track.TrackClickEvent;
import org.broad.igv.track.TrackGroup;
import org.broad.igv.ui.IGVMainFrame;

import org.broad.igv.ui.UIConstants;
import org.broad.igv.ui.util.Packable;

import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author jrobinso
 */
public class AttributePanel extends TrackPanelComponent implements Packable, Paintable {

    private static Logger log = Logger.getLogger(AttributePanel.class);

    private static Map<String, Color> colorMap = new Hashtable();


    /**
     * Constructs ...
     */
    public AttributePanel(TrackPanel trackPanel) {
        super(trackPanel);
        setBorder(javax.swing.BorderFactory.createLineBorder(Color.black));
        init();
    }


    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);
        Rectangle visibleRect = getVisibleRect();
        removeMousableRegions();
        paintOffscreen((Graphics2D) g, visibleRect);

    }


    public void paintOffscreen(Graphics2D g, Rectangle rect) {

        g.setColor(Color.white);
        g.fill(rect);
        paintImpl(g, rect);
        super.paintBorder(g);
    }

    public void paintImpl(Graphics2D g, Rectangle visibleRect) {

        List<String> keys = AttributeManager.getInstance().getAttributeKeys();
        keys.removeAll(AttributeManager.getInstance().getHiddenAttributes());

        if (keys == null) {
            return;
        }

        if (keys.size() > 0) {


            // Get the current tracks
            TrackPanel trackPanel = (TrackPanel) getParent();
            Collection<TrackGroup> groups = trackPanel.getGroups();


            if (!groups.isEmpty()) {

                int attributeColumnWidth = getAttributeColumnWidth();
                final Graphics2D graphics2D = (Graphics2D) g.create();
                graphics2D.setColor(Color.BLACK);

                final Graphics2D greyGraphics = (Graphics2D) g.create();
                greyGraphics.setColor(UIConstants.VERY_LIGHT_GRAY);

                int regionX = 3;
                int regionY = 0;

                for (Iterator<TrackGroup> groupIter = groups.iterator(); groupIter.hasNext();) {
                    TrackGroup group = groupIter.next();

                    if (regionY > visibleRect.y + visibleRect.height) {
                        break;
                    }

                    if (group.isVisible()) {
                        if (groups.size() > 1) {
                            greyGraphics.fillRect(0, regionY + 1, getWidth(), UIConstants.groupGap - 1);
                            regionY += UIConstants.groupGap;
                        }


                        if (group.isDrawBorder()) {
                            g.drawLine(0, regionY - 1, getWidth(), regionY - 1);
                        }

                        for (Track track : group.getTracks()) {
                            if(track == null) continue;
                            int trackHeight = track.getHeight();
                            if (regionY > visibleRect.y + visibleRect.height) {
                                break;
                            }

                            if (track.isVisible()) {
                                if (regionY + trackHeight >= visibleRect.y) {
                                    regionY = draw(keys, track, regionX, regionY, attributeColumnWidth, track.getHeight(), graphics2D);
                                } else {
                                    regionY += trackHeight;
                                }
                            }
                        }

                        if (group.isDrawBorder()) {
                            g.drawLine(0, regionY, getWidth(), regionY);
                        }
                    }
                }
            }
        }
    }


    private Color getColor(String attKey, String attValue) {

        if (attValue == null || attValue.length() == 0) {
            return Color.white;
        }

        String key = (attKey + "_" + attValue).toLowerCase();
        Color c = colorMap.get(key);
        if (c == null) {

            Integer cnt = colorCounter.get(attKey);
            if (cnt == null) {
                cnt = 0;
            }
            cnt++;
            colorCounter.put(attKey, cnt);
            float hue = (float) (.4 + 0.2 * Math.random());

            // int index = colorMap.size() + 1;
            c = randomColor(cnt);
            colorMap.put(key, c);
        }
        return c;
    }

    Map<String, Integer> colorCounter = new HashMap();

    /**
     * Method description
     *
     * @param idx
     * @return
     */
    public static Color randomColor(int idx) {
        float hue = (float) Math.random();
        float sat = (float) (0.8 * Math.random());
        float bri = (float) (0.6 + 0.4 * Math.random());
        return Color.getHSBColor(hue, sat, bri);
    }

    private int draw(List<String> keys, Track track, int trackX, int trackY, int trackWidth, int trackHeight,
                     Graphics2D graphics) {

        for (String key : keys) {

            String attributeValue = track.getAttributeValue(key);

            if (attributeValue != null) {

                Rectangle trackRectangle = new Rectangle(trackX, trackY, trackWidth, trackHeight);
                graphics.setColor(getColor(key, attributeValue));
                graphics.fill(trackRectangle);
                addMousableRegion(new MouseableRegion(trackRectangle, key, attributeValue));
            }
            trackX += trackWidth + AttributeHeaderPanel.COLUMN_BORDER_WIDTH;
        }
        trackY += trackHeight;

        return trackY;
    }

    private void init() {

        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (!PreferenceManager.getInstance().getAsBoolean(PreferenceManager.SHOW_ATTRIBUTE_VIEWS_KEY)) {
            setSize(0, getHeight());
        }
        setBackground(new java.awt.Color(255, 255, 255));
        setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(0, 0, 0)));
        setPreferredSize(new java.awt.Dimension(0, 0));
        setVerifyInputWhenFocusTarget(false);

        MouseInputAdapter mouseAdapter = new AttributePanelMouseAdapter();
        addMouseMotionListener(mouseAdapter);
        addMouseListener(mouseAdapter);
    }

    /**
     * Method description
     *
     * @param x
     * @param y
     * @return
     */
    public String getPopupMenuTitle(int x, int y) {
        Collection<Track> selectedTracks = getSelectedTracks();
        int selectedTrackCount = selectedTracks.size();
        if (selectedTrackCount == 1) {
            return selectedTracks.iterator().next().getName();
        } else {
            String keyValue = "";
            for (MouseableRegion region : this.getTrackRegions()) {
                if (region.containsPoint(x, y)) {
                    keyValue = region.getText();
                }
            }
            return keyValue + " (" + selectedTrackCount + " tracks)";
        }
    }

    /**
     * Method description
     *
     * @param x
     * @param y
     * @return
     */
    public String getMouseDoc(int x, int y) {

        List<MouseableRegion> mouseRegions = getTrackRegions();

        for (MouseableRegion mr : mouseRegions) {
            if (mr.containsPoint(x, y)) {
                return mr.getText();
            }
        }
        return "";
    }

    /**
     * Method description
     *
     * @return
     */
    public int getAttributeColumnWidth() {
        return AttributeHeaderPanel.ATTRIBUTE_COLUMN_WIDTH;
    }

    // Packable interface


    private int calculatePackWidth() {

        if (!PreferenceManager.getInstance().getAsBoolean(PreferenceManager.SHOW_ATTRIBUTE_VIEWS_KEY)) {
            return 0;
        }

        HashSet<String> attributeKeys = new HashSet(AttributeManager.getInstance().getAttributeKeys());
        attributeKeys.removeAll(AttributeManager.getInstance().getHiddenAttributes());

        int attributeCount = attributeKeys.size();
        int packWidth = (attributeCount) * (AttributeHeaderPanel.ATTRIBUTE_COLUMN_WIDTH +
                AttributeHeaderPanel.COLUMN_BORDER_WIDTH) + AttributeHeaderPanel.COLUMN_BORDER_WIDTH;
        return packWidth;
    }

    /**
     * Method description
     *
     * @param x
     * @param y
     * @param width
     * @param height
     */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, calculatePackWidth(), height);
    }

    /**
     * Method description
     */
    public void packComponent() {
        int newWidth = calculatePackWidth();

        Dimension dimension = getSize();
        dimension = new Dimension(newWidth, dimension.height);
        setMinimumSize(dimension);
        setMaximumSize(dimension);
        setSize(dimension);
        setPreferredSize(dimension);

    }

    class AttributePanelMouseAdapter extends MouseInputAdapter {

        int lastMousePressX = 0;

        public void mousePressed(final MouseEvent e) {

            if (log.isDebugEnabled()) {
                log.debug("Enter mousePressed");
            }
            clearTrackSelections();
            selectTracks(e);
            if (e.isPopupTrigger()) {
                TrackClickEvent te = new TrackClickEvent(e, null);
                openPopupMenu(te);

            }
            IGVMainFrame.getInstance().repaintNamePanels();
        }

        public void mouseReleased(MouseEvent e) {
            // Show Popup Menu.  The track selection is cleared afterwards.
            // Note: clearing after this operation is "non standard", at least on the mac
            if (e.isPopupTrigger()) {
                TrackClickEvent te = new TrackClickEvent(e, null);
                openPopupMenu(te);
                clearTrackSelections();
            }

        }

        public void mouseMoved(MouseEvent e) {
            setToolTipText(getMouseDoc(e.getX(), e.getY()));
        }
    }

    ;
}
