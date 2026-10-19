# R8 rules for release builds (isMinifyEnabled + isShrinkResources in app/build.gradle.kts).
#
# Nothing here for the libraries: Firebase (Auth, Firestore, AI Logic, App Check), Room, WorkManager, Navigation,
# kotlinx.serialization, coroutines, Coil and OkHttp ship their own consumer rules. The app's own code uses no
# reflection: JSON is read as JsonElement trees (FrankfurterJson, PhotonJson, WikipediaJson, AssistantJson), Firestore
# documents are plain maps (SyncDocuments), and the activity, receivers and worker are named in the manifest or by
# WorkManager, which keeps them.

# Readable stack traces in crash reports (file names are replaced by "SourceFile", line numbers kept).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Form drafts (SavedFormState) are java.io.Serializable and go into the SavedStateHandle, so they come back after
# process death. Keep what Java serialization looks up by name.
-keepclassmembers class com.example.planyourtrip.** implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Type-safe navigation routes (ui/navigation/Routes.kt): Navigation finds each route's serializer at run time.
# kotlinx.serialization's own rules cover this; kept explicitly so a route can never lose its serializer.
-keep,includedescriptorclasses class com.example.planyourtrip.ui.navigation.**$$serializer { *; }
-keepclassmembers class com.example.planyourtrip.ui.navigation.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
