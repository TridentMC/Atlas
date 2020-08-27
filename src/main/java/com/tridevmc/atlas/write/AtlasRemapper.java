package com.tridevmc.atlas.write;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.tridevmc.atlas.mappings.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.pmw.tinylog.Logger;

import java.io.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Remaps an InputStream of a JAR file using names from an AtlasMappings object.
 * <p>
 * TODO: More docs???
 *
 * @author Benjamin K
 */
public class AtlasRemapper {

    private final AtlasMappings mappings;
    private final InputStream obfuscatedInput;
    private final ExecutorService threadPool;

    public AtlasRemapper(AtlasMappings mappings, InputStream obfuscatedInput, ExecutorService threadPool) {
        this.mappings = mappings;
        this.obfuscatedInput = obfuscatedInput;
        this.threadPool = threadPool;
    }

    public AtlasRemapper(AtlasMappings mappings, InputStream obfuscatedInput, int poolSize) {
        this(mappings, obfuscatedInput, Executors.newFixedThreadPool(poolSize));
    }

    public AtlasRemapper(AtlasMappings mappings, InputStream obfuscatedInput) {
        this(mappings, obfuscatedInput, Executors.newCachedThreadPool());
    }

    /**
     * Performs a remapping operation on the InputStream the remapper was built with, writes the new JAR to the OutputStream provided.
     *
     * @param to the OutputStream to write the mapped JAR file to.
     * @throws IOException if reading or writing fails.
     */
    public void remap(OutputStream to) throws IOException {
        Logger.info("Loading jar from input stream for remap...");
        ZipInputStream jarIn = new ZipInputStream(this.obfuscatedInput);
        JarOutputStream jarOut = new JarOutputStream(to);
        Map<String, ClassNode> classNodes = Maps.newHashMap();
        ZipEntry entry;
        while ((entry = jarIn.getNextEntry()) != null) {
            byte[] data = this.readEntryBytes(jarIn);
            if (entry.getName().endsWith(".class") && mappings.getTypeMapped(entry.getName().substring(0, entry.getName().length() - ".class".length())).isPresent()) {
                ClassReader classReader = new ClassReader(data);
                ClassNode node = new ClassNode();
                classReader.accept(node, 0);
                if (mappings.getTypeMapped(node.name).isPresent()) {
                    classNodes.put(node.name, node);
                }
            } else if (entry.getName().endsWith("MANIFEST.MF")) {
                Manifest manifest = new Manifest(jarIn);
                manifest.getEntries().clear();
                jarOut.putNextEntry(new JarEntry(entry.getName()));
                manifest.write(jarOut);
                jarOut.closeEntry();
            } else {
                jarOut.putNextEntry(new JarEntry(entry.getName()));
                jarOut.write(data);
                jarOut.closeEntry();
            }
        }

        Logger.info("Loaded {} classes from jar, creating composites...", classNodes.size());

        List<Future<CompositeType>> compositeQueue = Lists.newArrayList();
        for (Map.Entry<String, ClassNode> nodeEntry : classNodes.entrySet()) {
            compositeQueue.add(this.threadPool.submit(() -> {
                List<AtlasType> typeComposite = Lists.newArrayList();
                List<ClassNode> queue = Lists.newArrayList(nodeEntry.getValue());
                while (!queue.isEmpty()) {
                    ClassNode currentNode = queue.remove(0);
                    mappings.getTypeMapped(currentNode.name).ifPresent(typeComposite::add);
                    Optional.ofNullable(classNodes.getOrDefault(currentNode.superName, null)).ifPresent(queue::add);
                    currentNode.interfaces.stream().map(n -> classNodes.getOrDefault(n, null)).filter(Objects::nonNull).forEach(queue::add);
                }
                if (typeComposite.isEmpty()) {
                    return null;
                } else {
                    return new CompositeType(nodeEntry.getKey(), typeComposite);
                }
            }));
        }

        Map<String, CompositeType> compositeTypes = Maps.newHashMap();
        while (!compositeQueue.isEmpty()) {
            List<Future<CompositeType>> completedCompositeStatuses = compositeQueue.stream().filter(Future::isDone).collect(Collectors.toList());
            compositeQueue.removeAll(completedCompositeStatuses);
            completedCompositeStatuses.stream().map(this::safeGet).filter(Objects::nonNull).forEach(composite -> compositeTypes.put(composite.rootName, composite));
        }

        Logger.info("Created {} composites, starting remap...", compositeTypes.size());

        long start = Instant.now().toEpochMilli();
        List<Future<RemappedData>> remappingQueue = Lists.newArrayList();
        for (ClassNode unmappedNode : classNodes.values()) {
            remappingQueue.add(this.threadPool.submit(() -> remapClass(compositeTypes, unmappedNode)));
        }

        while (!remappingQueue.isEmpty()) {
            List<Future<RemappedData>> completedRemapStatuses = remappingQueue.stream().filter(Future::isDone).collect(Collectors.toList());
            remappingQueue.removeAll(completedRemapStatuses);
            completedRemapStatuses.stream().map(this::safeGet).filter(Objects::nonNull).forEach(data -> data.write(jarOut));
        }

        jarOut.close();
        long end = Instant.now().toEpochMilli();
        long diff = end - start;
        Logger.info("Remapped and wrote {} classes in {}", classNodes.size(), String.format("%02d:%02d.%02d", TimeUnit.MILLISECONDS.toMinutes(diff),
                TimeUnit.MILLISECONDS.toSeconds(diff - TimeUnit.MINUTES.toMillis(TimeUnit.MILLISECONDS.toMinutes(diff))),
                TimeUnit.MILLISECONDS.toMillis(diff - TimeUnit.SECONDS.toMillis(TimeUnit.MILLISECONDS.toSeconds(diff)) - TimeUnit.MINUTES.toMillis(TimeUnit.MILLISECONDS.toMinutes(diff)))));

        Logger.info("Done!");
    }

    private RemappedData remapClass(Map<String, CompositeType> compositeTypes, ClassNode node) {
        String remappedName = mappings.getTypeNameMapped(node.name);
        String sourceFileName = remappedName.substring(Math.max(0, remappedName.lastIndexOf("/") + 1));
        sourceFileName = sourceFileName.substring(0, sourceFileName.contains("$") ? sourceFileName.indexOf("$") : sourceFileName.length()) + ".java";
        ClassWriter mappedWriter = new ClassWriter(0);
        ClassRemapper remapper = new ClassRemapper(mappedWriter, new ObjectWebRemapper(compositeTypes));
        node.accept(remapper);
        mappedWriter.visitSource(sourceFileName, null);

        return new RemappedData(node.name, remappedName, mappedWriter.toByteArray());
    }

    private byte[] readEntryBytes(ZipInputStream inputStream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer, 0, buffer.length)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
        return out.toByteArray();
    }

    private <T> T safeGet(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            Logger.error("Failed to get value of future {}", e);
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Stores the name and bytes of remapped entries to be written.
     */
    private static class RemappedData {
        private final String obfuscatedEntryName, mappedEntryName;
        private final byte[] data;

        public RemappedData(String obfuscatedEntryName, String mappedEntryName, byte[] data) {
            this.obfuscatedEntryName = obfuscatedEntryName;
            this.mappedEntryName = mappedEntryName;
            this.data = data;
        }

        public void write(JarOutputStream to) {
            try {
                JarEntry entry = new JarEntry(this.mappedEntryName.replace(".", File.separator) + ".class");
                entry.setTime(0x386D4380);
                to.putNextEntry(entry);
                to.write(data);
                to.closeEntry();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("obfuscatedEntryName", obfuscatedEntryName)
                    .add("mappedEntryName", mappedEntryName)
                    .toString();
        }
    }

    /**
     * Implementation of ObjectWebRemapper that handles remapping using composite types generated from the AtlasMappings used by the AtlasRemapper.
     */
    private class ObjectWebRemapper extends Remapper {

        private final Map<String, CompositeType> compositeTypes;

        private ObjectWebRemapper(Map<String, CompositeType> compositeTypes) {
            this.compositeTypes = compositeTypes;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            Optional<CompositeType> type = Optional.ofNullable(this.compositeTypes.getOrDefault(owner, null));
            Optional<AtlasMethod> method = type.flatMap(m -> m.getMethod(name, descriptor, false));
            String mapped = method.map(AtlasMember::getMappedName).orElse(name);
            return mapped;
        }


        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            Optional<CompositeType> type = Optional.ofNullable(this.compositeTypes.getOrDefault(owner, null));
            Optional<AtlasField> field = type.flatMap(m -> m.getField(name, false));
            String mapped = field.map(AtlasMember::getMappedName).orElse(name);
            return mapped;
        }

        @Override
        public String mapPackageName(String name) {
            String mapped = super.mapPackageName(name);
            return mapped;
        }

        @Override
        public String map(String internalName) {
            String mapped = AtlasRemapper.this.mappings.getTypeNameMapped(internalName);
            return mapped;
        }
    }

    /**
     * Used to handle inherited members on objects when mapping, contains a collection of types in order of priority and utility methods to get mapped data.
     */
    private static class CompositeType {
        private final String rootName;
        private List<AtlasType> types;

        public CompositeType(String rootName, List<AtlasType> types) {
            this.rootName = rootName;
            this.types = types;
        }

        public Optional<AtlasMethod> getMethod(String name, String descriptor, boolean fromMappedName) {
            Optional<AtlasMethod> out = Optional.empty();
            for (AtlasType type : types) {
                if (out.isPresent()) {
                    break;
                }
                out = type.getMethod(name, descriptor, fromMappedName);
            }
            return out;
        }

        public Optional<AtlasField> getField(String name, boolean fromMappedName) {
            Optional<AtlasField> out = Optional.empty();
            for (AtlasType type : types) {
                if (out.isPresent()) {
                    break;
                }
                out = type.getField(name, fromMappedName);
            }
            return out;
        }
    }

}

