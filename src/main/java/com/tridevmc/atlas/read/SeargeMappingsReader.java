package com.tridevmc.atlas.read;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tridevmc.atlas.mappings.*;
import com.tridevmc.atlas.util.StringFixer;
import org.pmw.tinylog.Logger;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.tridevmc.atlas.util.StringFixer.dotsToSlash;

public class SeargeMappingsReader implements IMappingsReader {
    private final String name;
    private final List<String> lines;
    private final Map<String, String> fields;
    private final Map<String, String> methods;

    private static final Pattern LINE_SPLITTER = Pattern.compile("([A-z$0-9]+) (.+)");
    private static final Pattern ARG_SPLITTER = Pattern.compile("(\\[*(?:L[A-z$0-9/]+;|I|V|Z|B|C|S|D|F|J))");
    private static final Pattern MAPPED_SPLITTER = Pattern.compile("\\(.*\\).+ (.+)");

    public SeargeMappingsReader(String name, List<String> lines, Map<String, String> fields, Map<String, String> methods) throws IOException {
        this.name = name;
        this.lines = lines;
        this.fields = fields;
        this.methods = methods;
    }

    @Override
    public AtlasMappings read() {
        AtlasMappings.Builder mappingsBuilder = new AtlasMappings.Builder(this.name, OffsetDateTime.now().format(DateTimeFormatter.ISO_DATE));
        AtlasType.Builder currentType = null;
        Map<String, AtlasType.Builder> typeBuilders = Maps.newHashMap();
        Map<String, List<UnresolvedMethod>> unresolvedMethods = Maps.newHashMap();

        for (String l : lines) {
            List<String> parts = getFullBodyMatch(l.trim(), LINE_SPLITTER);
            if (parts.size() != 2) {
                throw new RuntimeException("Parsed a line <" + l + "> with an invalid format");
            }
            String name = dotsToSlash(parts.get(0));
            String arg = dotsToSlash(parts.get(1));

            if (l.startsWith("\t")) {
                // We're dealing with something that needs a pre-existing type
                if (currentType == null) {
                    throw new RuntimeException("Parsed a dependent line <" + l + "> without a type");
                }
                if (arg.startsWith("(")) {
                    // Probably a method.
                    if (!unresolvedMethods.containsKey(currentType.getObfuscatedName())) {
                        unresolvedMethods.put(currentType.getObfuscatedName(), Lists.newArrayList());
                    }
                    unresolvedMethods.get(currentType.getObfuscatedName()).add(new UnresolvedMethod(name, arg));
                } else {
                    // Probably a field.
                    if (arg.startsWith("field") && fields.containsKey(arg)) {
                        arg = fields.get(arg);
                    }
                    AtlasField.Builder builder = new AtlasField.Builder(name, arg, "");

                    currentType.addMember(builder);
                }
            } else {
                // We're dealing with a brand new type
                if (currentType != null) {
                    Logger.info("Finished building type {}", currentType);
                }
                if (name.contains("$")) {
                    String[] split = name.split("\\$");
                    currentType = new AtlasType.Builder(name, arg);
                    typeBuilders.get(split[0]).addChild(split, currentType);
                } else {
                    currentType = new AtlasType.Builder(name, arg);
                    typeBuilders.put(name, currentType);
                    mappingsBuilder.addType(currentType);
                }
            }
        }

        // Now deal with SRG's lack of unobfuscated argument and return types.
        for(Map.Entry<String, List<UnresolvedMethod>> entry : unresolvedMethods.entrySet()) {
            String typeName = entry.getKey();
            if(typeName.contains("$")) {
                String[] split = entry.getKey().split("\\$");
                currentType =  typeBuilders.get(split[0]).getChild(typeName);
            } else {
                currentType = typeBuilders.get(typeName);
            }

            // For every method that has obfuscated type values (read: all of them)
            // within this type, iterate over it and get every true name of
            // the obfuscated type signatures within. For parity with
            // Mojang mappings.
            for(UnresolvedMethod s : entry.getValue()) {
                List<String> args = getAllMatches(s.argument, ARG_SPLITTER)
                        .stream()
                        .map(StringFixer::dotsToSlash)
                        .map(str -> webToJava(str, typeBuilders))
                        .collect(Collectors.toList());
                String name = dotsToSlash(getFullBodyMatch(s.argument, MAPPED_SPLITTER).get(0));
                if(name.startsWith("func") && methods.containsKey(name)) {
                    name = methods.get(name);
                }
                AtlasMethod.Builder b = new AtlasMethod.Builder(s.obfuscatedName, name, args.get(args.size()-1), args.subList(0, args.size()-1));
                currentType.addMember(b);
            }
        }

        return mappingsBuilder.build();
    }

    private static List<String> getAllMatches(String s, Pattern p) {
        List<String> matches = Lists.newArrayList();
        Matcher m = p.matcher(s);
        while (m.find()) {
            matches.add(m.group());
        }
        return matches;
    }

    private static List<String> getFullBodyMatch(String s, Pattern p) {
        List<String> matches = Lists.newArrayList();
        Matcher m = p.matcher(s);
        if(m.matches()) {
            for(int i =1;i <= m.groupCount();i++) {
                matches.add(m.group(i));
            }
        }
        return matches;
    }

    /**
     * Hackeneyed method to take an SRG-style ObjectWeb function signature (ex. {@code (Lbz$d;)Lbs$a;})
     * and convert it into a more traditional Java-esque form, in line with Mojang's mapping
     * function signatures.
     *
     * @param arg The string to convert
     * @param typeBuilders A list of types to sample the obfuscated names from
     * @return String converted into Java-esque function signature form
     */
    private static String webToJava(String arg, Map<String, AtlasType.Builder> typeBuilders) {
        StringBuilder c = new StringBuilder();

        int arrayCount = (int)arg.chars().filter(ch -> ch == '[').count();
        arg = arg.replace("[","");

        if(arg.startsWith("L")) {
            String obfName = arg.substring(1).substring(0, arg.length()-2);
            if(typeBuilders.containsKey(obfName)) {
                c.append(typeBuilders.get(obfName).getMappedName());
            }
            // If the name contains a forward slash, we can almost certainly conclude
            // it's already fully qualified and just encoded weird. Otherwise,
            // it's probably something we don't know about, and should. Report it.
            else if(!obfName.contains("/")) {
                Logger.warn("appending obfuscated name ("+obfName+") - this probably means something is wrong!");
                c.append(obfName);
            }
        } else {
            switch(arg) {
                case "I": c.append("int"); break;
                case "V": c.append("void"); break;
                case "Z": c.append("boolean"); break;
                case "B": c.append("byte"); break;
                case "C": c.append("char"); break;
                case "S": c.append("short"); break;
                case "D": c.append("double"); break;
                case "F": c.append("float"); break;
                case "J": c.append("long"); break;
                default:
                    throw new RuntimeException("Unknown ObjectWeb key '"+arg+"'");
            }
        }
        for (int i =0;i < arrayCount;i++) {
            c.append("[]");
        }
        return c.toString();
    }

    /**
     * Represents an unresolved method within a type, including
     * its original obfuscated name and signature/mapped name.
     */
    static class UnresolvedMethod {
        private final String obfuscatedName;
        private final String argument;

        public UnresolvedMethod(String a, String b) {
            this.obfuscatedName = a;
            this.argument = b;
        }
    }
}