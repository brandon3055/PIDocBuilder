package com.brandon3055.pidocbuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.sun.istack.internal.Nullable;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

/**
 * Created by brandon3055 on 1/12/2018.
 * <p>
 * This is the manifest for a specific version (thats mod version) of a mods documentation as it exists on the web server.
 * It stores information about where the structure and all page files are located as well as their hashes.
 * Represents a PI Per Revision Manifest.json
 */
public class ModDocManifest {

    public String modId;
    public List<String> modAliases = new ArrayList<>();
    public String modVersion;
    public Map<String, ManifestFile> baseFiles = new LinkedHashMap<>();
    public Map<String, ManifestLangFile> langFiles = new LinkedHashMap<>();

    public ModDocManifest(String modId, String modVersion) {
        this.modId = modId;
        this.modVersion = modVersion;
    }

    private void addFile(File modFolder, File file, File containingFolder, boolean lang) {
        String path = FileHelper.aPath(file).replace(FileHelper.aPath(modFolder) + "/", "");
        String hash = FileHelper.getFileHash(file);
        String url = Main.PI_REPO_URL + "/objects/" + FileHelper.hashFileLoc(hash);

        if (lang) {
            langFiles.put(path, new ManifestLangFile(path, url, hash, containingFolder.getName()));
        }
        else {
            baseFiles.put(path, new ManifestFile(path, url, hash));
        }

        //Add mod aliases from the structure file
        if (!lang && path.equals("structure/structure.json")) {
            JsonObject obj = FileHelper.readObj(file);
            if (obj.has("mod_aliases")) {
                obj.get("mod_aliases").getAsJsonArray().forEach(alias -> modAliases.add(alias.getAsString()));
            }
        }
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("mod_id", modId);

        JsonArray baseFileArray = new JsonArray();
        baseFiles.values().forEach(file -> baseFileArray.add(file.toObj()));
        obj.add("base_files", baseFileArray);

        JsonArray langFileArray = new JsonArray();
        langFiles.values().forEach(file -> langFileArray.add(file.toObj()));
        obj.add("lang_files", langFileArray);

        return obj;
    }

    public static ModDocManifest fromJson(JsonObject obj, String modVersion) {
        String modId = obj.get("mod_id").getAsString();
        ModDocManifest manifest = new ModDocManifest(modId, modVersion);

        JsonArray baseFileArray = obj.get("base_files").getAsJsonArray();
        baseFileArray.forEach(element -> {
            ManifestFile file = ManifestFile.fromJson(element.getAsJsonObject());
            manifest.baseFiles.put(file.filePath, file);
        });

        JsonArray langFileArray = obj.get("lang_files").getAsJsonArray();
        langFileArray.forEach(element -> {
            ManifestLangFile file = ManifestLangFile.fromJson(element.getAsJsonObject());
            manifest.langFiles.put(file.filePath, file);
        });

        return manifest;
    }

    /**
     * @param modVersionFolder This is the mod version folder e.g. draconicevolution/2.1.0
     * @return a doc manifest for the given mod folder or null if the folder is invalid.
     */
    @Nullable
    public static ModDocManifest fromModFolder(String modid, File modVersionFolder) {
        File[] files = modVersionFolder.listFiles(File::isDirectory);
        if (files == null) return null;

        String version = modVersionFolder.getName();
        ModDocManifest manifest = new ModDocManifest(modid, version);

        if (!(new File(modVersionFolder, "structure/structure.json").exists())) {
            return null;
        }

        for (File file : files) {
            List<File> matches = new ArrayList<>();
            boolean lang;
            if (file.isDirectory()) {
                FileHelper.recursiveCollect(file, matches, check -> true);
                lang = !file.getName().equals("structure");
            }
            else {
                matches.add(file);
                lang = false;
            }
            matches.forEach(match -> manifest.addFile(modVersionFolder, match, file, lang));
        }

        return manifest;
    }

    @Override
    public String toString() {
        try {
            StringWriter stringWriter = new StringWriter();
            JsonWriter jsonWriter = new JsonWriter(stringWriter);
            jsonWriter.setLenient(true);
            jsonWriter.setIndent("    ");
            Streams.write(toJson(), jsonWriter);
            return stringWriter.toString();
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof ModDocManifest) {
            ModDocManifest other = (ModDocManifest) obj;
            if (baseFiles.size() != other.baseFiles.size()) {
                return false;
            }
            if (langFiles.size() != other.langFiles.size()) {
                return false;
            }
            for (String path : baseFiles.keySet()) {
                ManifestFile file = baseFiles.get(path);
                ManifestFile otherFile = other.baseFiles.get(path);

                if (otherFile == null || !file.fileSha1.equals(otherFile.fileSha1)) {
                    return false;
                }
            }
            for (String path : langFiles.keySet()) {
                ManifestFile file = langFiles.get(path);
                ManifestFile otherFile = other.langFiles.get(path);

                if (otherFile == null || !file.fileSha1.equals(otherFile.fileSha1)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public int writeObjects() {
        int count = 0;
        count += writeFiles(baseFiles.values());
        count += writeFiles(langFiles.values());
        return count;
    }

    private int writeFiles(Collection<? extends ManifestFile> files) {
        int count = 0;
        for (ManifestFile mFile : files) {
            File outputFile = new File(BuildManager.objectsFolder, FileHelper.hashFileLoc(mFile.fileSha1));
            if (outputFile.exists()) continue;
            File repoFile = new File(BuildManager.piRepoFolder, modId + "/" + modVersion + "/" + mFile.filePath);
            try {
                count++;
                FileUtils.copyFile(repoFile, outputFile);
            }
            catch (IOException e) {
                outputFile.delete();
                throw new RuntimeException(e);
            }
        }
        return count;
    }

    public static class ManifestFile {
        public String filePath;
        public String fileURL;
        public String fileSha1;

        private ManifestFile() {}

        public ManifestFile(String filePath, String fileURL, String fileSha1) {
            this.filePath = filePath;
            this.fileURL = fileURL;
            this.fileSha1 = fileSha1;
        }

        public JsonObject toObj() {
            JsonObject obj = new JsonObject();
            obj.addProperty("file_path", filePath);
            obj.addProperty("url", fileURL);
            obj.addProperty("sha1", fileSha1);
            return obj;
        }

        protected ManifestFile fromObj(JsonObject obj) {
            filePath = obj.get("file_path").getAsString();
            fileURL = obj.get("url").getAsString();
            fileSha1 = obj.get("sha1").getAsString();
            return this;
        }

        public static ManifestFile fromJson(JsonObject obj) {
            return new ManifestFile().fromObj(obj);
        }
    }

    public static class ManifestLangFile extends ManifestFile {
        public String lang;

        private ManifestLangFile() {}

        public ManifestLangFile(String filePath, String fileURL, String fileMD5, String lang) {
            super(filePath, fileURL, fileMD5);
            this.lang = lang;
        }

        @Override
        public JsonObject toObj() {
            JsonObject obj = super.toObj();
            obj.addProperty("lang", lang);
            return obj;
        }

        @Override
        protected ManifestLangFile fromObj(JsonObject obj) {
            super.fromObj(obj);
            lang = obj.get("lang").getAsString();
            return this;
        }

        public static ManifestLangFile fromJson(JsonObject obj) {
            return new ManifestLangFile().fromObj(obj);
        }
    }
}