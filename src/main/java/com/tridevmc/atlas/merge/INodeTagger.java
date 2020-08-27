package com.tridevmc.atlas.merge;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Used for tagging nodes based on their build of origin.
 * Typically applies annotations to nodes but full node access is provided allowing more advanced behaviours.
 *
 * @author Benjamin K
 */
public interface INodeTagger {

    void tag(ClassNode node);

    void tag(FieldNode node);

    void tag(MethodNode node);

}
