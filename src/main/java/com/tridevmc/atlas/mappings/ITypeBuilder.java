package com.tridevmc.atlas.mappings;

/**
 * Utility interface for building types and assigning their mappings.
 *
 * @param <T> the type of type being built.
 */
public interface ITypeBuilder<T extends AtlasType> {

    /**
     * Builds the type and assigns the mappings it is a member of.
     *
     * @param mappings the mappings to assign to the type.
     * @return the built type.
     */
    T build(AtlasMappings mappings);

}
