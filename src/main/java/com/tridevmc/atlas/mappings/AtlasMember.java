package com.tridevmc.atlas.mappings;

import com.google.common.collect.ImmutableMap;
import org.objectweb.asm.Type;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Base type for all members of an AtlasType, stores references to both the obfuscated and mapped names.
 * <p>
 * Also contains methods for converting a type to a descriptor.
 *
 * @author Benjamin K
 */
public abstract class AtlasMember {

    private static final Map<String, String> classPrimitiveNames = ImmutableMap.copyOf(
            Arrays.stream(new Type[]{Type.VOID_TYPE, Type.BOOLEAN_TYPE, Type.CHAR_TYPE,
                    Type.BYTE_TYPE, Type.SHORT_TYPE, Type.INT_TYPE,
                    Type.FLOAT_TYPE, Type.LONG_TYPE, Type.DOUBLE_TYPE})
                    .collect(Collectors.toMap(Type::getClassName, Type::getDescriptor)));

    public static String convertToDescriptorType(String name) {
        return classPrimitiveNames.getOrDefault(name.replaceAll("\\]|\\[", ""), "L" + name + ";");
    }

    private final String obfuscatedName, mappedName;

    protected AtlasMember(String obfuscatedName, String mappedName) {
        this.obfuscatedName = obfuscatedName;
        this.mappedName = mappedName;
    }

    /**
     * Gets the obfuscated name of this member.
     *
     * @return the obfuscated name of this member.
     */
    public String getObfuscatedName() {
        return obfuscatedName;
    }

    /**
     * Gets the mapped name of this member.
     * @return the mapped name of this member.
     */
    public String getMappedName() {
        return mappedName;
    }

}
