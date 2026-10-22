/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.builder.testing;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Given a "standard" android.jar, creates a "mockable" version, where all classes and methods are
 * not final. Optionally makes all methods return "default" values, instead of throwing the infamous
 * "Stub!" exceptions.
 *
 * <p>The output does not depend on when or in which timezone it is generated, so outputs can be
 * compared or hashed by content.
 *
 * <p>ATTENTION! Changes to this class change the mockable jar seen by all unit tests. In AGP it is
 * produced by an artifact transform, which Gradle re-runs automatically when the transform's
 * implementation classpath changes (i.e. on a new AGP version).
 */
public class MockableJarGenerator {
    private static final int EMPTY_FLAGS = 0;
    private static final String CONSTRUCTOR = "<init>";
    private static final String CLASS_CONSTRUCTOR = "<clinit>";

    /**
     * Fixed timestamp for all output entries, so the output does not depend on when it was built.
     */
    private static final FileTime ZERO_TIME = FileTime.fromMillis(0);

    private static final Set<String> ENUM_METHODS =
            Set.of(CLASS_CONSTRUCTOR, "valueOf", "values", "$values");

    private static final Set<Type> INTEGER_LIKE_TYPES =
            Set.of(
                    Type.INT_TYPE,
                    Type.BYTE_TYPE,
                    Type.BOOLEAN_TYPE,
                    Type.CHAR_TYPE,
                    Type.SHORT_TYPE);

    private static final List<String> PREFIXES_TO_SKIP =
            List.of(
                    "java.",
                    "javax.",
                    "org.xml.",
                    "org.w3c.",
                    "junit.",
                    "org.apache.commons.logging");

    private final boolean returnDefaultValues;

    public MockableJarGenerator(boolean returnDefaultValues) {
        this.returnDefaultValues = returnDefaultValues;
    }

    /**
     * Writes the mockable version of {@code input} to {@code output}.
     *
     * <p>The jar is first written to a temporary file next to {@code output}, which then replaces
     * {@code output}. If generation fails, an existing {@code output} is left untouched and no
     * partial jar is left at the {@code output} path.
     *
     * @throws IllegalArgumentException if {@code output} is the same file as {@code input}
     */
    public void createMockableJar(File input, File output) throws IOException {
        Path outputPath = output.toPath().toAbsolutePath();
        if (Files.exists(outputPath) && Files.isSameFile(input.toPath(), outputPath)) {
            throw new IllegalArgumentException("Output must not be the input: " + outputPath);
        }
        // Not Files.createTempFile, which would give the output owner-only permissions.
        Path tempPath =
                outputPath.resolveSibling(output.getName() + "." + UUID.randomUUID() + ".tmp");
        try {
            writeMockableJar(input, tempPath);
            try {
                Files.move(
                        tempPath,
                        outputPath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                // Only if the temp file and output are on different file stores; the
                // replacement is then not atomic.
                Files.move(tempPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException e) {
                t.addSuppressed(e);
            }
            throw t;
        }
    }

    private void writeMockableJar(File input, Path output) throws IOException {
        try (JarFile androidJar = new JarFile(input);
                JarOutputStream outputStream =
                        new JarOutputStream(
                                new BufferedOutputStream(
                                        Files.newOutputStream(
                                                output,
                                                StandardOpenOption.CREATE_NEW,
                                                StandardOpenOption.WRITE)))) {

            for (JarEntry entry : Collections.list(androidJar.entries())) {
                try (InputStream inputStream = androidJar.getInputStream(entry)) {
                    if (entry.getName().endsWith(".class")) {
                        if (!skipClass(entry.getName().replace("/", "."))) {
                            rewriteClass(entry, inputStream, outputStream);
                        }
                    } else if (!skipEntry(entry)) {
                        ZipEntry zipEntry = newZipEntry(entry.getName());
                        zipEntry.setComment(entry.getComment());
                        outputStream.putNextEntry(zipEntry);
                        inputStream.transferTo(outputStream);
                    }
                }
            }
        }
    }

    /** Creates an output entry with fixed timestamps, see {@link #ZERO_TIME}. */
    private static ZipEntry newZipEntry(String name) {
        ZipEntry zipEntry = new ZipEntry(name);
        zipEntry.setLastModifiedTime(ZERO_TIME);
        zipEntry.setLastAccessTime(ZERO_TIME);
        zipEntry.setCreationTime(ZERO_TIME);
        return zipEntry;
    }

    private static boolean skipEntry(JarEntry entry) {
        String name = entry.getName();
        return name.endsWith("/")
                || name.startsWith("res/")
                || name.startsWith("assets/")
                || name.equals("AndroidManifest.xml")
                || name.equals("resources.arsc");
    }

    private static boolean skipClass(String className) {
        for (String prefix : PREFIXES_TO_SKIP) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Writes a modified *.class file to the output JAR file.
     */
    private void rewriteClass(
            JarEntry entry,
            InputStream inputStream,
            JarOutputStream outputStream) throws IOException {
        ClassReader classReader = new ClassReader(inputStream);
        ClassNode classNode = new ClassNode(Opcodes.ASM9);

        classReader.accept(classNode, EMPTY_FLAGS);

        modifyClass(classNode);

        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        classNode.accept(classWriter);

        outputStream.putNextEntry(newZipEntry(entry.getName()));
        outputStream.write(classWriter.toByteArray());
    }

    /**
     * Modifies a {@link ClassNode} to clear final flags and rewrite byte code.
     */
    @SuppressWarnings("unchecked")
    private void modifyClass(ClassNode classNode) {
        // Make the class not final.
        classNode.access &= ~Opcodes.ACC_FINAL;

        List<MethodNode> methodNodes = classNode.methods;
        for (MethodNode methodNode : methodNodes) {
            methodNode.access &= ~(Opcodes.ACC_FINAL | Opcodes.ACC_NATIVE);
            fixMethodBody(methodNode, classNode);
        }

        List<FieldNode> fieldNodes = classNode.fields;
        for (FieldNode fieldNode : fieldNodes) {
            // Make public instance fields non-final. This is needed e.g. to "mock" SyncResult.stats.
            if ((fieldNode.access & Opcodes.ACC_PUBLIC) != 0 &&
                    (fieldNode.access & Opcodes.ACC_STATIC) == 0) {
                fieldNode.access &= ~Opcodes.ACC_FINAL;
            }
        }

        List<InnerClassNode> innerClasses = classNode.innerClasses;
        for (InnerClassNode innerClassNode : innerClasses) {
            innerClassNode.access &= ~Opcodes.ACC_FINAL;
        }
    }

    /**
     * Rewrites the method bytecode to remove the "Stub!" exception.
     */
    private void fixMethodBody(MethodNode methodNode, ClassNode classNode) {
        if ((methodNode.access & Opcodes.ACC_ABSTRACT) != 0) {
            // Abstract methods don't have bodies to rewrite.
            return;
        }

        if ((classNode.access & Opcodes.ACC_ENUM) != 0 && ENUM_METHODS.contains(methodNode.name)) {
            // Don't break enum classes.
            return;
        }

        Type returnType = Type.getReturnType(methodNode.desc);
        InsnList instructions = methodNode.instructions;
        if (instructions == null) {
            // Create a body if the method didn't have it, e.g. if it was native.
            instructions = new InsnList();
            methodNode.instructions = instructions;
        }

        if (methodNode.name.equals(CONSTRUCTOR)) {
            // Keep the call to parent constructor, delete the exception after that.

            boolean deadCode = false;
            for (AbstractInsnNode instruction : instructions.toArray()) {
                if (!deadCode) {
                    if (instruction.getOpcode() == Opcodes.INVOKESPECIAL) {
                        instructions.insert(instruction, new InsnNode(Opcodes.RETURN));
                        // Start removing all following instructions.
                        deadCode = true;
                    }
                } else {
                    instructions.remove(instruction);
                }
            }
        } else {
            instructions.clear();

            if (returnDefaultValues || methodNode.name.equals(CLASS_CONSTRUCTOR)) {
                if (INTEGER_LIKE_TYPES.contains(returnType)) {
                    instructions.add(new InsnNode(Opcodes.ICONST_0));
                } else if (returnType.equals(Type.LONG_TYPE)) {
                    instructions.add(new InsnNode(Opcodes.LCONST_0));
                } else if (returnType.equals(Type.FLOAT_TYPE)) {
                    instructions.add(new InsnNode(Opcodes.FCONST_0));
                } else if (returnType.equals(Type.DOUBLE_TYPE)) {
                    instructions.add(new InsnNode(Opcodes.DCONST_0));
                } else if (!returnType.equals(Type.VOID_TYPE)) {
                    instructions.add(new InsnNode(Opcodes.ACONST_NULL));
                }

                instructions.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
            } else {
                instructions.insert(throwExceptionsList(methodNode, classNode));
            }
        }
    }

    private static InsnList throwExceptionsList(MethodNode methodNode, ClassNode classNode) {
        try {
            String runtimeException = Type.getInternalName(RuntimeException.class);
            Constructor<RuntimeException> constructor =
                    RuntimeException.class.getConstructor(String.class);

            InsnList instructions = new InsnList();
            instructions.add(
                    new TypeInsnNode(Opcodes.NEW, runtimeException));
            instructions.add(new InsnNode(Opcodes.DUP));

            String className = classNode.name.replace('/', '.');
            instructions.add(
                    new LdcInsnNode(
                            "Method "
                                    + methodNode.name
                                    + " in "
                                    + className
                                    + " not mocked. See"
                                    + " https://developer.android.com/r/studio-ui/build/not-mocked"
                                    + " for details."));
            instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    runtimeException,
                    CONSTRUCTOR,
                    Type.getType(constructor).getDescriptor(),
                    false));
            instructions.add(new InsnNode(Opcodes.ATHROW));

            return instructions;
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
