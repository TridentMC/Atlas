package com.tridevmc.atlas.read;

import com.tridevmc.atlas.mappings.AtlasMappings;

/**
 * Interface that defines an object as a mappings reader, useful for more generic pipeline purposes.
 *
 * @author Benjamin K
 */
public interface IMappingsReader {

    /**
     * Performs a read of the mappings on the given object, the object itself must be constructed with the required information to create AtlasMappings.
     *
     * @return the mappings that were read from the source data.
     */
    AtlasMappings read();

}
