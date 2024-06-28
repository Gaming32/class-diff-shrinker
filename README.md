# Class Diff Shrinker

When patching source code and recompiling the file, constant pool entries will likely have been inserted in the middle. If you attempt to generate a binary patch between the two class files, the result will be very large. This is because any place the shifted constant pool entries get used is forced to be included in the patch. Not only does this make the patch very large, but it can also force it to include copyrighted code if the patch is being applied to copyrighted code.

This library helps solve this issue by reordering the constant pool (and bootstrap method pool) of the modified class file to match the original class file. This makes it so any unmodified code is completely absent from the patch file.
