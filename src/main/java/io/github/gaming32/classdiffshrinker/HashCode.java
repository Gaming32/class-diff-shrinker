package io.github.gaming32.classdiffshrinker;

import io.github.dmlloyd.classfile.BootstrapMethodEntry;
import io.github.dmlloyd.classfile.constantpool.AnnotationConstantValueEntry;
import io.github.dmlloyd.classfile.constantpool.DynamicConstantPoolEntry;
import io.github.dmlloyd.classfile.constantpool.LoadableConstantEntry;
import io.github.dmlloyd.classfile.constantpool.MemberRefEntry;
import io.github.dmlloyd.classfile.constantpool.ModuleEntry;
import io.github.dmlloyd.classfile.constantpool.NameAndTypeEntry;
import io.github.dmlloyd.classfile.constantpool.PackageEntry;
import io.github.dmlloyd.classfile.constantpool.PoolEntry;

import java.util.List;

final class HashCode {
    private HashCode() {
    }

    static int hashCode(PoolEntry entry) {
        return switch (entry) {
            case AnnotationConstantValueEntry e -> e.constantValue().hashCode();
            case DynamicConstantPoolEntry e -> combine(hashCode(e.bootstrap()), hashCode(e.nameAndType()));
            case LoadableConstantEntry e -> e.constantValue().hashCode();
            case MemberRefEntry e -> combine(hashCode(e.owner()), hashCode(e.nameAndType()));
            case ModuleEntry e -> hashCode(e.name());
            case NameAndTypeEntry e -> combine(hashCode(e.name()), hashCode(e.type()));
            case PackageEntry e -> hashCode(e.name());
        };
    }

    static int hashCode(BootstrapMethodEntry entry) {
        return combine(hashCode(entry.bootstrapMethod()), hashCode(entry.arguments()));
    }

    static int hashCode(List<? extends PoolEntry> entries) {
        int result = 0;
        for (final PoolEntry entry : entries) {
            result = result * 31 + hashCode(entry);
        }
        return result;
    }

    private static int combine(int... hashes) {
        int result = 0;
        for (final int hash : hashes) {
            result = result * 31 + hash;
        }
        return result;
    }
}
