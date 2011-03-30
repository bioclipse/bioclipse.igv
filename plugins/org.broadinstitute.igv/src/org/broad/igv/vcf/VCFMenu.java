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

package org.broad.igv.vcf;

import org.apache.log4j.Logger;
import org.broad.igv.track.Track;
import org.broad.igv.track.TrackClickEvent;
import org.broad.igv.track.TrackMenuUtils;
import org.broad.igv.ui.IGVMainFrame;
import org.broad.tribble.Feature;
import org.broad.tribble.util.variantcontext.Genotype;
import org.broad.tribble.util.variantcontext.VariantContext;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * User: Jesse Whitworth
 * Date: Jul 16, 2010
 */
public class VCFMenu extends JPopupMenu {

    private static Logger log = Logger.getLogger(VCFMenu.class);
    private VCFTrack track;
    Map<String, Genotype> sampleGenotypes;
    List<String> samples;

    static boolean depthSortingDirection;
    static boolean genotypeSortingDirection;
    static boolean sampleSortingDirection;
    static boolean qualitySortingDirection;

    public VCFMenu(VCFTrack t, TrackClickEvent te, VariantContext variant) {
        this.track = t;

        this.addPopupMenuListener(new PopupMenuListener() {
            public void popupMenuWillBecomeVisible(PopupMenuEvent popupMenuEvent) {

            }

            public void popupMenuWillBecomeInvisible(PopupMenuEvent popupMenuEvent) {
                close();
            }

            public void popupMenuCanceled(PopupMenuEvent popupMenuEvent) {
                close();
            }

            private void close() {
                track.clearSelectedVariant();
                IGVMainFrame.getInstance().repaint();
            }

        });

        samples = track.getAllSamples();
        if (samples != null) {
            sampleGenotypes = new HashMap<String, Genotype>();
            for (String sample : samples) {
                Genotype genotype = variant.getGenotype(sample);
                if (genotype != null) {
                    sampleGenotypes.put(sample, genotype);
                }
            }
        }


        //Title
        JLabel popupTitle = new JLabel("<html><b>" + track.getName(), JLabel.CENTER);
        Font newFont = getFont().deriveFont(Font.BOLD, 12);
        popupTitle.setFont(newFont);
        add(popupTitle);

        //Change Track Settings
        addSeparator();
        add(getFeatureVisibilityItem());

        //Hides
        addSeparator();
        JLabel hideHeading = new JLabel("<html>&nbsp;&nbsp;<b>Display Options", JLabel.LEFT);
        add(hideHeading);
        add(getColorMenuItem());
        add(getHideFilteredItem());
        //add(getRenderIDItem());

        //Sorter
        addSeparator();
        JLabel sortHeading = new JLabel("<html>&nbsp;&nbsp;<b>Sort Variant By", JLabel.LEFT);
        add(sortHeading);
        for (JMenuItem item : getSortMenuItems(te, variant)) {
            add(item);
        }

        //Variant Information
        addSeparator();
        JLabel displayHeading = new JLabel("Display Mode", JLabel.LEFT);
        add(displayHeading);
        for (JMenuItem item : getDisplayModeItems()) {
            add(item);
        }

        addSeparator();
        add(TrackMenuUtils.getRemoveMenuItem(Arrays.asList(new Track[]{track})));

    }

    private JMenuItem getFeatureVisibilityItem() {
        JMenuItem item = new JMenuItem("Set Feature Visibility Window...");
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                changeVisibilityWindow();
                IGVMainFrame.getInstance().getContentPane().repaint();
            }
        });
        return item;
    }

    public JMenu getColorMenuItem() {
        JMenu colorMenu = new JMenu("Color By");
        java.util.List<JMenuItem> items = new ArrayList<JMenuItem>();
        items.add(getColorByGenotype());
        items.add(getColorByAllele());
        for (JMenuItem item : items) {
            colorMenu.add(item);
        }
        return colorMenu;
    }

    private JMenuItem getColorByGenotype() {
        final JMenuItem item = new JCheckBoxMenuItem("Genotype", track.getColorMode() == VCFTrack.ColorMode.GENOTYPE);
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                track.setColorMode(VCFTrack.ColorMode.GENOTYPE);
                IGVMainFrame.getInstance().getContentPane().repaint();
            }
        });
        return item;
    }


    private JMenuItem getColorByAllele() {
        final JMenuItem item = new JCheckBoxMenuItem("Allele", track.getColorMode() == VCFTrack.ColorMode.ALLELE);
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                track.setColorMode(VCFTrack.ColorMode.ALLELE);
                IGVMainFrame.getInstance().getContentPane().repaint();
            }
        });
        return item;
    }

    private JMenuItem getRenderIDItem() {
        JMenuItem item = new JCheckBoxMenuItem("Display Variant Names", track.getRenderID());
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                track.setRenderID(!track.getRenderID());
                IGVMainFrame.getInstance().getContentPane().repaint();
            }
        });
        return item;
    }

    private JMenuItem getHideFilteredItem() {
        JMenuItem item = new JCheckBoxMenuItem("Suppress Filtered Sites", track.getHideFiltered());
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                track.setHideFiltered(!track.getHideFiltered());
                IGVMainFrame.getInstance().getContentPane().repaint();
            }
        });
        return item;
    }

    private JMenuItem getHideAncestralItem() {
        JMenuItem item = new JCheckBoxMenuItem("Suppress Ancestral Genotypes", track.getHideAncestral());
        item.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                track.setHideAncestral(!track.getHideAncestral());
                IGVMainFrame.getInstance().getContentPane().repaint();
            }
        });
        return item;
    }


    public JMenuItem getGenotypeSortItem(final VariantContext variant) {

        JMenuItem item = new JMenuItem("Genotype");
        try {
            variant.getAlleles();
            item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    GenotypeComparator compare = new GenotypeComparator();
                    genotypeSortingDirection = !genotypeSortingDirection;
                    sortSamples(variant, compare);
                    IGVMainFrame.getInstance().getContentPane().repaint();
                }
            });
        } catch (Exception e) {
            item.setEnabled(false);
        }
        return item;
    }

    public JMenuItem getSampleNameSortItem(final VariantContext variant) {
        JMenuItem item = new JMenuItem("Sample Name");
        try {
            variant.getAlleles();
            item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent evt) {
                    Comparator<String> compare = new Comparator<String>() {
                        public int compare(String o, String o1) {
                            if (sampleSortingDirection) {
                                return o.compareTo(o1);
                            } else {
                                return o1.compareTo(o);
                            }
                        }
                    };
                    sampleSortingDirection = !sampleSortingDirection;
                    sortSamples(variant, compare);
                    IGVMainFrame.getInstance().getContentPane().repaint();
                }
            });
        } catch (Exception e) {
            item.setEnabled(false);
        }
        return item;
    }

    public JMenuItem getDepthSortItem(final VariantContext variant) {
        JMenuItem item = new JMenuItem("Depth");
        try {
            String variantDepth = variant.getAttributeAsString("DP");
            int depth = Integer.valueOf(variantDepth);
            if (depth > -1) {
                item.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent evt) {
                        DepthComparator compare = new DepthComparator();
                        depthSortingDirection = !depthSortingDirection;
                        sortSamples(variant, compare);
                        IGVMainFrame.getInstance().getContentPane().repaint();
                    }
                });
                return item;
            }
        } catch (Exception e) {
            item.setEnabled(false);
        }
        return item;
    }

    public JMenuItem getQualitySortItem(final VariantContext variant) {
        JMenuItem item = new JMenuItem("Quality");
        try {
            double quality = variant.getPhredScaledQual();
            if (quality > -1) {
                item.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent evt) {
                        QualityComparator compare = new QualityComparator();
                        qualitySortingDirection = !qualitySortingDirection;
                        sortSamples(variant, compare);
                        IGVMainFrame.getInstance().getContentPane().repaint();
                    }
                });
            }
        } catch (Exception e) {
            item.setEnabled(false);
        }
        return item;
    }

    public void changeVisibilityWindow() {
        int value = getIntValue("Visibility Window", track.getVisibilityWindow());
        if (value > 0) {
            track.setVisibilityWindow(value);
        }
    }

    private static int getIntValue(String parameter, int value) {
        while (true) {
            String height = JOptionPane.showInputDialog(
                    IGVMainFrame.getInstance(), parameter + ": ",
                    String.valueOf(value));
            if ((height == null) || height.trim().equals("")) {
                return Integer.MIN_VALUE;   // <= the logical "null" value
            }

            try {
                value = Integer.parseInt(height);
                return value;
            } catch (NumberFormatException numberFormatException) {
                JOptionPane.showMessageDialog(IGVMainFrame.getInstance(),
                        parameter + " must be an integer number.");
            }
        }
    }

    private void sortSamples(VariantContext variant, Comparator<String> compare) {
        try {
            if ((sampleGenotypes.size() > 1)) {
                Collections.sort(samples, compare);
                track.setAllSamples(samples);
            }
        } catch (Exception e) {
            log.error(e);
        }
    }


    public Collection<JMenuItem> getSortMenuItems(TrackClickEvent te, Feature closestFeature) {

        java.util.List<JMenuItem> items = new ArrayList<JMenuItem>();
        VariantContext variant = (VariantContext) closestFeature;

        items.add(getGenotypeSortItem(variant));
        items.add(getSampleNameSortItem(variant));
        items.add(getDepthSortItem(variant));
        items.add(getQualitySortItem(variant));
        return items;
    }

    public List<JMenuItem> getDisplayModeItems() {

        List<JMenuItem> items = new ArrayList();

        ButtonGroup group = new ButtonGroup();

        Track.DisplayMode displayMode = track.getDisplayMode();

        JRadioButtonMenuItem m1 = new JRadioButtonMenuItem("Collapsed");
        m1.setSelected(displayMode == Track.DisplayMode.COLLAPSED);
        m1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                track.setDisplayMode(Track.DisplayMode.COLLAPSED);
                IGVMainFrame.getInstance().repaint();
            }
        });

        JRadioButtonMenuItem m2 = new JRadioButtonMenuItem("Squished");
        m2.setSelected(displayMode == Track.DisplayMode.SQUISHED);
        m2.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                track.setDisplayMode(Track.DisplayMode.SQUISHED);
                IGVMainFrame.getInstance().repaint();
            }
        });

        JRadioButtonMenuItem m3 = new JRadioButtonMenuItem("Expanded");
        m3.setSelected(displayMode == Track.DisplayMode.EXPANDED);
        m3.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                track.setDisplayMode(Track.DisplayMode.EXPANDED);
                IGVMainFrame.getInstance().repaint();
            }
        });


        items.add(m1);
        items.add(m2);
        items.add(m3);
        group.add(m1);
        group.add(m2);
        group.add(m3);

        return items;
    }


    class GenotypeComparator implements Comparator<String> {

        public int compare(String e1, String e2) {
            int genotype1 = classifyGenotype(sampleGenotypes.get(e1));
            int genotype2 = classifyGenotype(sampleGenotypes.get(e2));

            if (genotype2 == genotype1) {
                return 0;
            } else if (genotype2 > genotype1) {
                return genotypeSortingDirection ? 1 : -1;
            } else {
                return genotypeSortingDirection ? -1 : 1;
            }
        }
    }


    class DepthComparator implements Comparator<String> {

        public int compare(String s1, String s2) {


            String readDepth1 = sampleGenotypes.get(s1).getAttributeAsString("DP");
            String readDepth2 = sampleGenotypes.get(s2).getAttributeAsString("DP");

            double depth1;
            try {
                depth1 = Double.valueOf(readDepth1);
            } catch (Exception e) {
                depth1 = -1;
            }
            double depth2;
            try {
                depth2 = Double.valueOf(readDepth2);
            } catch (Exception e) {
                depth2 = -1;
            }

            if (depth2 == depth1) {
                return 0;
            } else if (depth2 < depth1) {
                return depthSortingDirection ? -1 : 1;
            } else {
                return depthSortingDirection ? 1 : 1;
            }
        }
    }

    class QualityComparator implements Comparator<String> {

        public int compare(String s1, String s2) {

            double qual1 = sampleGenotypes.get(s1).getPhredScaledQual();
            double qual2 = sampleGenotypes.get(s2).getPhredScaledQual();

            if (qual2 == qual1) {
                return 0;
            } else if (qual2 < qual1) {
                return qualitySortingDirection ? -1 : 1;
            } else {
                return qualitySortingDirection ? 1 : 1;
            }
        }
    }

    private int classifyGenotype(Genotype genotype) {

        if (genotype.isNoCall()) {
            return 0;
        } else if (genotype.isHomVar()) {
            return 3;
        } else if (genotype.isHet()) {
            return 2;
        } else if (genotype.isHomRef()) {
            return 1;
        }
        return -1; //Unknown
    }


}
