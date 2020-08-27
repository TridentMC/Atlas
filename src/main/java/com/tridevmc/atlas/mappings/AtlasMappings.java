package com.tridevmc.atlas.mappings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Used to represent a collection of types that can collectively be used to remap an obfuscated JAR.
 *
 * @author Benjamin K
 */
public class AtlasMappings {

    public static class Builder {
        private String name, dateGenerated;
        private List<AtlasType.Builder> types = Lists.newArrayList();

        public Builder(String name, String dateGenerated) {
            this.name = name;
            this.dateGenerated = dateGenerated;
        }

        public Builder addType(AtlasType.Builder type) {
            this.types.add(type);
            return this;
        }

        public AtlasMappings build() {
            return new AtlasMappings(this.name, this.dateGenerated, this.types);
        }
    }

    private final String name, dateGenerated;
    private final ImmutableList<AtlasType> types;

    private AtlasMappings(String name, String dateGenerated, List<AtlasType.Builder> types) {
        this.name = name;
        this.dateGenerated = dateGenerated;
        this.types = ImmutableList.copyOf(types.stream().map(t -> t.build(this)).collect(Collectors.toList()));
    }

    /**
     * Attempts to get a type matching the name provided.
     *
     * @param name           the name of the type to get.
     * @param fromMappedName whether or not the search is to be performed using mapped or unmapped names.
     * @return an optional of the matching type, or an empty optional if no such type exists.
     */
    public Optional<AtlasType> getType(String name, boolean fromMappedName) {
        if (name.contains("$")) {
            String[] splitName = name.split("\\$");
            return this.getType(splitName[0], fromMappedName).flatMap(t -> t.getChild(splitName, fromMappedName));
        }
        return this.types.stream().filter(t -> fromMappedName ? t.getMappedName().equals(name) : t.getObfuscatedName().equals(name)).findAny();
    }

    /**
     * Attempts to get a type matching the obfuscated name provided.
     *
     * @param name the obfuscated name of the type to get.
     * @return an optional of the matching type, or an empty optional of no such type exists.
     */
    public Optional<AtlasType> getTypeMapped(String name) {
        return this.getType(name, false);
    }

    /**
     * Attempts to map the given type name from its obfuscated state.
     *
     * @param name the obfuscated name to map.
     * @return the name after mapping has been applied, or the original if the name couldn't be mapped.
     */
    public String getTypeNameMapped(String name) {
        return this.getTypeMapped(name).map(AtlasMember::getMappedName).orElse(name);
    }

    /**
     * Attempts to get a type matching the mapped name provided.
     *
     * @param name the mapped name of the type to get.
     * @return an optional of the matching type, or an empty optional of no such type exists.
     */
    public Optional<AtlasType> getTypeObfuscated(String name) {
        return this.getType(name, true);
    }

    /**
     * Attempts to obfuscate the given type name from its mapped state.
     *
     * @param name the mapped name to obfuscate.
     * @return the name after obfuscation has been applied, or the original if the name couldn't be obfuscated.
     */
    public String getTypeNameObfuscated(String name) {
        return this.getTypeObfuscated(name).map(AtlasMember::getObfuscatedName).orElse(name);
    }

    /**
     * Gets the name of the mappings, used if the mappings are serialized.
     *
     * @return the name of the mappings.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets a date string using ISO format that represents when the mappings were generated.
     *
     * @return the date string of the mappings.
     */
    public String getDateGenerated() {
        return dateGenerated;
    }

    /**
     * Gets an immutable list of all of the types stored in the mappings.
     *
     * @return an immutable list of all of the types stored in the mappings.
     */
    public ImmutableList<AtlasType> getTypes() {
        return this.types;
    }

}
