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
package org.broad.igv.util;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;
import org.broad.igv.Globals;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.tribble.util.HttpUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jrobinso
 */
public class FileUtils {
    final public static String separator = System.getProperties().getProperty("file.separator");
    public static StringBuffer buffer = new StringBuffer();
    private static Logger log = Logger.getLogger(FileUtils.class);


    public static boolean resourceExists(String path) {
        try {
            boolean remoteFile = isRemote(path);
            return (!remoteFile && (new File(path).exists())) ||
                    (remoteFile && HttpUtils.resourceAvailable(new URL(path)));
        } catch (MalformedURLException e) {
            log.error("Malformed URL: " + path, e);
            return false;
        }
    }


    public static boolean isRemote(String path) {
        return path.startsWith("http://") || path.startsWith("https://") || path.startsWith("ftp://");
    }


    public static boolean canWriteTo(File file) {
        FileOutputStream fos = null;
        try {
            file.createNewFile();
            return true;

            // Has permission
        } catch (Exception e) {
            return false;
        } finally {
            file.delete();
        }
    }

    public static String getInstallDirectory() {

        String path = FileUtils.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation().getPath();
        File f = new File(path);
        if (f.isDirectory()) {
            return f.getAbsolutePath();
        } else {
            return f.getParentFile().getAbsolutePath();
        }

    }

    static public String getRelativePath(File baseFile, String targetFile) {
        return getRelativePath(baseFile, new File(targetFile));
    }

    static public String getRelativePath(File baseFile, File targetFile) {

        if (!baseFile.isDirectory()) {
            throw new RuntimeException(baseFile.getAbsolutePath() +
                    " is not a directory!");
        }

        boolean isTargetAFile = targetFile.isFile();
        File target = targetFile;
        if (isTargetAFile) {
            target = targetFile.getParentFile();
        } else {
            if (!targetFile.isDirectory()) {
                throw new RuntimeException(targetFile.getAbsolutePath() +
                        " is not a directory or file!");
            }
        }

        String path = "???";
        boolean isUsingRelativePath = true;
        boolean isMoreTargetPath = true;
        String delimeter = separator.equals("\\") ? "\\\\" : separator;
        String path1 = baseFile.getAbsolutePath();
        String path2 = target.getAbsolutePath();

        if (!path1.equals(path2)) {

            String[] basePath = path1.split(delimeter);
            String[] targetPath = path2.split(delimeter);

            // Compare paths
            int i = 0;
            for (; i < basePath.length; i++) {

                if (i == basePath.length) { // No more path
                    break; // No more path string to process
                }

                if (i == targetPath.length) { // No more path
                    int levelsBack = basePath.length - i;
                    path = completeTheRelativePath(levelsBack, targetPath, i);
                    break; // No more path string to process
                }

                // Roots must match or we use the absolute path
                if (i == 0) {
                    if (!basePath[i].equals(targetPath[i])) {
                        path = target.getAbsolutePath();
                        isUsingRelativePath = false;
                        break;// use default absolute path
                    } else {
                        path = "." + separator; // set a default path for same drive
                    }
                    continue;
                }

                // Nodes in path still match so keep looking
                if (basePath[i].equals(targetPath[i])) {
                    continue;
                }

                // Finally reached a point where path nodes don't match
                int levelsBack = basePath.length - i;
                path = completeTheRelativePath(levelsBack, targetPath, i);
                isMoreTargetPath = false; // Target now completely processed
                break;
            }

            if (isUsingRelativePath) {
                if (isMoreTargetPath) {
                    path += completeTheRelativePath(0, targetPath, i);
                }
            }
        } else {
            path = "." + separator;
        }

        if (isTargetAFile) {
            if (isUsingRelativePath) {
                path += targetFile.getName();
            } else {
                path = new File(path, targetFile.getName()).getAbsolutePath();
            }
        }
        return path;
    }

    /**
     * Return the canonical path of the file.  If the canonical path cannot
     * be computed return the absolute path.
     */
    static public String getCanonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (Exception e) {
            return file.getAbsolutePath();
        }
    }

    static public String getPlatformIndependentPath(String path) {
        return path.replace('\\', '/');
    }

    static private String completeTheRelativePath(int levelsBack,
                                                  String[] targetPath, int startingIndexForTarget) {

        // Complete the relative path
        buffer.delete(0, buffer.length());
        for (int j = 0; j < levelsBack; j++) {
            buffer.append("..");
            buffer.append(separator);
        }

        for (int k = startingIndexForTarget; k < targetPath.length; k++) {
            buffer.append(targetPath[k]);
            buffer.append(separator);
        }
        return buffer.toString();
    }

    /**
     * Delete a directory and all subdirectories
     *
     * @param dir
     * @return
     */
    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }


    public static void main(String[] args) {
        fixEOL("/Volumes/igv/data/public/ismb/ismb_snp_answers.bed", "/Volumes/igv/data/public/ismb/snp_answers.bed");
    }

    public static void fixEOL(String ifile, String ofile) {
        BufferedReader br = null;
        PrintWriter pw = null;
        try {
            br = new BufferedReader(new FileReader(ifile));
            pw = new PrintWriter(new FileWriter(ofile));
            String nextLine;
            while ((nextLine = br.readLine()) != null) {
                pw.println(nextLine);
            }

            br.close();
            pw.close();

        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {

        }
    }


    /**
     * Create a file from an input stream.
     *
     * @param url
     * @param outputFile
     * @throws java.io.IOException
     */
    public static boolean downloadFile(URL url, File outputFile) throws IOException {

        int maxTries = 1000;
        int nTries = 0;

        log.info("Downloading " + url + " to " + outputFile.getAbsolutePath());
        int downloaded = 0;
        byte[] buf = new byte[64 * 1024]; // 64K buffer

        OutputStream out = null;
        InputStream is = null;
        try {

            out = new FileOutputStream(outputFile);

            URLConnection connection = IGVHttpUtils.openConnection(url);
            int contentLength = connection.getContentLength();

            if (contentLength <= 0) {
                log.debug("Content-length = " + contentLength);
                // Try downloading until a loop returns zero bytes

                is = connection.getInputStream();
                int bytesRead;
                while ((bytesRead = is.read(buf)) != -1) {
                    out.write(buf, 0, bytesRead);
                    downloaded += bytesRead;
                }


            } else {
                while (downloaded < contentLength && nTries < maxTries) {
                    is = connection.getInputStream();
                    int bytesRead;
                    while ((bytesRead = is.read(buf)) != -1) {
                        out.write(buf, 0, bytesRead);
                        downloaded += bytesRead;
                    }

                    if (contentLength > downloaded) {
                        is.close();
                        connection = IGVHttpUtils.openConnection(url);
                        connection.setRequestProperty("Range", "bytes=" + downloaded + "-");
                        nTries++;
                        log.info("Restarting download from position: " + downloaded);
                    }


                }
            }

            if (downloaded < contentLength) {
                out.close();
                out = null;
                outputFile.delete();
                MessageUtils.showMessage("Error downloading file: " + outputFile.getAbsoluteFile());
                return false;
            } else {
                log.info("Download complete.  Transferred " + downloaded + " bytes");
                return true;
            }


        }


        catch (Exception e) {
            out.close();
            out = null;
            outputFile.delete();
            MessageUtils.showMessage("<html>Error downloading file: " + outputFile.getAbsoluteFile() +
                    "<br/>" + e.toString());
            return false;

        }
        finally {
            if (is != null) {
                is.close();
            }
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }

    /**
     * Create a file from an input stream.
     *
     * @param inputFile
     * @param outputFile
     * @throws java.io.IOException
     */

    public static void copyFile(File inputFile, File outputFile) throws IOException {

        int totalSize = 0;

        OutputStream out = null;
        InputStream in = null;
        try {
            in = new FileInputStream(inputFile);
            out = new FileOutputStream(outputFile);
            byte[] buffer = new byte[64000];
            int bytes_read;
            while ((bytes_read = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytes_read);
                totalSize += bytes_read;
            }

        }


        catch (Exception e) {
            outputFile.delete();
            MessageUtils.showMessage("<html>Error copying file: " + outputFile.getAbsoluteFile() +
                    "<br/>" + e.toString());

        }
        finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }


    public static void replaceStrings(File inputFile, File outputFile, Map<String, String> replace) throws IOException {

        BufferedReader reader = null;
        PrintWriter writer = null;

        try {
            reader = new BufferedReader(new FileReader(inputFile));
            writer = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)));
            String nextLine;
            while ((nextLine = reader.readLine()) != null) {
                for (Map.Entry<String, String> entry : replace.entrySet()) {
                    nextLine = nextLine.replace(entry.getKey(), entry.getValue());
                }
                writer.println(nextLine);
            }

        }
        finally {
            reader.close();
            writer.close();

        }
    }


    static Map<String, String> illegalChar = new HashMap();

    static {
        illegalChar.put("_qm_", "\\?");
        illegalChar.put("_fbr_", "\\[");
        illegalChar.put("_rbr_", "]");
        illegalChar.put("_fsl_", "/");
        illegalChar.put("_bsl_", "\\\\");
        illegalChar.put("_eq_", "=");
        illegalChar.put("_pl_", "\\+");
        illegalChar.put("_lt_", "<");
        illegalChar.put("_gt_", ">");
        illegalChar.put("_co_", ":");
        illegalChar.put("_sc_", ";");
        illegalChar.put("_dq_", "\"");
        illegalChar.put("_sq_", "'");
        illegalChar.put("_st_", "\\*");
        illegalChar.put("_pp_", "\\|");
    }

    static public String legalFileName(String string) {
        for (Map.Entry<String, String> entry : illegalChar.entrySet()) {
            string = string.replaceAll(entry.getValue(), entry.getKey());
        }
        return string;
    }


}

