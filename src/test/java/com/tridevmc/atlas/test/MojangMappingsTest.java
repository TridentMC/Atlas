package com.tridevmc.atlas.test;


import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.tridevmc.atlas.read.MojangMappingsReader;
import com.tridevmc.atlas.write.AtlasRemapper;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.stream.Stream;

public class MojangMappingsTest {

    private static final String VERSION_MANIFEST = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    @Test
    public void readMojangMappings() throws IOException {
        JsonObject versionManifest = new Gson().fromJson(new InputStreamReader(new URL(VERSION_MANIFEST).openStream()), JsonObject.class);
        String latestRelease = versionManifest.getAsJsonObject("latest").get("release").getAsString();
        Optional<String> latestVersionPackage = Stream.generate(versionManifest.getAsJsonArray("versions").iterator()::next)
                .filter(e -> e.getAsJsonObject().get("id").getAsString().equals(latestRelease))
                .findAny()
                .map(e -> e.getAsJsonObject().get("url").getAsString());
        URL packageURL = new URL(latestVersionPackage.orElseThrow(() -> new RuntimeException("Failed to locate version package from version manifest!")));
        JsonObject versionPackage = new Gson().fromJson(new InputStreamReader(packageURL.openStream()), JsonObject.class);
        // We use the server binary and mappings, just because.
        URL serverJarURL = new URL(versionPackage.getAsJsonObject("downloads").getAsJsonObject("server").get("url").getAsString());
        URL serverMappingsURL = new URL(versionPackage.getAsJsonObject("downloads").getAsJsonObject("server_mappings").get("url").getAsString());
        File serverJar = createNewFile(new File("mojangmappings", "server_unmapped.jar"));
        File serverMappings = createNewFile(new File("mojangmappings", "server.mmap"));
        File serverJarOut = createNewFile(new File("mojangmappings", "server_mapped.jar"));
        Files.asByteSink(serverJar).writeFrom(serverJarURL.openStream());
        Files.asByteSink(serverMappings).writeFrom(serverMappingsURL.openStream());
        AtlasRemapper remapper = new AtlasRemapper(new MojangMappingsReader(serverMappings.getName(), Files.readLines(serverMappings, Charset.defaultCharset())).read(), new FileInputStream(serverJar));
        remapper.remap(new FileOutputStream(serverJarOut));
    }

    private static File createNewFile(File file) throws IOException {
        file.getParentFile().mkdirs();
        file.createNewFile();
        return file;
    }


}
