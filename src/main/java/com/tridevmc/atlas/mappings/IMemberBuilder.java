package com.tridevmc.atlas.mappings;

/**
 * Utility interface for building members and assigning their parent types.
 *
 * @param <T> the type of member being built.
 */
public interface IMemberBuilder<T extends AtlasMember> {

    /**
     * Builds the member and assigns the parent type given.
     *
     * @param type the parent type.
     * @return the built member object.
     */
    T build(AtlasType type);

}
