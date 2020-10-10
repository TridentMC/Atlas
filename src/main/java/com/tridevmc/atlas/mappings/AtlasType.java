package com.tridevmc.atlas.mappings;

import com.google.common.base.MoreObjects;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Used to represent a type (class) that has mapping data that can be applied during remapping.
 * <p>
 * Contains utility methods for matching child members in addition to standard member data.
 *
 * @author Benjamin K
 */
public class AtlasType extends AtlasMember {

    public static class Builder implements ITypeBuilder<AtlasType> {
        private final String obfuscatedName;
        private final String mappedName;
        private final List<AtlasType.Builder> children;
        private final List<IMemberBuilder<? extends AtlasMember>> members;
        private AtlasType built;

        public Builder(String obfuscatedName, String mappedName) {
            this.obfuscatedName = obfuscatedName;
            this.mappedName = mappedName;
            this.members = Lists.newArrayList();
            this.children = Lists.newArrayList();
        }

        public Builder getChild(String obfuscatedName) {
            for(AtlasType.Builder b : children) {
                if(b.getObfuscatedName().equals(obfuscatedName)) return b;
            }
            throw new RuntimeException("unable to find " + obfuscatedName);
        }

        public String getObfuscatedName() {
            return obfuscatedName;
        }

        public String getMappedName() {
            return mappedName;
        }

        public Builder addMember(IMemberBuilder<? extends AtlasMember> member) {
            this.members.add(member);
            return this;
        }

        public Builder addChild(String[] fullName, AtlasType.Builder child) {
            return this.addChild(fullName, child, 0);
        }

        public Builder addChild(String[] fullName, AtlasType.Builder child, int depth) {
            if (String.join("$", Arrays.copyOfRange(fullName, 0, depth + 1)).equals(this.obfuscatedName)) {
                if (fullName.length - depth == 2) {
                    this.children.add(child);
                } else {
                    String childName = String.join("$", Arrays.copyOfRange(fullName, 0, depth + 2));
                    Builder matchingChild = this.children.stream().filter((Predicate<Builder>) input -> input.obfuscatedName.equals(childName)).findAny().orElse(null);
                    if (matchingChild == null) {
                        throw new RuntimeException("No matching parent class was found for full name " + String.join("$", fullName));
                    } else {
                        matchingChild.addChild(fullName, child, depth + 1);
                    }
                }
            } else {
                throw new RuntimeException("No matching parent class was found for full name " + String.join("$", fullName));
            }
            return this;
        }

        public AtlasType build(AtlasMappings mappings) {
            if (this.built == null) {
                if (!this.children.isEmpty()) {
                    List<AtlasType> children = this.children.stream().map(b -> b.build(mappings)).collect(Collectors.toList());
                    this.built = new AtlasType(mappings, obfuscatedName, mappedName, children, members);
                } else {
                    this.built = new AtlasType(mappings, obfuscatedName, mappedName, Collections.emptyList(), members);
                }
            }
            return this.built;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("obfuscatedName", obfuscatedName)
                    .add("mappedName", mappedName)
                    .add("children", children.size())
                    .add("members", members.size())
                    .toString();
        }
    }

    private final transient AtlasMappings mappings;

    private final ImmutableList<AtlasType> children;
    private final ImmutableList<AtlasField> fields;
    private final ImmutableList<AtlasMethod> methods;

    private AtlasType(AtlasMappings mappings, String obfuscatedName, String mappedName, List<AtlasType> children, List<IMemberBuilder<? extends AtlasMember>> members) {
        super(obfuscatedName, mappedName);
        this.mappings = mappings;
        this.children = ImmutableList.copyOf(children);
        this.fields = ImmutableList.copyOf(members.stream().filter(m -> m instanceof AtlasField.Builder).map(m -> (AtlasField) m.build(this)).collect(Collectors.toList()));
        this.methods = ImmutableList.copyOf(members.stream().filter(m -> m instanceof AtlasMethod.Builder).map(m -> (AtlasMethod) m.build(this)).collect(Collectors.toList()));
    }

    /**
     * Gets the AtlasMappings that this type is part of.
     *
     * @return the AtlasMappings this type is part of.
     */
    public AtlasMappings getMappings() {
        return mappings;
    }

    /**
     * Gets an immutable list of all the fields on this type, not including children.
     *
     * @return an immutable list of all the fields on this type.
     */
    public ImmutableList<AtlasField> getFields() {
        return this.fields;
    }

    /**
     * Gets an immutable list of all the methods on this type, not including children.
     *
     * @return an immutable list of all the methods on this type.
     */
    public ImmutableList<AtlasMethod> getMethods() {
        return this.methods;
    }

    /**
     * Gets an immutable list of all the child types of this type.
     *
     * @return an immutable list of all the child types.
     */
    public ImmutableList<AtlasType> getChildren() {
        return this.children;
    }

    /**
     * Gets a child type matching the given full name.
     *
     * @param fullName the full name of the type, subclasses separated by "$"
     * @param fromMappedName whether or not the search is to be performed using mapped or unmapped names.
     * @return an optional of the matching child type, or an empty optional if no such type exists.
     */
    public Optional<AtlasType> getChild(String[] fullName, boolean fromMappedName) {
        return this.getChild(fullName, fromMappedName, 0);
    }

    private Optional<AtlasType> getChild(String[] fullName, boolean fromMappedName, int depth) {
        if (String.join("$", Arrays.copyOfRange(fullName, 0, depth + 1)).equals(fromMappedName ? this.getMappedName() : this.getObfuscatedName())) {
            String childName = String.join("$", Arrays.copyOfRange(fullName, 0, depth + 2));
            AtlasType matchingChild = this.children.stream().filter(t -> childName.equals(fromMappedName ? t.getMappedName() : t.getObfuscatedName())).findAny().orElse(null);
            if (matchingChild == null) {
                return Optional.empty();
            }
            if (fullName.length - depth == 2) {
                return Optional.of(matchingChild);
            } else {
                return matchingChild.getChild(fullName, fromMappedName, depth + 1);
            }
        }
        return Optional.empty();
    }

    /**
     * Gets a field matching the given obfuscated name.
     *
     * @param name the obfuscated name of the field.
     * @return an optional of the field found, or an empty optional if no such field exists.
     */
    public Optional<AtlasField> getFieldObfuscated(String name) {
        return this.getField(name, false);
    }

    /**
     * Gets a field matching the given mapped name.
     *
     * @param name the mapped name of the field.
     * @return an optional of the field found, or an empty optional if no such field exists.
     */
    public Optional<AtlasField> getFieldMapped(String name) {
        return this.getField(name, true);
    }

    /**
     * Gets a field matching the given name.
     *
     * @param name           the name of the field.
     * @param fromMappedName whether or not the search is to be performed using mapped or unmapped names.
     * @return an optional of the field found, or an empty optional if no such field exists.
     */
    public Optional<AtlasField> getField(String name, boolean fromMappedName) {
        return this.getFields().stream().filter((m) -> fromMappedName ? m.getMappedName().equals(name) : m.getObfuscatedName().equals(name)).findAny();
    }

    /**
     * Gets a method matching the given obfuscated name and descriptor.
     *
     * @param name       the obfuscated name of the method.
     * @param descriptor the obfuscated descriptor of the method.
     * @return an optional of the method found, or an empty optional if no such method exists.
     */
    public Optional<AtlasMethod> getMethodObfuscated(String name, String descriptor) {
        return this.getMethod(name, descriptor, false);
    }

    /**
     * Gets a method matching the given mapped name and descriptor.
     *
     * @param name       the mapped name of the method.
     * @param descriptor the mapped descriptor of the method.
     * @return an optional of the method found, or an empty optional if no such method exists.
     */
    public Optional<AtlasMethod> getMethodMapped(String name, String descriptor) {
        return this.getMethod(name, descriptor, true);
    }

    /**
     * Gets a method matching the given name and descriptor.
     *
     * @param name           the name of the method.
     * @param descriptor     the descriptor of the method.
     * @param fromMappedName whether or not the search is to be performed using mapped or unmapped names.
     * @return an optional of the method found, or an empty optional if no such method exists.
     */
    public Optional<AtlasMethod> getMethod(String name, String descriptor, boolean fromMappedName) {
        return this.getMethods().stream().filter((m) -> fromMappedName ? m.matchesMapped(name, descriptor) : m.matchesObfuscated(name, descriptor)).findAny();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("obfuscatedName", this.getObfuscatedName())
                .add("mappedName", this.getMappedName())
                .add("fields", this.fields)
                .add("methods", this.methods)
                .toString();
    }
}
