# kotlinx.serialization genera serializzatori che R8 non vede usare da
# nessuna parte e cancellerebbe: senza queste regole la build release
# compila e poi va in errore a runtime, che e' il modo peggiore di
# scoprirlo.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.sosound.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.sosound.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.sosound.app.data.**$$serializer { *; }

# Media3 cerca per riflessione le implementazioni di servizio.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Ktor e OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
