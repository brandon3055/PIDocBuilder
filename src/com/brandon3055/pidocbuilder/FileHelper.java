package com.brandon3055.pidocbuilder;

import com.google.common.hash.Hashing;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

/**
 * Created by brandon3055 on 1/25/2018.
 */
public class FileHelper {
    public static String getFileHash(File file) {
        try {
            return Hashing.sha1().hashBytes(FileUtils.readFileToByteArray(file)).toString();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Finds all files in the directory and sub directories that match the predicate and adds them to the list.
     */
    public static void recursiveCollect(File dir, List<File> matches, Predicate<File> matcher) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                recursiveCollect(file, matches, matcher);
            }
            else if (matcher.test(file)) {
                matches.add(file);
            }
        }
    }

    public static JsonObject readObj(File file) {
        try {
            JsonReader reader = new JsonReader(new FileReader(file));
            JsonParser parser = new JsonParser();
            reader.setLenient(true);
            JsonElement element = parser.parse(reader);
            IOUtils.closeQuietly(reader);
            return element.getAsJsonObject();
        }
        catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void writeJson(JsonObject obj, File file) {
        try {
            JsonWriter writer = new JsonWriter(new FileWriter(file));
            writer.setIndent("  ");
            Streams.write(obj, writer);
            writer.flush();
            IOUtils.closeQuietly(writer);
        }
        catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static String hashFileLoc(String hash) {
        if (hash.length() < 2) throw new RuntimeException("Invalid hash! " + hash);
        return hash.substring(0, 2) + "/" + hash;
    }

    public static String aPath(File file) {
        return file.getAbsolutePath().replace("\\", "/");
    }
}
