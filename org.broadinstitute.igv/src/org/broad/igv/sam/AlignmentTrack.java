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
package org.broad.igv.sam;

//~--- non-JDK imports --------------------------------------------------------

import com.jidesoft.swing.JidePopupMenu;
import org.apache.log4j.Logger;
import org.broad.igv.Globals;
import org.broad.igv.PreferenceManager;
import org.broad.igv.feature.FeatureUtils;
import org.broad.igv.lists.GeneList;
import org.broad.igv.renderer.GraphicUtils;
import org.broad.igv.renderer.Renderer;
import org.broad.igv.session.Session;
import org.broad.igv.tdf.TDFDataSource;
import org.broad.igv.tdf.TDFReader;
import org.broad.igv.track.*;
import org.broad.igv.ui.IGVMainFrame;
import org.broad.igv.ui.InsertSizeSettingsDialog;
import org.broad.igv.ui.panel.*;
import org.broad.igv.ui.util.FileChooserDialog;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.ui.util.UIUtilities;
import org.broad.igv.util.ColorUtilities;
import org.broad.igv.util.ResourceLocator;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;

/**
 * @author jrobinso
 */
public class AlignmentTrack extends AbstractTrack implements DragListener {

    public enum SortOption {
        START, STRAND, NUCELOTIDE, QUALITY, SAMPLE, READ_GROUP, INSERT_SIZE
    }

    public enum ColorOption {
        INSERT_SIZE, READ_STRAND, FRAGMENT_STRAND, PAIR_ORIENTATION, SAMPLE, READ_GROUP;
    }


    public static final int MIN_ALIGNMENT_SPACING = 10;
    static final ColorOption DEFAULT_COLOR_OPTION = ColorOption.INSERT_SIZE;
    static final boolean DEFAULT_SHOWALLBASES = false;

    private static ColorOption colorByOption = null;

    private SequenceTrack sequenceTrack;
    private CoverageTrack coverageTrack;
    private SpliceJunctionFinderTrack spliceJunctionTrack;

    private RenderOptions renderOptions;

    private static Logger log = Logger.getLogger(AlignmentTrack.class);
    private int expandedHeight = 14;
    private int maxCollapsedHeight = 4;
    private int collapsedHeight = maxCollapsedHeight;

    private FeatureRenderer renderer;
    private double minVisibleScale = 25;
    private Rectangle renderedRect;
    private HashMap<String, Color> selectedReadNames = new HashMap();
    private int selectionColorIndex = 0;
    private int minHeight = 100;
    private AlignmentDataManager dataManager;

    public AlignmentTrack(ResourceLocator locator, AlignmentDataManager dataManager) {
        super(locator);

        this.dataManager = dataManager;

        PreferenceManager prefs = PreferenceManager.getInstance();

        float maxRange = prefs.getAsFloat(PreferenceManager.SAM_MAX_VISIBLE_RANGE);
        minVisibleScale = (maxRange * 1000) / 700;

        renderer = new AlignmentRenderer();

        this.setDisplayMode(DisplayMode.EXPANDED);

        if (prefs.getAsBoolean(PreferenceManager.SAM_SHOW_REF_SEQ)) {
            sequenceTrack = new SequenceTrack("Reference sequence");
            sequenceTrack.setHeight(14);
        }

        renderOptions = new RenderOptions();


        if (colorByOption == null) {
            String colorByString = PreferenceManager.getInstance().get(PreferenceManager.SAM_COLOR_BY);
            if (colorByString == null) {
                colorByOption = DEFAULT_COLOR_OPTION;
            } else {
                try {
                    colorByOption = ColorOption.valueOf(colorByString);
                }
                catch (Exception e) {
                    log.error("Error setting color option", e);
                    colorByOption = DEFAULT_COLOR_OPTION;

                }
            }
        }
    }


    public void setCoverageTrack(CoverageTrack coverageTrack) {
        this.coverageTrack = coverageTrack;
    }

    public CoverageTrack getCoverageTrack() {
        return coverageTrack;
    }

    public void setSpliceJunctionTrack(SpliceJunctionFinderTrack spliceJunctionTrack) {
        this.spliceJunctionTrack = spliceJunctionTrack;
    }

    public SpliceJunctionFinderTrack getSpliceJunctionTrack() {
        return spliceJunctionTrack;
    }

    public void setRenderer(FeatureRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void setHeight(int preferredHeight) {
        super.setHeight(preferredHeight);
        minHeight = preferredHeight;
    }

    /**
     * Method description
     *
     * @return
     */
    @Override
    public int getHeight() {
        int h = Math.max(minHeight, getNLevels() * (getRowHeight()) + 20);
        return h;
    }

    private int getRowHeight() {
        return getDisplayMode() == DisplayMode.EXPANDED ? expandedHeight : collapsedHeight;
    }

    private int getNLevels() {
        return dataManager.getNLevels();
    }

    @Override
    public int getPreferredHeight() {
        return Math.max(100, getHeight());
    }


    public void render(RenderContext context, Rectangle rect) {


        // Split rects
        int seqHeight = sequenceTrack == null ? 0 : sequenceTrack.getHeight();
        if (seqHeight > 0) {
            Rectangle seqRect = new Rectangle(rect);
            seqRect.height = seqHeight;
            sequenceTrack.render(context, seqRect);
        }

        int gap = (seqHeight > 0 ? seqHeight : 6);

        rect.y += gap;
        rect.height -= gap;
        renderedRect = new Rectangle(rect);

        if (context.getScale() > minVisibleScale) {

            Graphics2D g = context.getGraphic2DForColor(Color.gray);
            GraphicUtils.drawCenteredText("Zoom in to see alignments.", context.getVisibleRect(), g);
            return;

        }

        renderFeatures(context, rect);
    }

    private void renderFeatures(RenderContext context, Rectangle inputRect) {
        try {
            log.debug("Render features");
            List<AlignmentInterval.Row> tmp = dataManager.getAlignmentRows(context); //genomeId, chr, start, end);

            Map<String, PEStats> peStats = dataManager.getPEStats();
            if (peStats != null) {
                renderOptions.peStats = peStats;
            }

            if (tmp == null) {
                return;
            }


            Rectangle visibleRect = context.getVisibleRect();

            // Divide rectangle into equal height levels
            double y = inputRect.getY();
            double h = expandedHeight;
            if (getDisplayMode() != DisplayMode.EXPANDED) {
                int visHeight = context.getVisibleRect().height;
                int depth = dataManager.getMaxLevels();
                collapsedHeight = Math.min(maxCollapsedHeight, Math.max(1, Math.min(expandedHeight, visHeight / depth)));
                h = collapsedHeight;
            }

            int levelNumber = 0;
            for (AlignmentInterval.Row row : tmp) {

                if ((visibleRect != null && y > visibleRect.getMaxY())) {
                    return;
                }

                if (y + h > visibleRect.getY()) {
                    Rectangle rect = new Rectangle(inputRect.x, (int) y, inputRect.width, (int) h);
                    renderOptions.colorOption = colorByOption;
                    renderer.renderAlignments(row.alignments,
                            context,
                            rect,
                            renderOptions,
                            getDisplayMode() == DisplayMode.EXPANDED,
                            selectedReadNames);
                }
                levelNumber++;
                y += h;
            }
        } catch (Exception ex) {
            log.error("Error rendering track", ex);
            throw new RuntimeException("Error rendering track ", ex);

        }

    }

    public void clearCaches() {
        dataManager.clear();
        renderOptions = new RenderOptions();
    }


    /**
     * Sort alignment rows such that alignments that intersect from the
     * center appear left to right by start position
     */
    public void sortRows(SortOption option, ReferenceFrame referenceFrame) {
        if (option == SortOption.STRAND || option == SortOption.READ_GROUP || option == SortOption.SAMPLE) {
            dataManager.repackAlignments(referenceFrame, option);
        } else {
            dataManager.sortRows(option, referenceFrame);
        }
    }

    public void sortRows(SortOption option, ReferenceFrame referenceFrame, double location) {

        if (option == SortOption.STRAND || option == SortOption.READ_GROUP || option == SortOption.SAMPLE) {
            dataManager.repackAlignments(referenceFrame, option);
        } else {
            dataManager.sortRows(option, referenceFrame, location);
        }
    }


    public void packAlignments(ReferenceFrame referenceFrame) {
        dataManager.repackAlignments(referenceFrame);
    }

    /**
     * Copy the contents of the popup text to the system clipboard.
     */
    public void copyToClipboard(final TrackClickEvent e, Alignment alignment, double location) {

        if (alignment != null) {
            StringBuffer buf = new StringBuffer();
            buf.append(alignment.getValueString(location, null).replace("<br>", "\n"));
            buf.append("\n");
            buf.append("Alignment start position = " + alignment.getChr() + ":" + (alignment.getAlignmentStart() + 1));
            buf.append("\n");
            buf.append(alignment.getReadSequence());
            StringSelection stringSelection = new StringSelection(buf.toString());
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
        }

    }

    /**
     * Jump to the mate region
     */
    public void gotoMate(final TrackClickEvent te, Alignment alignment) {


        if (alignment != null) {
            ReadMate mate = alignment.getMate();
            if (mate != null && mate.isMapped()) {
                String chr = mate.getChr();
                int start = mate.start - 1;
                te.getFrame().centerOnLocation(chr, start);
                te.getFrame().recordHistory();
            }
        }
    }

    /**
     * Split the screen so the current view and mate region are side by side.  Need a better
     * name for this method.
     */
    public void splitScreenMate(final TrackClickEvent te, Alignment alignment) {

        if (alignment != null) {
            ReadMate mate = alignment.getMate();
            if (mate != null && mate.isMapped()) {
                String mateChr = mate.getChr();
                int mateStart = mate.start - 1;

                ReferenceFrame frame = te.getFrame();
                String locus1 = frame.getFormattedLocusString();

                // Generate a locus string for the read mate.  Keep the window width (in base pairs) == to the current range
                ReferenceFrame.Range range = frame.getCurrentRange();
                int length = range.getLength();
                int s2 = Math.max(0, mateStart - length / 2);
                int e2 = s2 + length;
                String startStr = NumberFormat.getInstance().format(s2);
                String endStr = NumberFormat.getInstance().format(e2);
                String mateLocus = mateChr + ":" + startStr + "-" + endStr;

                Session currentSession = IGVMainFrame.getInstance().getSession();

                // If we are already in gene list mode add the mate as another panel, otherwise switch to gl mode

                List<String> loci = null;
                if (FrameManager.isGeneListMode()) {
                    loci = new ArrayList(FrameManager.getFrames().size());
                    for(ReferenceFrame ref : FrameManager.getFrames()) {
                        loci.add(ref.getLocus().toString());
                    }
                    loci.add(mateLocus);
                } else {
                    loci = Arrays.asList(locus1, mateLocus);
                }
                GeneList.sortByPosition(loci);
                StringBuffer listName = new StringBuffer();
                for (String s : loci) {
                    listName.append(s + "   ");
                }

                GeneList geneList = new GeneList(listName.toString(), loci, false);
                currentSession.setCurrentGeneList(geneList);
                IGVMainFrame.getInstance().resetFrames();

            }
        }
    }


    public void setWindowFunction(WindowFunction type) {
        // ignored
    }

    public WindowFunction getWindowFunction() {
        return null;
    }

    public void setRendererClass(Class rc) {
        // ignored
    }

    // SamTracks use posA custom renderer, not derived from Renderer

    public Renderer getRenderer() {
        return null;
    }

    public boolean isLogNormalized() {
        return false;
    }

    public float getRegionScore(String chr, int start, int end, int zoom, RegionScoreType type, ReferenceFrame frame) {
        return 0.0f;
    }

    public String getValueStringAt(String chr, double position, int y, ReferenceFrame frame) {

        Alignment feature = getAlignmentAt(position, y, frame);

        // TODO -- highlight mate

        String tmp = (feature == null) ? null : feature.getValueString(position, getWindowFunction());

        return tmp;
    }

    private Alignment getAlignmentAt(double position, int y, ReferenceFrame frame) {

        List<AlignmentInterval.Row> alignmentRows = dataManager.getAlignmentRows(frame);

        if (alignmentRows == null || alignmentRows.isEmpty()) {
            return null;
        }

        int h = getDisplayMode() == DisplayMode.EXPANDED ? expandedHeight : collapsedHeight;
        int levelNumber = (y - renderedRect.y) / h;
        if (levelNumber < 0 || levelNumber >= alignmentRows.size()) {
            return null;
        }

        AlignmentInterval.Row row = alignmentRows.get(levelNumber);
        List<Alignment> features = row.alignments;

        // give posA 2 pixel window, otherwise very narrow features will be missed.
        double bpPerPixel = frame.getScale();
        double minWidth = 2 * bpPerPixel;    /* * */
        return (Alignment) FeatureUtils.getFeatureAt(position, minWidth, features);

    }

    public void dragStopped(DragEvent evt) {
        // Disabled.  Not sure why we ever thought this was posA good idea
        //if (PreferenceManager.getInstance().getSAMPreferences().isAutosort() &&
        //        ReferenceFrame.getInstance().getScale() < 1) {
        //    sortRows(SortOption.START);
        //    IGVMainFrame.getInstance().repaintDataPanels();
        //}
    }

    private static Alignment getFeatureContaining(
            List<Alignment> features, int right) {

        int leftBounds = 0;
        int rightBounds = features.size() - 1;
        int idx = features.size() / 2;
        int lastIdx = -1;

        while (idx != lastIdx) {
            lastIdx = idx;
            Alignment f = features.get(idx);
            if (f.contains(right)) {
                return f;
            }

            if (f.getStart() > right) {
                rightBounds = idx;
                idx = (leftBounds + idx) / 2;
            } else {
                leftBounds = idx;
                idx = (rightBounds + idx) / 2;

            }

        }
        // Check the extremes
        if (features.get(0).contains(right)) {
            return features.get(0);
        }

        if (features.get(rightBounds).contains(right)) {
            return features.get(rightBounds);
        }

        return null;
    }

    @Override
    public boolean handleDataClick(TrackClickEvent te) {
        MouseEvent e = te.getMouseEvent();

        if (e.isShiftDown() || e.isAltDown() || (e.getClickCount() > 1)) {
            return super.handleDataClick(te);
        } else if (e.getButton() == MouseEvent.BUTTON1 &&
                (Globals.IS_MAC && e.isMetaDown() || (!Globals.IS_MAC && e.isControlDown()))) {
            double location = te.getFrame().getChromosomePosition(e.getX());
            double displayLocation = location + 1;
            Alignment alignment = this.getAlignmentAt(displayLocation, e.getY(), te.getFrame());
            if (alignment != null) {
                if (selectedReadNames.containsKey(alignment.getReadName())) {
                    selectedReadNames.remove(alignment.getReadName());
                } else {
                    Color c = alignment.isPaired() && alignment.getMate() != null && alignment.getMate().isMapped() ?
                            ColorUtilities.randomColor(selectionColorIndex++) : Color.black;
                    selectedReadNames.put(alignment.getReadName(), c);
                }
                Object source = e.getSource();
                if (source instanceof JComponent) {
                    ((JComponent) source).repaint();
                }
            }
            return true;

        }
        return false;
    }


    public JPopupMenu getPopupMenu(final TrackClickEvent e) {

        JPopupMenu popupMenu = new JidePopupMenu();

        JLabel popupTitle = new JLabel("  " + getName(), JLabel.CENTER);

        Font newFont = popupMenu.getFont().deriveFont(Font.BOLD, 12);
        popupTitle.setFont(newFont);
        if (popupTitle != null) {
            popupMenu.add(popupTitle);
        }

        addSortMenuItem(popupMenu, e.getFrame());
        addPackMenuItem(popupMenu, e.getFrame());
        addCoverageDepthMenuItem(popupMenu);
        popupMenu.addSeparator();
        addColorByMenuItem(popupMenu);

        addShadeBaseMenuItem(popupMenu);
        addShadeCentersMenuItem(popupMenu);

        popupMenu.addSeparator();
        if (dataManager.isPairedEnd()) {
            addViewAsPairsMenuItem(popupMenu, e);
            addGoToMate(popupMenu, e);
        }
        showMateRegion(popupMenu, e);
        addShowAllBasesMenuItem(popupMenu);
        addInsertSizeMenuItem(popupMenu);

        popupMenu.addSeparator();
        addShowCoverageItem(popupMenu);
        addLoadCoverageDataItem(popupMenu);

        addCopyToClipboardItem(popupMenu, e);
        popupMenu.addSeparator();

        addSelecteByNameItem(popupMenu);
        popupMenu.addSeparator();

        JLabel trackSettingsHeading = new JLabel("  Track Settings", JLabel.LEFT);
        trackSettingsHeading.setFont(newFont);

        popupMenu.add(trackSettingsHeading);


        Collection<Track> tmp = new ArrayList();
        tmp.add(this);
        popupMenu.add(TrackMenuUtils.getTrackRenameItem(tmp));

        TrackMenuUtils.addDisplayModeItems(tmp, popupMenu);

        popupMenu.add(TrackMenuUtils.getRemoveMenuItem(tmp));

        popupMenu.addSeparator();
        addClearSelectionsMenuItem(popupMenu);

        return popupMenu;
    }

    public void addSelecteByNameItem(JPopupMenu menu) {
        // Change track height by attribute
        JMenuItem item = new JMenuItem("Select by name...");
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {
                String val = MessageUtils.showInputDialog("Enter read name: ");
                if (val != null && val.trim().length() > 0) {
                    selectedReadNames.put(val, ColorUtilities.randomColor(selectedReadNames.size() + 1));
                    refresh();
                }
            }
        });

        menu.add(item);
    }

    public void addSortMenuItem(JPopupMenu menu, final ReferenceFrame frame) {
        // Change track height by attribute
        JMenu item = new JMenu("Sort alignments");

        JMenuItem m1 = new JMenuItem("by start location");
        m1.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {
                IGVMainFrame.getInstance().getTrackManager().sortAlignmentTracks(SortOption.START);
                refresh();

            }
        });
        item.add(m1);

        JMenuItem m2 = new JMenuItem("by strand");
        m2.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {
                IGVMainFrame.getInstance().getTrackManager().sortAlignmentTracks(SortOption.STRAND);
                refresh();

            }
        });
        item.add(m2);

        JMenuItem m3 = new JMenuItem("by base");
        m3.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {

                IGVMainFrame.getInstance().getTrackManager().sortAlignmentTracks(SortOption.NUCELOTIDE);
                refresh();

            }
        });
        item.add(m3);

        JMenuItem m4 = new JMenuItem("by mapping quality");
        m4.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {

                IGVMainFrame.getInstance().getTrackManager().sortAlignmentTracks(SortOption.QUALITY);
                refresh();

            }
        });
        item.add(m4);


        JMenuItem m5 = new JMenuItem("by sample");
        m5.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {

                IGVMainFrame.getInstance().getTrackManager().sortAlignmentTracks(SortOption.SAMPLE);
                refresh();

            }
        });
        item.add(m5);

        JMenuItem m6 = new JMenuItem("by read group");
        m6.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {

                IGVMainFrame.getInstance().getTrackManager().sortAlignmentTracks(SortOption.READ_GROUP);
                refresh();

            }
        });
        item.add(m6);

        if (dataManager.isPairedEnd()) {
            JMenuItem m7 = new JMenuItem("by insert size");
            m7.addActionListener(new ActionListener() {

                public void actionPerformed(ActionEvent aEvt) {

                    IGVMainFrame.getInstance().getTrackManager().sortAlignmentTracks(SortOption.INSERT_SIZE);
                    refresh();

                }
            });
            item.add(m7);
        }


        if (frame != null && frame.getScale() >= MIN_ALIGNMENT_SPACING) {
            item.setEnabled(false);
        }


        menu.add(item);
    }


    private void setColorOption(ColorOption option) {
        colorByOption = option;
        PreferenceManager.getInstance().put(PreferenceManager.SAM_COLOR_BY, option.toString());
    }

    public void addColorByMenuItem(JPopupMenu menu) {
        // Change track height by attribute
        JMenu colorMenu = new JMenu("Color alignments");

        ButtonGroup group = new ButtonGroup();

        if (dataManager.isPairedEnd()) {
            JRadioButtonMenuItem m1 = new JRadioButtonMenuItem("by insert size");
            m1.setSelected(colorByOption == ColorOption.INSERT_SIZE);
            m1.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent aEvt) {
                    setColorOption(ColorOption.INSERT_SIZE);
                    refresh();
                }
            });
            colorMenu.add(m1);
            group.add(m1);

            JRadioButtonMenuItem m1a = new JRadioButtonMenuItem("by pair orientation");
            m1a.setSelected(colorByOption == ColorOption.PAIR_ORIENTATION);
            m1a.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent aEvt) {
                    setColorOption(ColorOption.PAIR_ORIENTATION);
                    refresh();
                }
            });
            colorMenu.add(m1a);
            group.add(m1a);
        }

        JRadioButtonMenuItem m2 = new JRadioButtonMenuItem("by read strand");
        m2.setSelected(colorByOption == ColorOption.READ_STRAND);
        m2.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent aEvt) {
                setColorOption(ColorOption.READ_STRAND);
                refresh();
            }
        });
        colorMenu.add(m2);
        group.add(m2);

        JRadioButtonMenuItem m4 = new JRadioButtonMenuItem("by read group");
        m4.setSelected(colorByOption == ColorOption.READ_GROUP);
        m4.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent aEvt) {
                setColorOption(ColorOption.READ_GROUP);
                refresh();
            }
        });
        colorMenu.add(m4);
        group.add(m4);

        JRadioButtonMenuItem m5 = new JRadioButtonMenuItem("by sample");
        m5.setSelected(colorByOption == ColorOption.SAMPLE);
        m5.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent aEvt) {
                setColorOption(ColorOption.SAMPLE);
                refresh();
            }
        });
        colorMenu.add(m5);
        group.add(m5);


        menu.add(colorMenu);

    }


    public void addPackMenuItem(JPopupMenu menu, final ReferenceFrame frame) {
        // Change track height by attribute
        JMenuItem item = new JMenuItem("Re-pack alignments");
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {
                UIUtilities.invokeOnEventThread(new Runnable() {

                    public void run() {
                        IGVMainFrame.getInstance().getTrackManager().packAlignmentTracks();
                        refresh();
                    }
                });
            }
        });

        menu.add(item);
    }

    public void addCopyToClipboardItem(JPopupMenu menu, final TrackClickEvent te) {

        final MouseEvent me = te.getMouseEvent();
        final double location = te.getFrame().getChromosomePosition(me.getX());
        double displayLocation = location + 1;
        final Alignment alignment = getAlignmentAt(displayLocation, me.getY(), te.getFrame());

        // Change track height by attribute
        JMenuItem item = new JMenuItem("Copy read details to clipboard");
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {
                copyToClipboard(te, alignment, location);

            }
        });
        if (te.getFrame() == null || alignment == null) {
            item.setEnabled(false);
        }

        menu.add(item);
    }

    public void addViewAsPairsMenuItem(JPopupMenu menu, final TrackClickEvent te) {
        // Change track height by attribute
        final JMenuItem item = new JCheckBoxMenuItem("View as pairs");
        item.setSelected(dataManager.isLoadAsPairs());
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent aEvt) {

                dataManager.setLoadAsPairs(item.isSelected());
                refresh();

            }
        });
        menu.add(item);
    }

    public void addGoToMate(JPopupMenu menu, final TrackClickEvent te) {
        // Change track height by attribute
        JMenuItem item = new JMenuItem("Go to mate");
        MouseEvent e = te.getMouseEvent();
        double location = te.getFrame().getChromosomePosition(e.getX());
        double displayLocation = location + 1;
        final Alignment alignment = getAlignmentAt(displayLocation, e.getY(), te.getFrame());
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent aEvt) {
                gotoMate(te, alignment);
            }
        });
        if (te.getFrame() == null || alignment == null || !alignment.isPaired() || !alignment.getMate().isMapped()) {
            item.setEnabled(false);
        }
        menu.add(item);
    }


    public void showMateRegion(JPopupMenu menu, final TrackClickEvent te) {
        // Change track height by attribute
        JMenuItem item = new JMenuItem("View mate region in split screen");
        MouseEvent e = te.getMouseEvent();
        double location = te.getFrame().getChromosomePosition(e.getX());
        double displayLocation = location + 1;
        final Alignment alignment = getAlignmentAt(displayLocation, e.getY(), te.getFrame());

        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent aEvt) {
                splitScreenMate(te, alignment);
            }
        });
        if (te.getFrame() == null || alignment == null || !alignment.isPaired() || !alignment.getMate().isMapped()) {
            item.setEnabled(false);
        }
        menu.add(item);
    }

    public void addClearSelectionsMenuItem(JPopupMenu menu) {
        // Change track height by attribute
        JMenuItem item = new JMenuItem("Clear selections");
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {
                selectedReadNames.clear();
                refresh();
            }
        });

        menu.add(item);
    }

    public void addShowAllBasesMenuItem(JPopupMenu menu) {
        // Change track height by attribute
        final JMenuItem item = new JCheckBoxMenuItem("Show all bases");
        item.setSelected(renderOptions.showAllBases);
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent aEvt) {
                renderOptions.showAllBases = item.isSelected();
                refresh();
            }
        });
        menu.add(item);
    }

    public void addCoverageDepthMenuItem(JPopupMenu menu) {
        // Change track height by attribute
        final JMenuItem item = new JCheckBoxMenuItem("Set maximum coverage depth ...");
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {
                int maxLevels = dataManager.getMaxLevels();
                String val = MessageUtils.showInputDialog("Maximum coverage depth", String.valueOf(maxLevels));
                try {
                    int newMaxLevels = Integer.parseInt(val);
                    if (newMaxLevels != maxLevels) {
                        dataManager.setMaxLevels(newMaxLevels);
                        //dataManager.reload();
                        refresh();
                    }
                }
                catch (NumberFormatException ex) {
                    MessageUtils.showMessage("Insert size must be an integer value: " + val);
                }

            }
        });


        menu.add(item);
    }

    public void addInsertSizeMenuItem(JPopupMenu menu) {
        // Change track height by attribute
        final JMenuItem item = new JCheckBoxMenuItem("Set insert size options ...");
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {

                InsertSizeSettingsDialog dlg = new InsertSizeSettingsDialog(IGVMainFrame.getInstance(), renderOptions);
                dlg.setModal(true);
                dlg.setVisible(true);
                if (!dlg.isCanceled()) {
                    renderOptions.setComputeIsizes(dlg.isComputeIsize());
                    //renderOptions.setMinPercentile(dlg.getMinPercentile());
                    // renderOptions.setMaxPercentile(dlg.getMaxPercentile());
                    renderOptions.setMinInsertSizeThreshold(dlg.getMinThreshold());
                    renderOptions.setMaxInsertSizeThreshold(dlg.getMaxThreshold());
                    refresh();
                }
            }
        });

        if (dataManager.isPairedEnd()) {
            menu.add(item);
        }
    }

    private void refresh() {
        IGVMainFrame.getInstance().repaintDataPanels();
    }

    public void addShadeBaseMenuItem(JPopupMenu menu) {
        // Change track height by attribute
        final JMenuItem item = new JCheckBoxMenuItem("Shade base by quality");
        item.setSelected(renderOptions.shadeBases);
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {
                UIUtilities.invokeOnEventThread(new Runnable() {

                    public void run() {
                        renderOptions.shadeBases = item.isSelected();
                        refresh();
                    }
                });
            }
        });

        menu.add(item);
    }

    public void addShadeCentersMenuItem(JPopupMenu menu) {
        // Change track height by attribute
        final JMenuItem item = new JCheckBoxMenuItem("Shade alignments intersecting center");
        item.setSelected(renderOptions.shadeCenters);
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {
                UIUtilities.invokeOnEventThread(new Runnable() {

                    public void run() {

                        renderOptions.shadeCenters = item.isSelected();
                        refresh();
                    }
                });
            }
        });

        menu.add(item);
    }


    public void addShowCoverageItem(JPopupMenu menu) {
        // Change track height by attribute
        final JMenuItem item = new JCheckBoxMenuItem("Show coverage track");
        item.setSelected(getCoverageTrack() != null && getCoverageTrack().isVisible());
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {
                UIUtilities.invokeOnEventThread(new Runnable() {

                    public void run() {
                        if (getCoverageTrack() != null) {
                            getCoverageTrack().setVisible(item.isSelected());
                            refresh();
                            IGVMainFrame.getInstance().repaintNamePanels();
                        }
                    }
                });
            }
        });

        menu.add(item);
    }

    public void addLoadCoverageDataItem(JPopupMenu menu) {
        // Change track height by attribute
        final JMenuItem item = new JCheckBoxMenuItem("Load coverage data...");
        item.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent aEvt) {
                UIUtilities.invokeOnEventThread(new Runnable() {

                    public void run() {

                        FileChooserDialog trackFileDialog = IGVMainFrame.getInstance().getTrackFileChooser();
                        trackFileDialog.setMultiSelectionEnabled(false);
                        trackFileDialog.setVisible(true);
                        if (!trackFileDialog.isCanceled()) {
                            File file = trackFileDialog.getSelectedFile();
                            String path = file.getAbsolutePath();
                            if (path.endsWith(".tdf") || path.endsWith(".tdf")) {

                                TDFReader reader = TDFReader.getReader(file.getAbsolutePath());
                                TDFDataSource ds = new TDFDataSource(reader, 0, getName() + " coverage");
                                getCoverageTrack().setDataSource(ds);
                                refresh();
                            } else {
                                MessageUtils.showMessage("Coverage data must be in .tdf format");
                            }
                        }
                    }
                });
            }
        });

        menu.add(item);
    }

    @Override
    public Map<String, String> getPersistentState() {
        Map<String, String> attrs = super.getPersistentState();
        attrs.putAll(renderOptions.getPersistentState());
        return attrs;
    }

    @Override
    public void restorePersistentState(Map<String, String> attributes) {
        super.restorePersistentState(attributes);    //To change body of overridden methods use File | Settings | File Templates.
        renderOptions.restorePersistentState(attributes);
    }


    public static class RenderOptions {
        boolean shadeBases;
        boolean shadeCenters;
        boolean flagUnmappedPairs;
        boolean showAllBases;
        private boolean computeIsizes;
        private int minInsertSizeThreshold;
        private int maxInsertSizeThreshold;
        private double minPercentile = 0.1;
        private double maxPercentile = 99.9;
        ColorOption colorOption;
        //ContinuousColorScale insertSizeColorScale;
        private boolean viewPairs = false;
        Map<String, PEStats> peStats;

        RenderOptions() {
            PreferenceManager prefs = PreferenceManager.getInstance();
            shadeBases = prefs.getAsBoolean(PreferenceManager.SAM_SHADE_BASE_QUALITY);
            shadeCenters = prefs.getAsBoolean(PreferenceManager.SAM_SHADE_CENTER);
            flagUnmappedPairs = prefs.getAsBoolean(PreferenceManager.SAM_FLAG_UNMAPPED_PAIR);
            computeIsizes = prefs.getAsBoolean(PreferenceManager.SAM_COMPUTE_ISIZES);
            minInsertSizeThreshold = prefs.getAsInt(PreferenceManager.SAM_MIN_INSERT_SIZE_THRESHOLD);
            maxInsertSizeThreshold = prefs.getAsInt(PreferenceManager.SAM_INSERT_SIZE_THRESHOLD);
            showAllBases = DEFAULT_SHOWALLBASES;
            colorOption = colorByOption;
            //updateColorScale();
        }

        /*private void updateColorScale() {
            int delta = 1;
            if (medianInsertSize == 0 || madInsertSize == 0) {
                delta = (maxInsertSizeThreshold - minInsertSizeThreshold) / 10;
            } else {
                delta = Math.min((maxInsertSizeThreshold - minInsertSizeThreshold) / 3, madInsertSize);
            }
            insertSizeColorScale = new ContinuousColorScale(minInsertSizeThreshold, minInsertSizeThreshold + delta,
                    maxInsertSizeThreshold - delta, maxInsertSizeThreshold, Color.blue, AlignmentRenderer.grey1, Color.red);
        }*/

        /**
         * Called by session writer.  Return instance variable values as a map of strings.  Used to record current state
         * of object.   Variables with default values are not stored, as it is presumed the user has not changed them.
         *
         * @return
         */
        public Map<String, String> getPersistentState() {
            Map<String, String> attributes = new HashMap();
            PreferenceManager prefs = PreferenceManager.getInstance();
            if (shadeBases != prefs.getAsBoolean(PreferenceManager.SAM_SHADE_BASE_QUALITY)) {
                attributes.put("shadeBases", String.valueOf(shadeBases));
            }
            if (shadeCenters != prefs.getAsBoolean(PreferenceManager.SAM_SHADE_CENTER)) {
                attributes.put("shadeCenters", String.valueOf(shadeBases));
            }
            if (flagUnmappedPairs != prefs.getAsBoolean(PreferenceManager.SAM_FLAG_UNMAPPED_PAIR)) {
                attributes.put("flagUnmappedPairs", String.valueOf(flagUnmappedPairs));
            }
            if (maxInsertSizeThreshold != prefs.getAsInt(PreferenceManager.SAM_INSERT_SIZE_THRESHOLD)) {
                attributes.put("insertSizeThreshold", String.valueOf(maxInsertSizeThreshold));
            }
            if (getMinInsertSizeThreshold() != prefs.getAsInt(PreferenceManager.SAM_MIN_INSERT_SIZE_THRESHOLD)) {
                attributes.put("minInsertSizeThreshold", String.valueOf(maxInsertSizeThreshold));
            }
            if (showAllBases != DEFAULT_SHOWALLBASES) {
                attributes.put("showAllBases", String.valueOf(showAllBases));
            }
            if (colorOption != DEFAULT_COLOR_OPTION) {
                attributes.put("colorOption", colorByOption.toString());
            }

            return attributes;
        }

        /**
         * Called by session reader.  Restores state of object.
         *
         * @param attributes
         */
        public void restorePersistentState(Map<String, String> attributes) {

            String value;
            value = attributes.get("insertSizeThreshold");
            if (value != null) {
                maxInsertSizeThreshold = Integer.parseInt(value);
            }
            value = attributes.get("minInsertSizeThreshold");
            if (value != null) {
                setMinInsertSizeThreshold(Integer.parseInt(value));
            }
            value = attributes.get("shadeBases");
            if (value != null) {
                shadeBases = Boolean.parseBoolean(value);
            }
            value = attributes.get("shadeCenters");
            if (value != null) {
                shadeCenters = Boolean.parseBoolean(value);
            }
            value = attributes.get("flagUnmappedPairs");
            if (value != null) {
                flagUnmappedPairs = Boolean.parseBoolean(value);
            }
            value = attributes.get("showAllBases");
            if (value != null) {
                showAllBases = Boolean.parseBoolean(value);
            }
            value = attributes.get("colorOption");
            if (value != null) {
                colorOption = ColorOption.valueOf(value);
                colorByOption = colorOption;
            }
        }

        public int getMinInsertSizeThreshold() {
            return minInsertSizeThreshold;
        }

        public void setMinInsertSizeThreshold(int minInsertSizeThreshold) {
            this.minInsertSizeThreshold = minInsertSizeThreshold;
            //updateColorScale();
        }

        public int getMaxInsertSizeThreshold() {
            return maxInsertSizeThreshold;

        }


        public boolean isViewPairs() {
            return viewPairs;
        }

        public void setViewPairs(boolean viewPairs) {
            this.viewPairs = viewPairs;
        }

        public boolean isComputeIsizes() {
            return computeIsizes;
        }

        public void setComputeIsizes(boolean computeIsizes) {
            this.computeIsizes = computeIsizes;
        }

        public double getMinPercentile() {
            return minPercentile;
        }

        public void setMinPercentile(double minPercentile) {
            this.minPercentile = minPercentile;
        }

        public double getMaxPercentile() {
            return maxPercentile;
        }

        public void setMaxPercentile(double maxPercentile) {
            this.maxPercentile = maxPercentile;
        }

        public void setMaxInsertSizeThreshold(int maxInsertSizeThreshold) {
            this.maxInsertSizeThreshold = maxInsertSizeThreshold;
        }
    }


}
