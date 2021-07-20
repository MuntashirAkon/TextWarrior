/*
 * Copyright (C) 2007-2008 OpenIntents.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.openintents.filemanager.util;

import java.io.File;

import android.net.Uri;
import android.provider.MediaStore.Audio;
import android.provider.MediaStore.Video;

/**
 * @author Peli
 * @version 2009-07-03
 */
public class FileUtils {
    /**
     * TAG for log messages.
     */
    static final String TAG = "FileUtils";

    /*
     * Whether the filename is a video file.
     *
     * @param filename
     * @return
     *//*
	public static boolean isVideo(String filename) {
		String mimeType = getMimeType(filename);
		if (mimeType != null && mimeType.startsWith("video/")) {
			return true;
		} else {
			return false;
		}
	}*/

    /**
     * Whether the URI is a local one.
     */
    public static boolean isLocal(String uri) {
        return uri != null && !uri.startsWith("http://");
    }

    /**
     * Gets the extension of a file name, like ".png" or ".jpg".
     *
     * @return Extension including the dot("."); "" if there is no extension;
     * null if uri was null.
     */
    public static String getExtension(String uri) {
        if (uri == null) {
            return null;
        }

        int dot = uri.lastIndexOf(".");
        if (dot >= 0) {
            return uri.substring(dot);
        } else {
            // No extension.
            return "";
        }
    }

    /**
     * @return true if uri is a media uri.
     */
    public static boolean isMediaUri(String uri) {
        return uri.startsWith(Audio.Media.INTERNAL_CONTENT_URI.toString())
                || uri.startsWith(Audio.Media.EXTERNAL_CONTENT_URI.toString())
                || uri.startsWith(Video.Media.INTERNAL_CONTENT_URI.toString())
                || uri.startsWith(Video.Media.EXTERNAL_CONTENT_URI.toString());
    }

    /**
     * Convert File into Uri.
     * @return uri
     */
    public static Uri getUri(File file) {
        if (file != null) {
            return Uri.fromFile(file);
        }
        return null;
    }

    /**
     * Convert Uri into File.
     * @return file
     */
    public static File getFile(Uri uri) {
        if (uri != null) {
            String filepath = uri.getPath();
            if (filepath != null) {
                return new File(filepath);
            }
        }
        return null;
    }

    /**
     * Returns the path only (without file name).
     */
    public static File getPathWithoutFilename(File file) {
        if (file != null) {
            if (file.isDirectory()) {
                // no file to be split off. Return everything
                return file;
            } else {
                String filename = file.getName();
                String filepath = file.getAbsolutePath();

                // Construct path without file name.
                String pathwithoutname = filepath.substring(0, filepath.length() - filename.length());
                if (pathwithoutname.endsWith("/")) {
                    pathwithoutname = pathwithoutname.substring(0, pathwithoutname.length() - 1);
                }
                return new File(pathwithoutname);
            }
        }
        return null;
    }

    /**
     * Constructs a file from a path and file name.
     */
    public static File getFile(String curdir, String file) {
        String separator = "/";
        if (curdir.endsWith("/")) {
            separator = "";
        }
        return new File(curdir + separator + file);
    }

    public static File getFile(File curdir, String file) {
        return getFile(curdir.getAbsolutePath(), file);
    }
}
