package com.tridevmc.atlas.read;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tridevmc.atlas.mappings.AtlasField;
import com.tridevmc.atlas.mappings.AtlasMappings;
import com.tridevmc.atlas.mappings.AtlasMethod;
import com.tridevmc.atlas.mappings.AtlasType;
import org.pmw.tinylog.Logger;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads Mojang mapping files and converts them into an AtlasMappings object to use with a Remapper.
 *
 * @author Benjamin K
 */
public class MojangMappingsReader implements IMappingsReader {

    private final String name;
    private final List<String> lines;

    public MojangMappingsReader(String name, String lines) {
        this(name, lines.split("\n"));
    }

    public MojangMappingsReader(String name, String[] lines) {
        this.name = name;
        this.lines = Arrays.asList(lines.clone());
    }

    public MojangMappingsReader(String name, List<String> lines) {
        this.name = name;
        this.lines = Lists.newArrayList(lines);
    }

    @Override
    public AtlasMappings read() {
        AtlasMappings.Builder mappingsBuilder = new AtlasMappings.Builder(this.name, OffsetDateTime.now().format(DateTimeFormatter.ISO_DATE));
        AtlasType.Builder currentType = null;
        Map<String, AtlasType.Builder> typeBuilders = Maps.newHashMap();
        for (int lineNo = 0; lineNo < lines.size(); lineNo++) {
            String line = this.lines.get(lineNo);
            if (line.startsWith("#")) {
                Logger.info("Skipping line {} - contains comment", lineNo);
                continue;
            }

            Pattern namePattern = Pattern.compile("([A-z0-9.\\-_$<>]+)");
            Matcher matcher = namePattern.matcher(line);
            List<String> matches = Lists.newArrayList();
            while (matcher.find()) {
                matches.add(matcher.group());
            }
            if (!line.startsWith(" ")) {
                String mappedName = matches.get(0).replace(".", "/");
                String obfuscatedName = matches.get(2).replace(".", "/");
                if (currentType != null)
                    Logger.info("Finished building type {}", currentType);
                if (!obfuscatedName.contains("$")) {
                    currentType = new AtlasType.Builder(obfuscatedName, mappedName);
                    typeBuilders.put(obfuscatedName, currentType);
                    mappingsBuilder.addType(currentType);
                } else {
                    String[] splitName = obfuscatedName.split("\\$");
                    String rootName = splitName[0];
                    currentType = new AtlasType.Builder(obfuscatedName, mappedName);
                    typeBuilders.get(rootName).addChild(splitName, currentType);
                }
            } else {
                if (line.contains("(")) {
                    // Methods represent args with brackets, this must be a method.
                    int offset = line.contains(":") ? 2 : 0;
                    String returnType = dotsToSlash(matches.get(offset).replace(".", "/"));
                    String mappedName = matches.get(offset + 1).replace(".", "/");
                    String obfuscatedName = matches.get(matches.size() - 1).replace(".", "/");
                    List<String> arguments = matches.subList(offset + 2, matches.size() - 2).stream().map(this::dotsToSlash).collect(Collectors.toList());
                    AtlasMethod.Builder methodBuilder = new AtlasMethod.Builder(obfuscatedName, mappedName, returnType, arguments);
                    currentType.addMember(methodBuilder);
                } else {
                    // Fields
                    String type = dotsToSlash(matches.get(0).replace(".", "/"));
                    String mappedName = matches.get(1).replace(".", "/");
                    String obfuscatedName = matches.get(matches.size() - 1).replace(".", "/");
                    AtlasField.Builder fieldBuilder = new AtlasField.Builder(obfuscatedName, mappedName, type);
                    currentType.addMember(fieldBuilder);
                }
            }
        }

        return mappingsBuilder.build();
    }

    private String dotsToSlash(String str) {
        return str.replace(".", "/");
    }
}
