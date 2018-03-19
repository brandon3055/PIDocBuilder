package com.brandon3055.pidocbuilder;

import java.io.IOException;

/**
 * Build sequence:
 *
 * - Validate specified pi server directory and ModDocs directory
 * - For each mod in the ModDocs repository
 *      - Read the build manifest for the mod
 *      - Parse each mod version
 *      - Compare to the latest existing build for that version
 *      - If something has changed then build the version and add it to the build manifest
 *
 *
 */
public class Main {

    //http://pi.brandon3055.com/manifest.json
    //http://pi.brandon3055.com/mods
    //http://pi.brandon3055.com/objects
    public static final String PI_REPO_URL = "http://pi.brandon3055.com";

    public static void main(String[] args) throws InterruptedException, IOException {
        if (args.length != 2) {
            throw new RuntimeException("Please specify the pi web server directory and the pi ModDocs directory.");
        }

        BuildManager.initialize(args[0], args[1]);
        BuildManager.loadManifests();
        BuildManager.compareManifests();
        BuildManager.build();
        Thread.sleep(100);
    }

    public static void log(Object o) {
        System.out.println(o);
    }

    public static void error(Object o) {
        System.err.println(o);
    }
}
