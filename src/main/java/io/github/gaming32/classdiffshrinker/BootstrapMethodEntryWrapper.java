package io.github.gaming32.classdiffshrinker;

import io.github.dmlloyd.classfile.BootstrapMethodEntry;

record BootstrapMethodEntryWrapper(BootstrapMethodEntry entry) {
    @Override
    public String toString() {
        return entry.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof BootstrapMethodEntryWrapper wrapper && entry.equals(wrapper.entry);
    }

    @Override
    public int hashCode() {
        return HashCode.hashCode(entry);
    }
}
