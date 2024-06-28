package io.github.gaming32.classdiffshrinker;

import io.github.dmlloyd.classfile.Attributes;
import io.github.dmlloyd.classfile.BootstrapMethodEntry;
import io.github.dmlloyd.classfile.ClassFile;
import io.github.dmlloyd.classfile.ClassModel;
import io.github.dmlloyd.classfile.ClassReader;
import io.github.dmlloyd.classfile.constantpool.AnnotationConstantValueEntry;
import io.github.dmlloyd.classfile.constantpool.ConstantPool;
import io.github.dmlloyd.classfile.constantpool.ConstantPoolBuilder;
import io.github.dmlloyd.classfile.constantpool.DynamicConstantPoolEntry;
import io.github.dmlloyd.classfile.constantpool.FieldRefEntry;
import io.github.dmlloyd.classfile.constantpool.InterfaceMethodRefEntry;
import io.github.dmlloyd.classfile.constantpool.LoadableConstantEntry;
import io.github.dmlloyd.classfile.constantpool.MemberRefEntry;
import io.github.dmlloyd.classfile.constantpool.MethodRefEntry;
import io.github.dmlloyd.classfile.constantpool.ModuleEntry;
import io.github.dmlloyd.classfile.constantpool.NameAndTypeEntry;
import io.github.dmlloyd.classfile.constantpool.PackageEntry;
import io.github.dmlloyd.classfile.constantpool.PoolEntry;

import java.io.ByteArrayOutputStream;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static io.github.dmlloyd.classfile.ClassFile.*;

/**
 * This class contains methods to modify the modified class's constant pool to be in the same order as the original
 * class's constant pool.
 */
public class ClassDiffShrinker {
    private static final byte[] EMPTY_ENTRY = {TAG_UTF8, 0, 0};

    /**
     * Modifies the modified class's constant pool to match the original's.
     * @param originalClass The original class's bytes.
     * @param modifiedClass The modified class's bytes.
     * @return The bytes of the modified class, with the constant pool and bootstrap method pool sorted to match
     *         {@code originalClass}'s order
     * @see #shrink(ClassFile, byte[], byte[])
     * @see #shrink(ClassFile, ClassModel, ClassModel)
     */
    public static byte[] shrink(byte[] originalClass, byte[] modifiedClass) {
        return shrink(ClassFile.of(), originalClass, modifiedClass);
    }

    /**
     * Modifies the modified class's constant pool to match the original's.
     * @param classFile The {@link ClassFile} context to use for the parsing and transformation.
     * @param originalClass The original class's bytes.
     * @param modifiedClass The modified class's bytes.
     * @return The bytes of the modified class, with the constant pool and bootstrap method pool sorted to match
     *         {@code originalClass}'s order
     */
    public static byte[] shrink(ClassFile classFile, byte[] originalClass, byte[] modifiedClass) {
        return shrink(classFile, classFile.parse(originalClass), classFile.parse(modifiedClass));
    }

    /**
     * Modifies the modified class's constant pool to match the original's.
     * @param classFile The {@link ClassFile} context to use for the parsing and transformation.
     * @param originalClass The original class's {@link ClassModel}.
     * @param modifiedClass The modified class's {@link ClassModel}.
     * @return The bytes of the modified class, with the constant pool and bootstrap method pool sorted to match
     *         {@code originalClass}'s order
     */
    public static byte[] shrink(ClassFile classFile, ClassModel originalClass, ClassModel modifiedClass) {
        final CombinedClass combined = createCombinedClass(classFile, originalClass, modifiedClass);
        return trimUnusedEntries(
            combined.classBytes,
            (ClassReader)classFile.parse(combined.classBytes).constantPool(),
            combined.constantPool,
            modifiedClass.constantPool()
        );
    }

    private static CombinedClass createCombinedClass(ClassFile classFile, ClassModel originalClass, ClassModel modifiedClass) {
        final ConstantPoolBuilder poolBuilder = ConstantPoolBuilder.of(originalClass);
        addConstantPoolEntries(poolBuilder, modifiedClass.constantPool());
        return new CombinedClass(
            classFile.build(
                modifiedClass.thisClass(), poolBuilder,
                builder -> modifiedClass.forEachElement(builder::with)
            ),
            poolBuilder
        );
    }

    private static void addConstantPoolEntries(ConstantPoolBuilder builder, ConstantPool from) {
        for (final PoolEntry entry : from) {
            switch (entry) {
                case AnnotationConstantValueEntry e -> builder.annotationConstantValueEntry(e.constantValue());
                case DynamicConstantPoolEntry e -> builder.constantDynamicEntry(e.bootstrap(), e.nameAndType());
                case LoadableConstantEntry e -> builder.loadableConstantEntry(e.constantValue());
                case MemberRefEntry refEntry -> {
                    switch (refEntry) {
                        case FieldRefEntry e -> builder.fieldRefEntry(e.owner(), e.nameAndType());
                        case InterfaceMethodRefEntry e -> builder.interfaceMethodRefEntry(e.owner(), e.nameAndType());
                        case MethodRefEntry e -> builder.methodRefEntry(e.owner(), e.nameAndType());
                    }
                }
                case ModuleEntry e -> builder.moduleEntry(e.name());
                case NameAndTypeEntry e -> builder.nameAndTypeEntry(e.name(), e.type());
                case PackageEntry e -> builder.packageEntry(e.name());
            }
        }
        for (int i = 0, l = from.bootstrapMethodCount(); i < l; i++) {
            final BootstrapMethodEntry entry = from.bootstrapMethodEntry(i);
            builder.bsmEntry(entry.bootstrapMethod(), entry.arguments());
        }
    }

    private static byte[] trimUnusedEntries(
        byte[] newBytes,
        ClassReader newReader,
        ConstantPoolBuilder newCP,
        ConstantPool modifiedCP
    ) {
        final ByteArrayOutputStream result = new ByteArrayOutputStream(newBytes.length);
        result.write(newBytes, 0, 10);

        final int endCpIndex = trimUnusedCPEntries(result, newBytes, newReader, modifiedCP);
        final int bsmPos = skipToBSMs(newReader, endCpIndex);
        result.write(newBytes, endCpIndex, bsmPos - endCpIndex);
        if (bsmPos < newBytes.length) {
            final int afterBSMs = trimUnusedBSMEntries(result, newBytes, newReader, newCP, modifiedCP, bsmPos);
            result.write(newBytes, afterBSMs, newBytes.length - afterBSMs);
        }

        return result.toByteArray();
    }

    private static int trimUnusedCPEntries(
        ByteArrayOutputStream result,
        byte[] newBytes,
        ClassReader newReader,
        ConstantPool modifiedCP
    ) {
        final var poolEntries = StreamSupport.stream(modifiedCP.spliterator(), false)
            .map(PoolEntryWrapper::new)
            .collect(Collectors.toSet());
        int index = 10;
        for (int i = 1; i < newReader.size(); i++) {
            final int cpIndex = i;
            final int lastIndex = index;
            final int tag = newReader.readU1(index++);
            switch (tag) {
                case TAG_CLASS, TAG_METHODTYPE, TAG_MODULE, TAG_STRING, TAG_PACKAGE -> index += 2;
                case TAG_METHODHANDLE -> index += 3;
                case TAG_CONSTANTDYNAMIC, TAG_FIELDREF, TAG_FLOAT, TAG_INTEGER, TAG_INTERFACEMETHODREF,
                     TAG_INVOKEDYNAMIC, TAG_METHODREF, TAG_NAMEANDTYPE -> index += 4;
                case TAG_DOUBLE, TAG_LONG -> {
                    i++;
                    index += 8;
                }
                case TAG_UTF8 -> index += 2 + newReader.readU2(index);
                default -> throw new AssertionError();
            }
            if (poolEntries.contains(new PoolEntryWrapper(newReader.entryByIndex(cpIndex)))) {
                result.write(newBytes, lastIndex, index - lastIndex);
            } else {
                result.writeBytes(EMPTY_ENTRY);
            }
        }
        return index;
    }

    // Based on ASM's getFirstAttributeOffset
    private static int skipToBSMs(ClassReader reader, int header) {
        int currentOffset = header + 8 + reader.readU2(header + 6) * 2;

        final int fieldsCount = reader.readU2(currentOffset);
        currentOffset += 2;
        for (int i = 0; i < fieldsCount; i++) {
            currentOffset += 6;
            currentOffset = reader.skipAttributeHolder(currentOffset);
        }

        final int methodsCount = reader.readU2(currentOffset);
        currentOffset += 2;
        for (int i = 0; i < methodsCount; i++) {
            currentOffset += 6;
            currentOffset = reader.skipAttributeHolder(currentOffset);
        }

        final int attributesCount = reader.readU2(currentOffset);
        currentOffset += 2;
        for (int i = 0; i < attributesCount; i++) {
            final String attributeName = reader.readUtf8Entry(currentOffset).stringValue();
            currentOffset += 2;
            if (attributeName.equals(Attributes.NAME_BOOTSTRAP_METHODS)) {
                return currentOffset;
            }
            currentOffset += 4 + reader.readInt(currentOffset);
        }
        return reader.classfileLength();
    }

    private static int trimUnusedBSMEntries(
        ByteArrayOutputStream result,
        byte[] newBytes,
        ClassReader newReader,
        ConstantPoolBuilder newCP,
        ConstantPool modifiedCP,
        int index
    ) {
        final int length = newReader.readInt(index);
        index += 4;

        if (modifiedCP.bootstrapMethodCount() == 0) {
            // Simply remove the BSMs entirely
            return index + length;
        }

        final var bsmEntries = IntStream.range(0, modifiedCP.bootstrapMethodCount())
            .mapToObj(modifiedCP::bootstrapMethodEntry)
            .map(BootstrapMethodEntryWrapper::new)
            .collect(Collectors.toSet());
        final int strippedHandleIndex = newCP.methodHandleEntry(
            bsmEntries.iterator()
                .next()
                .entry()
                .bootstrapMethod()
                .asSymbol()
        ).index();
        final byte[] strippedHandleBytes = {(byte)(strippedHandleIndex >> 8), (byte)strippedHandleIndex, 0, 0};

        index += 2;
        final ByteArrayOutputStream newBSM = new ByteArrayOutputStream(length);
        final int bsmCount = newReader.bootstrapMethodCount();
        newBSM.write(bsmCount >> 8);
        newBSM.write(bsmCount);

        for (int i = 0; i < bsmCount; i++) {
            final int lastIndex = index;

            index += 4 + newReader.readU2(index + 2) * 2;

            if (bsmEntries.contains(new BootstrapMethodEntryWrapper(newReader.bootstrapMethodEntry(i)))) {
                newBSM.write(newBytes, lastIndex, index - lastIndex);
            } else {
                newBSM.writeBytes(strippedHandleBytes);
            }
        }

        final byte[] newBsmBytes = newBSM.toByteArray();
        result.write(newBsmBytes.length >> 24);
        result.write(newBsmBytes.length >> 16);
        result.write(newBsmBytes.length >> 8);
        result.write(newBsmBytes.length);
        result.writeBytes(newBsmBytes);
        return index;
    }

    private record CombinedClass(byte[] classBytes, ConstantPoolBuilder constantPool) {
    }
}
