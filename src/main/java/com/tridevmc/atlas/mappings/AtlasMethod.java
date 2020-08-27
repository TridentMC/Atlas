package com.tridevmc.atlas.mappings;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Used to represent mappings provided for a method, contains type references for the arguments and return type.
 * <p>
 * Also contains methods for creation and caching of both obfuscated and mapped method descriptors.
 *
 * @author Benjamin K
 */
public class AtlasMethod extends AtlasMember {

    public static class Builder implements IMemberBuilder<AtlasMethod> {
        private String obfuscatedName, mappedName;
        private String returnType;
        private List<String> argumentTypes;

        public Builder(String obfuscatedName, String mappedName, String returnType, List<String> argumentTypes) {
            this.obfuscatedName = obfuscatedName;
            this.mappedName = mappedName;
            this.returnType = returnType;
            this.argumentTypes = argumentTypes;
        }

        public AtlasMethod build(AtlasType parent) {
            return new AtlasMethod(parent, obfuscatedName, mappedName, returnType, argumentTypes);
        }
    }

    public final String returnType;
    public final ImmutableList<String> argumentTypes;
    private final transient AtlasType parent;

    private transient String obfuscatedDescriptor = null;
    private transient String mappedDescriptor = null;

    public AtlasMethod(AtlasType parent, String obfuscatedName, String mappedName, String returnType, List<String> argumentTypes) {
        super(obfuscatedName, mappedName);
        this.parent = parent;
        this.returnType = returnType;
        this.argumentTypes = ImmutableList.copyOf(argumentTypes);
    }

    /**
     * Gets the return type for this method or null if void, the type provided are mapped and not obfuscated.
     *
     * @return the return type for this method.
     */
    public String getReturnType() {
        return returnType;
    }

    /**
     * Gets a list of all the argument types used for this method, the argument types provided are mapped and not obfuscated.
     *
     * @return a list of all the argument types for this method.
     */
    public ImmutableList<String> getArgumentTypes() {
        return argumentTypes;
    }

    /**
     * Determines if the method matches the obfuscated name and descriptor provided.
     *
     * @param name       the name of the method when obfuscated.
     * @param descriptor the descriptor of the method when obfuscated.
     * @return true if the method matches, false otherwise.
     */
    public boolean matchesObfuscated(String name, String descriptor) {
        return this.getObfuscatedName().equals(name) && this.getObfuscatedDescriptor().equals(descriptor);
    }

    /**
     * Determines if the method matches the mapped name and descriptor provided.
     *
     * @param name       the name of the method when mapped.
     * @param descriptor the descriptor of the method when mapped.
     * @return true if the method matches, false otherwise.
     */
    public boolean matchesMapped(String name, String descriptor) {
        return this.getMappedName().equals(name) && this.getMappedDescriptor().equals(descriptor);
    }

    /**
     * Creates or retrieves a cached descriptor for this method using obfuscated names.
     *
     * @return the descriptor for this method with obfuscated names.
     */
    private String getObfuscatedDescriptor() {
        if (this.obfuscatedDescriptor == null) {
            this.obfuscatedDescriptor = this.getDescriptor(false);
        }
        return this.obfuscatedDescriptor;
    }

    /**
     * Creates or retrieves a cached descriptor for this method using mapped names.
     *
     * @return the descriptor for this method with mapped names.
     */
    private String getMappedDescriptor() {
        if (this.mappedDescriptor == null) {
            this.mappedDescriptor = this.getDescriptor(true);
        }
        return this.mappedDescriptor;
    }

    /**
     * Creates a descriptor representation for the method using the information already stored.
     *
     * @param mapped determines if the descriptor that's generated should use mapped or obfuscated names.
     * @return a descriptor matching the method and given arguments.
     */
    private String getDescriptor(boolean mapped) {
        StringBuilder descriptorArgs = new StringBuilder();
        for (String argumentType : this.getArgumentTypes()) {
            argumentType = simplifyType(descriptorArgs, argumentType);
            String typeName = AtlasMember.convertToDescriptorType(mapped ? this.parent.getMappings().getTypeNameMapped(argumentType) : this.parent.getMappings().getTypeNameObfuscated(argumentType));
            descriptorArgs.append(typeName);
        }
        StringBuilder returnDescriptor = new StringBuilder();
        String returnType = this.getReturnType();
        returnType = simplifyType(returnDescriptor, returnType);
        returnType = AtlasMember.convertToDescriptorType(mapped ? this.parent.getMappings().getTypeNameMapped(returnType) : this.parent.getMappings().getTypeNameObfuscated(returnType));
        returnDescriptor.append(returnType);
        return String.format("(%s)%s", descriptorArgs, returnDescriptor);
    }

    private String simplifyType(StringBuilder descriptorArgs, String argumentType) {
        if (argumentType.endsWith("[]")) {
            int arrayDepth = (argumentType.length() - argumentType.indexOf("[]")) / 2;
            for (int i = 0; i < arrayDepth; i++) {
                descriptorArgs.append("[");
            }
            argumentType = argumentType.substring(0, argumentType.indexOf("[]"));
        }
        return argumentType;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("obfuscatedName", this.getObfuscatedName())
                .add("mappedName", this.getMappedName())
                .add("returnType", this.returnType)
                .add("argumentTypes", this.argumentTypes)
                .toString();
    }

}
