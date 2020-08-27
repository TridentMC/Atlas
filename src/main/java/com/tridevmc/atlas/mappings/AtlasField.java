package com.tridevmc.atlas.mappings;

import com.google.common.base.MoreObjects;

/**
 * Used to represent mappings provided for a field, contains a type reference in addition to standard member data.
 *
 * @author Benjamin K
 */
public class AtlasField extends AtlasMember {

    public static class Builder implements IMemberBuilder<AtlasField> {

        private String obfuscatedName, mappedName;
        private String type;

        public Builder(String obfuscatedName, String mappedName, String type) {
            this.obfuscatedName = obfuscatedName;
            this.mappedName = mappedName;
            this.type = type;
        }

        public Builder setObfuscatedName(String obfuscatedName) {
            this.obfuscatedName = obfuscatedName;
            return this;
        }

        public Builder setMappedName(String mappedName) {
            this.mappedName = mappedName;
            return this;
        }

        public Builder setType(String type) {
            this.type = type;
            return this;
        }

        @Override
        public AtlasField build(AtlasType parent) {
            return new AtlasField(parent, this.obfuscatedName, this.mappedName, this.type);
        }
    }

    private final String type;
    private final transient AtlasType parent;

    public AtlasField(AtlasType parent, String obfuscatedName, String mappedName, String type) {
        super(obfuscatedName, mappedName);
        this.parent = parent;
        this.type = type;
    }

    /**
     * The type of object the field represents, this type is mapped and not obfuscated.
     *
     * @return the mapped type of the field.
     */
    public String getType() {
        return type;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("obfuscatedName", this.getObfuscatedName())
                .add("mappedName", this.getMappedName())
                .add("type", this.type)
                .toString();
    }
}
