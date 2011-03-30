/**
 * Copyright (c) 2010-2011 by Fred Hutchinson Cancer Research Center.  All Rights Reserved.

 * This software is licensed under the terms of the GNU Lesser General
 * Public License (LGPL), Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.

 * THE SOFTWARE IS PROVIDED "AS IS." FRED HUTCHINSON CANCER RESEARCH CENTER MAKES NO
 * REPRESENTATIONS OR WARRANTES OF ANY KIND CONCERNING THE SOFTWARE, EXPRESS OR IMPLIED,
 * INCLUDING, WITHOUT LIMITATION, WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, NONINFRINGEMENT, OR THE ABSENCE OF LATENT OR OTHER DEFECTS,
 * WHETHER OR NOT DISCOVERABLE.  IN NO EVENT SHALL FRED HUTCHINSON CANCER RESEARCH
 * CENTER OR ITS TRUSTEES, DIRECTORS, OFFICERS, EMPLOYEES, AND AFFILIATES BE LIABLE FOR
 * ANY DAMAGES OF ANY KIND, INCLUDING, WITHOUT LIMITATION, INCIDENTAL OR
 * CONSEQUENTIAL DAMAGES, ECONOMIC DAMAGES OR INJURY TO PROPERTY AND LOST PROFITS,
 * REGARDLESS OF  WHETHER FRED HUTCHINSON CANCER RESEARCH CENTER SHALL BE ADVISED,
 * SHALL HAVE OTHER REASON TO KNOW, OR IN FACT SHALL KNOW OF THE POSSIBILITY OF THE
 * FOREGOING.
 */
package org.broad.igv.renderer;

//~--- non-JDK imports --------------------------------------------------------

import org.apache.log4j.Logger;
import org.broad.igv.feature.*;
import org.broad.igv.track.FeatureTrack;
import org.broad.igv.track.RenderContext;
import org.broad.igv.track.Track;
import org.broad.igv.ui.FontManager;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.GeneralPath;
import java.util.List;

/**
 * Renderer for splice junctions. Draws a filled-in arc for each junction, with the width of the
 * arc representing the depth of coverage. If coverage information is present for the flanking
 * regions, draws that, too; otherwise indicates flanking regions with rectangles
 *
 * @author dhmay
 */
public class SpliceJunctionRenderer extends IGVFeatureRenderer {

    private static Logger log = Logger.getLogger(SpliceJunctionRenderer.class);

    //color for drawing all arcs
    Color ARC_COLOR_NEG = new Color(50, 50, 150, 140); //transparent dull blue
    Color ARC_COLOR_POS = new Color(150, 50, 50, 140); //transparent dull red

    //central horizontal line color
    Color COLOR_CENTERLINE = new Color(0, 0, 0, 100);

    //maximum depth that can be displayed, due to track height limitations. Junctions with
    //this depth and deeper will all look the same
    protected int MAX_DEPTH = 50;

    /**
     * Note:  assumption is that featureList is sorted by pStart position.
     *
     * @param featureList
     * @param context
     * @param trackRectangle
     * @param track
     *     public void render(List<IGVFeature> featureList,
                               RenderContext context,
                               Rectangle trackRectangle,
                               Track track) {
     */
    @Override
    public void render(List<IGVFeature> featureList,
                               RenderContext context,
                               Rectangle trackRectangle,
                               Track track) {

        double origin = context.getOrigin();
        double locScale = context.getScale();

        // Clear values
        lastFeatureLineMaxY = 0;
        lastFeatureBoundsMaxY = 0;
        lastRegionMaxY = 0;

        // TODO -- use enum instead of string "Color"
        if ((featureList != null) && !featureList.isEmpty()) {

            // Create a graphics object to draw font names.  Graphics are not cached
            // by font, only by color, so its neccessary to create a new one to prevent
            // affecting other tracks.
            font = FontManager.getScalableFont(track.getFontSize());
            Graphics2D fontGraphics = (Graphics2D) context.getGraphic2DForColor(Color.BLACK).create();
            fontGraphics.setFont(font);

            // Track coordinates
            double trackRectangleX = trackRectangle.getX();
            double trackRectangleMaxX = trackRectangle.getMaxX();

            // Draw the lines that represent the bounds of
            // a feature's region
            // TODO -- bugs in "Line Placement" style -- hardocde to fishbone

            int lastPixelEnd = -1;
            int occludedCount = 0;
            int maxOcclusions = 2;

            IGVFeature featureArray[] = featureList.toArray(new IGVFeature[featureList.size()]);
            for (IGVFeature feature : featureArray) {
                SpliceJunctionFeature junctionFeature = (SpliceJunctionFeature) feature;

                // Get the pStart and pEnd of the entire feature.  at extreme zoom levels the
                // virtual pixel value can be too large for an int, so the computation is
                // done in double precision and cast to an int only when its confirmed its
                // within the field of view.
                int flankingStart = junctionFeature.getStart();
                int flankingEnd = junctionFeature.getEnd();

                int junctionStart = junctionFeature.getJunctionStart();
                int junctionEnd = junctionFeature.getJunctionEnd();
                
                double virtualPixelStart = Math.round((flankingStart - origin) / locScale);
                double virtualPixelEnd = Math.round((flankingEnd - origin) / locScale);

                double virtualPixelJunctionStart = Math.round((junctionStart - origin) / locScale);
                double virtualPixelJunctionEnd = Math.round((junctionEnd - origin) / locScale);

                // If the any part of the feature fits in the
                // Track rectangle draw it
                if ((virtualPixelEnd >= trackRectangleX) && (virtualPixelStart <= trackRectangleMaxX)) {

                    //
                    int displayPixelEnd = (int) Math.min(trackRectangleMaxX, virtualPixelEnd);
                    int displayPixelStart = (int) Math.max(trackRectangleX, virtualPixelStart);


                    if (displayPixelEnd <= lastPixelEnd) {
                        if (occludedCount >= maxOcclusions) {
                            continue;
                        } else {
                            occludedCount++;
                        }
                    } else {
                        occludedCount = 0;
                        lastPixelEnd = displayPixelEnd;
                    }

                    float depth = junctionFeature.getJunctionDepth();


                    drawFeature((int) virtualPixelStart, (int) virtualPixelEnd,
                            (int) virtualPixelJunctionStart, (int) virtualPixelJunctionEnd, depth,
                            trackRectangle, context, feature.getStrand(), junctionFeature);

                    // Determine the y offset of features based on strand type

                    // If the width is < 3 there isn't room to draw the
                    // feature, or orientation.  If the feature has any exons
                    // at all indicate by filling a small rect

                    int pixelYCenter = trackRectangle.y + NORMAL_STRAND_Y_OFFSET / 2;

                    // If this is the highlight feature highlight it
                    if (getHighlightFeature() == feature) {
                        int yStart = pixelYCenter - BLOCK_HEIGHT / 2 - 1;
                        Graphics2D highlightGraphics = context.getGraphic2DForColor(Color.cyan);
                        highlightGraphics.drawRect(displayPixelStart - 1, yStart,
                                (displayPixelEnd - displayPixelStart + 2), BLOCK_HEIGHT + 2);
                    }
                }
            }

            //draw a central horizontal line
                Graphics2D g2D = context.getGraphic2DForColor(COLOR_CENTERLINE);
                g2D.drawLine((int) trackRectangleX, (int) trackRectangle.getCenterY(),
                        (int) trackRectangleMaxX, (int) trackRectangle.getCenterY());

        }
    }


    /**
     * Draw depth of coverage for the starting or ending flanking region
     * @param g2D
     * @param pixelStart
     * @param pixelLength
     * @param regionDepthArray
     * @param maxPossibleArcHeight
     * @param trackRectangle
     * @param isPositiveStrand
     */
    protected void drawFlankingRegion(Graphics g2D, int pixelStart, int pixelLength, int[] regionDepthArray,
                                      int maxPossibleArcHeight, Rectangle trackRectangle, boolean isPositiveStrand) {
        for (int i=0; i<pixelLength; i++)
        {
            float arrayIndicesPerPixel = (float) regionDepthArray.length /
                    (float) pixelLength;
            int flankingRegionArrayPixelMinIndex = (int) (i * arrayIndicesPerPixel);
            int flankingRegionArrayPixelMaxIndex = (int) ((i+1) * arrayIndicesPerPixel);
            flankingRegionArrayPixelMinIndex =
                    Math.max(0, Math.min(flankingRegionArrayPixelMinIndex, regionDepthArray.length-1));
            flankingRegionArrayPixelMaxIndex =
                    Math.max(0, Math.min(flankingRegionArrayPixelMaxIndex, regionDepthArray.length-1));

            int meanDepthThisPixel = 0;
            for (int j=flankingRegionArrayPixelMinIndex; j<=flankingRegionArrayPixelMaxIndex; j++)
                meanDepthThisPixel += regionDepthArray[j];
            meanDepthThisPixel /= (flankingRegionArrayPixelMaxIndex-flankingRegionArrayPixelMinIndex+1);
            meanDepthThisPixel = Math.min(MAX_DEPTH, meanDepthThisPixel);
            int pixelHeight = Math.max(maxPossibleArcHeight * meanDepthThisPixel / MAX_DEPTH, 2);
            g2D.fillRect(pixelStart+i,
                    (int)trackRectangle.getCenterY() + (isPositiveStrand ? -pixelHeight : 0),
                    1, pixelHeight);
        }
    }

    /**
     * Draw a filled arc representing a single feature. The thickness and height of the arc are proportional to the
     * depth of coverage.  Some of this gets a bit arcane -- the result of lots of visual tweaking.
     * @param pixelFeatureStart the starting position of the feature, whether on-screen or not
     * @param pixelFeatureEnd the ending position of the feature, whether on-screen or not
     * @param pixelJunctionStart the starting position of the junction, whether on-screen or not
     * @param pixelJunctionEnd the ending position of the junction, whether on-screen or not
     * @param depth coverage depth
     * @param trackRectangle
     */
     protected void drawFeature(int pixelFeatureStart, int pixelFeatureEnd,
                                   int pixelJunctionStart, int pixelJunctionEnd, float depth,
                                   Rectangle trackRectangle, RenderContext context, Strand strand,
                                   SpliceJunctionFeature junctionFeature) {
        boolean isPositiveStrand = true;
        // Get the feature's direction, color appropriately
        if (strand != null && strand.equals(Strand.NEGATIVE))
            isPositiveStrand = false;

        Color color = isPositiveStrand ? ARC_COLOR_POS : ARC_COLOR_NEG;

        Graphics2D g2D = context.getGraphic2DForColor(color);
                //Height of top of an arc of maximum depth
        int maxPossibleArcHeight = (trackRectangle.height - 1)/2;

        if (junctionFeature.hasFlankingRegionDepthArrays())
        {
            //draw a wigglegram of the splice junction flanking region depth of coverage

            int startFlankingRegionPixelLength = pixelJunctionStart - pixelFeatureStart;
            int endFlankingRegionPixelLength =  pixelFeatureEnd - pixelJunctionEnd;

            drawFlankingRegion(g2D, pixelFeatureStart, startFlankingRegionPixelLength,
                    junctionFeature.getStartFlankingRegionDepthArray(), maxPossibleArcHeight,
                    trackRectangle, isPositiveStrand);
            drawFlankingRegion(g2D, pixelJunctionEnd+1, endFlankingRegionPixelLength,
                    junctionFeature.getEndFlankingRegionDepthArray(), maxPossibleArcHeight,
                    trackRectangle, isPositiveStrand);
        }
        else {
            //Draw rectangles indicating the overlap on each side of the junction
            int overlapRectHeight = 3;
            int overlapRectTopX = (int)trackRectangle.getCenterY() + (isPositiveStrand ? -2 : 0);
            if (pixelFeatureStart < pixelJunctionStart)
            {
                g2D.fillRect(pixelFeatureStart, overlapRectTopX,
                        pixelJunctionStart - pixelFeatureStart, overlapRectHeight);
            }
            if (pixelJunctionEnd < pixelFeatureEnd)
            {
                g2D.fillRect(pixelJunctionEnd, overlapRectTopX,
                        pixelFeatureEnd - pixelJunctionEnd, overlapRectHeight);
            }
        }

        //Create a path describing the arc, using Bezier curves. The Bezier control points for the top and
        //bottom arcs are based on the boundary points of the rectangles containing the arcs

        //proportion of the maximum arc height used by a minimum-height arc
        double minArcHeightProportion = 0.33;

        int innerArcHeight = (int) (maxPossibleArcHeight * minArcHeightProportion);
        float depthProportionOfMax = Math.min(1, depth / MAX_DEPTH);
        int arcWidth = Math.max(1, (int) ((1-minArcHeightProportion) * maxPossibleArcHeight * depthProportionOfMax));
        int outerArcHeight = innerArcHeight + arcWidth;


        //Height of bottom of the arc
        int arcBeginY = (int)trackRectangle.getCenterY() +
                (isPositiveStrand ? -1 : 1);
        int outerArcPeakY = isPositiveStrand ?
                arcBeginY - outerArcHeight :
                arcBeginY + outerArcHeight;
        int innerArcPeakY = isPositiveStrand ?
                arcBeginY - innerArcHeight :
                arcBeginY + innerArcHeight;
        //dhmay: I don't really understand Bezier curves.  For some reason I have to put the Bezier control
        //points farther up or down than I want the arcs to extend.  This multiplier seems about right
        int outerBezierY = arcBeginY + (int) (1.5 * (outerArcPeakY - arcBeginY));
        int innerBezierY = arcBeginY + (int) (1.5 * (innerArcPeakY - arcBeginY));

        //Putting the Bezier control points slightly off to the sides of the arc 
        int bezierXPad = Math.max(1, (pixelJunctionEnd - pixelJunctionStart) / 30);

        GeneralPath arcPath = new GeneralPath();
        arcPath.moveTo(pixelJunctionStart, arcBeginY);
        arcPath.curveTo(pixelJunctionStart-bezierXPad, outerBezierY, //Bezier 1
                pixelJunctionEnd+bezierXPad, outerBezierY,         //Bezier 2
                pixelJunctionEnd, arcBeginY);        //Arc end
        arcPath.curveTo(pixelJunctionEnd+bezierXPad, innerBezierY, //Bezier 1
                pixelJunctionStart-bezierXPad, innerBezierY,         //Bezier 2
                pixelJunctionStart, arcBeginY);        //Arc end

        //Draw the arc, to ensure outline is drawn completely (fill won't do it, necessarily). This will also
        //give the arc a darker outline
        g2D.draw(arcPath);
        //Fill the arc
        g2D.fill(arcPath);

        lastFeatureLineMaxY = Math.max(outerArcPeakY, arcBeginY);
    }


}
