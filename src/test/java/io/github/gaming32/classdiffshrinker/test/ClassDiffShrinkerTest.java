package io.github.gaming32.classdiffshrinker.test;

import com.nothome.delta.Delta;
import io.github.gaming32.classdiffshrinker.ClassDiffShrinker;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;

public class ClassDiffShrinkerTest {
    public static void main(String[] args) throws IOException, ReflectiveOperationException {
        final byte[] originalBytes = read("OriginalClass.class");
        final byte[] modifiedBytes = read("ModifiedClass.class");
        final byte[] shrunkBytes = ClassDiffShrinker.shrink(originalBytes, modifiedBytes);
        System.out.printf(
            "Original: %d, Modified: %d, Shrunk: %d\n",
            originalBytes.length, modifiedBytes.length, shrunkBytes.length
        );
        System.out.printf(
            "Original/Modified: %d, Original/Shrunk: %d\n",
            new Delta().compute(originalBytes, modifiedBytes).length,
            new Delta().compute(originalBytes, shrunkBytes).length
        );
        MethodHandles.lookup()
            .defineClass(shrunkBytes)
            .getMethod("main", String[].class)
            .invoke(null, new Object[] {args});
    }

    private static byte[] read(String path) throws IOException {
        try (InputStream is = ClassDiffShrinkerTest.class.getResourceAsStream(path)) {
            assert is != null;
            return is.readAllBytes();
        }
    }
}
