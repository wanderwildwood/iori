# iori's crash guard knows its own code by class name in a stack trace, so those names must
# survive R8, and its code must not be inlined into upstream callers, where its frames would
# carry upstream's names. iori's code is small; keeping all of it is the simple way.
-keep class com.wanderwildwood.iori.** { *; }
