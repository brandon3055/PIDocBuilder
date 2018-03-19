package com.brandon3055.pidocbuilder;

import com.google.gson.JsonObject;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by brandon3055 on 1/12/2018.
 * <p>
 * <p>
 * I don't need to parse the structure file.
 * i just need to hash the structure file, md files and lang files
 * If any of them have changed then i need to rebuild that doc version
 */
public class BuildManager {

    public static File piRootFolder;
    public static File modsFolder;
    public static File objectsFolder;
    public static File piRepoFolder;

    //modId -> (modVersion -> Manifest)
    private static Map<String, Map<String, ModDocManifest>> repoModVersionManifestMap = new HashMap<>();
    //modId -> BuildManifest
    public static Map<String, BuildManifest> modBuildManifestMap = new HashMap<>();
    //List of all manifests that need to be built
    //modId -> ModDocManifest list
    public static Map<String, List<ModDocManifest>> scheduledBuildMap = new HashMap<>();

    public static void initialize(String piRoot, String piRepo) {
        piRootFolder = new File(piRoot);
        piRepoFolder = new File(piRepo);

        if (!piRepoFolder.exists() || !piRepoFolder.isDirectory()) {
            throw new RuntimeException("The specified PI Repository folder does not exist!");
        }

        if (!piRootFolder.exists() && !piRootFolder.mkdirs()) {
            throw new RuntimeException("The specified PI Root folder does not exist and could not be created!");
        }

        modsFolder = new File(piRootFolder, "mods");
        objectsFolder = new File(piRootFolder, "objects");

        if (!modsFolder.exists() && !modsFolder.mkdirs()) {
            throw new RuntimeException("Unable to create mods folder in root directory");
        }

        if (!objectsFolder.exists() && !objectsFolder.mkdirs()) {
            throw new RuntimeException("Unable to create objects folder in root directory");
        }
    }

    public static void loadManifests() {
        readRepository();
        readBuildManifests();
    }

    private static void readRepository() {
        File[] mods = piRepoFolder.listFiles(File::isDirectory);
        if (mods == null) {
            throw new RuntimeException("The specified repo folder is empty. There is nothing to build!");
        }

        for (File mod : mods) {
            File[] modVersions = mod.listFiles(File::isDirectory);
            if (modVersions == null) {
                Main.error("Found invalid mod folder in repo! " + mod);
                continue;
            }
            for (File version : modVersions) {
                ModDocManifest manifest = ModDocManifest.fromModFolder(mod.getName(), version);
                if (manifest != null) {
                    repoModVersionManifestMap.computeIfAbsent(manifest.modId, s -> new HashMap<>()).put(manifest.modVersion, manifest);
                }
            }
        }
    }

    private static void readBuildManifests() {
        File[] files = modsFolder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!file.isFile() || !file.getName().endsWith(".json")) {
                Main.log("Error! Found invalid file in mods folder! " + file);
            }
            Main.log("Reading manifest: " + file);
            JsonObject obj = FileHelper.readObj(file);
            BuildManifest manifest = BuildManifest.fromJson(obj);
            modBuildManifestMap.put(manifest.modid, manifest);
        }
    }

    public static void compareManifests() {
        for (String modId : repoModVersionManifestMap.keySet()) {
            Main.log("Comparing manifests for mod " + modId);
            for (ModDocManifest manifest : repoModVersionManifestMap.get(modId).values()) {
                BuildManifest bm = modBuildManifestMap.get(modId);
                BuildManifest.Build lastBuild = bm == null ? null : bm.getLatestForVersion(manifest.modVersion);
                boolean requiresBuild;

                if (lastBuild == null) {
                    requiresBuild = true;
                }
                else {
                    JsonObject obj = FileHelper.readObj(lastBuild.getManifestFile());
                    ModDocManifest lastManifest = ModDocManifest.fromJson(obj, manifest.modVersion);
                    requiresBuild = !manifest.equals(lastManifest);
                }

                if (requiresBuild) {
                    scheduledBuildMap.computeIfAbsent(modId, s -> new LinkedList<>()).add(manifest);
                    Main.log("Found new/modified manifest for mod version " + manifest.modVersion);
                }
            }
        }
    }

    public static void build() throws IOException {
        if (scheduledBuildMap.isEmpty()) {
            Main.error("Found no documentation changes to build!");
            System.exit(404);
        }

        for (String modId : scheduledBuildMap.keySet()) {
            Main.log("Building documentation for mod " + modId);
            List<ModDocManifest> toBuild = scheduledBuildMap.get(modId);
            BuildManifest bm = getBuildManifest(modId);
            for (ModDocManifest manifest : toBuild) {
                Main.log("Building for mod version " + manifest.modVersion);
                buildManifest(manifest, bm);
            }
            File bmFile = new File(modsFolder, bm.modid + ".json");
            FileHelper.writeJson(bm.toObj(), bmFile);
        }

        //It works! I just need to write the actual mod manifest that links mod id and aliases to the mod build manifest
        //Dont need to know where the manifest is because the build manifest already knows. Just need to link to the build manifest

        //Write the linking manifest that is the single manifest that points to each mods own build manifest.
        Main.log("Writing linking manifest");
        Map<String, List<String>> aliasmap = compileAliases();
        JsonObject manifestList = new JsonObject();
        aliasmap.forEach((mod, aliases) -> aliases.forEach(alias -> manifestList.addProperty(alias, Main.PI_REPO_URL + "/mods/" + mod + ".json")));
        FileHelper.writeJson(manifestList, new File(piRootFolder, "manifest.json"));
    }

    private static Map<String, List<String>> compileAliases() {
        Map<String, List<String>> aliasMap = new HashMap<>();

        for (String modid : repoModVersionManifestMap.keySet()) {
            List<String> aliases = new ArrayList<>();
            aliases.add(modid);
            repoModVersionManifestMap.get(modid).values().forEach(manifest -> manifest.modAliases.forEach(alias -> {
                if (!aliases.contains(alias)) {
                    aliases.add(alias);
                }
            }));
            aliasMap.put(modid, aliases);
        }

        return aliasMap;
    }

    private static void buildManifest(ModDocManifest manifest, BuildManifest bm) throws IOException {
        int count = manifest.writeObjects();
        Main.log("Detected " + count + " Changed File(s)!");

        File temp = new File(BuildManager.piRootFolder, "manifest_build.temp");
        FileHelper.writeJson(manifest.toJson(), temp);
        String hash = FileHelper.getFileHash(temp);
        File manifestFile = new File(BuildManager.objectsFolder, FileHelper.hashFileLoc(hash));
        FileUtils.copyFile(temp, manifestFile);
        temp.delete();
        bm.addBuild(Main.PI_REPO_URL + "/objects/" + FileHelper.hashFileLoc(hash), manifest.modVersion);
    }

    private static BuildManifest getBuildManifest(String modId) {
        return modBuildManifestMap.computeIfAbsent(modId, BuildManifest::new);
    }
}
