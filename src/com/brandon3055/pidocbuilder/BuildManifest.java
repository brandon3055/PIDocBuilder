package com.brandon3055.pidocbuilder;

import com.google.gson.JsonObject;
import com.sun.istack.internal.Nullable;

import java.io.File;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Created by brandon3055 on 1/12/2018.
 * This object holds and is used to manage all of the builds for a specific mods documentation.
 */
public class BuildManifest {

    public String modid;
    public LinkedList<Build> builds = new LinkedList<>();
    private int nextBuild = 0;

    private BuildManifest() {}

    public BuildManifest(String modid) {
        this.modid = modid;
    }

    public JsonObject toObj() {
        JsonObject obj = new JsonObject();
        obj.addProperty("mod_id", modid);
        builds.forEach(build -> {
            Main.log("Write Build: " + build.buildNumber+" "+hashCode());
            obj.add(String.valueOf(build.buildNumber), build.toJson());
        });
        return obj;
    }

    private BuildManifest fromObj(JsonObject obj) {
        modid = obj.get("mod_id").getAsString();
        obj.entrySet().forEach(entry -> {
            if (!entry.getKey().equals("mod_id")) {
                int buildNumber = Integer.parseInt(entry.getKey());
                builds.add(Build.fromJson(entry.getValue().getAsJsonObject(), buildNumber));
            }
        });

        builds.sort(Comparator.comparingInt(b -> b.buildNumber));
        if (!builds.isEmpty()) {
            nextBuild = builds.getLast().buildNumber + 1;
        }

        return this;
    }

    public static BuildManifest fromJson(JsonObject obj) {
        return new BuildManifest().fromObj(obj);
    }

    /**
     * Gets the latest build for the specified mod version.
     * @param version the mod version.
     * @return the latest build for the specified mod version or null if there are no builds for that version.
     */
    @Nullable
    public Build getLatestForVersion(String version) {
        Iterator<Build> i = builds.descendingIterator();
        while (i.hasNext()) {
            Build build = i.next();
            if (build.modVersion.equals(version)) {
                return build;
            }
        }
        return null;
    }

    private int getNextBuild() {
        return nextBuild++;
    }

    public void addBuild(String manifestURL, String modVersion) {
        builds.add(new Build(getNextBuild(), manifestURL, modVersion));
    }

    public static class Build {
        public int buildNumber;
        public String manifestURL;
        public String modVersion;

        public Build(int buildNumber, String manifestURL, String modVersion) {
            this.buildNumber = buildNumber;
            this.manifestURL = manifestURL;
            this.modVersion = modVersion;
        }

        public File getManifestFile() {
            String path = manifestURL.replace(Main.PI_REPO_URL + "/", "");
            return new File(BuildManager.piRootFolder, path);
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("file", manifestURL);
            obj.addProperty("mod_version", modVersion);
            return obj;
        }

        public static Build fromJson(JsonObject obj, int buildNumber) {
            String manifestFile = obj.get("file").getAsString();
            String modVersion = obj.get("mod_version").getAsString();
            return new Build(buildNumber, manifestFile, modVersion);
        }
    }
}
