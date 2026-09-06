# Hilt, Room, WorkManager, and kotlinx.serialization publish their own consumer rules.
-keep class com.vidbox.worker.RecoveryWorker { public <init>(...); }
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable
