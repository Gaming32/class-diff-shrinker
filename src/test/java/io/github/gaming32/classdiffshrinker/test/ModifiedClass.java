package io.github.gaming32.classdiffshrinker.test;

import java.util.function.Supplier;

public class ModifiedClass {
    public static void main(String[] args) {
        System.err.println("Early bird");
        System.err.println((Supplier<Integer>)() -> args.length);
        // System.out.println("Hello, world! " + args.length);
    }
}
