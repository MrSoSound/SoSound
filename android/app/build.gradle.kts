import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "com.sosound.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sosound.app"
        // Android 8: sotto, il foreground service si comporta in modo
        // abbastanza diverso da richiedere un secondo percorso di codice.
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.1.1"
    }

    // La chiave di firma sta fuori dal progetto e fuori da git: chi ce
    // l'ha puo' firmare un aggiornamento che Android accetta come nostro.
    // Se il file non c'e' — un'altra macchina, un altro clone — la build
    // release semplicemente non si firma, invece di fallire in modo
    // oscuro a meta' compilazione.
    val chiavi = rootProject.file("keystore.properties")
    val credenziali = Properties().apply {
        if (chiavi.exists()) chiavi.inputStream().use { load(it) }
    }

    signingConfigs {
        if (chiavi.exists()) {
            create("rilascio") {
                // v3 esplicito: e' lo schema che permette, un giorno, di
                // cambiare chiave senza far disinstallare l'app a tutti.
                // Senza, quella porta resta chiusa per sempre.
                enableV2Signing = true
                enableV3Signing = true
                storeFile = file(credenziali.getProperty("storeFile"))
                storePassword = credenziali.getProperty("storePassword")
                keyAlias = credenziali.getProperty("keyAlias")
                keyPassword = credenziali.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // L'app si installa a mano, non passa dal Play Store: la build
            // di debug è quella che userai davvero.
            isMinifyEnabled = false
        }
        release {
            if (chiavi.exists()) signingConfig = signingConfigs.getByName("rilascio")

            // Accorciare il codice resta spento.
            //
            // Quasi tutto il peso dell'APK e' Python e yt-dlp dentro gli
            // asset, che R8 non tocca: si guadagnerebbero briciole. Si
            // rischierebbe invece il modo peggiore di rompere qualcosa —
            // compila, si installa, e sbaglia a riprodurre il terzo brano
            // perche' una classe cercata per riflessione non c'e' piu'.
            // Si accende quando c'e' tempo di provarlo davvero.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }

    // Room scrive qui lo schema atteso a ogni versione: serve a
    // confrontarci le migrazioni scritte a mano, ed e' l'unico modo di
    // accorgersi di una differenza prima che lo faccia il telefono
    // dell'utente rifiutandosi di aprire il database.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs {
            // yt-dlp arriva come interprete Python dentro un .so che va
            // estratto su disco per poter essere eseguito. Con il
            // packaging moderno resterebbe compresso dentro l'APK e
            // l'inizializzazione fallirebbe.
            useLegacyPackaging = true
        }
    }

    splits {
        // Un APK per architettura invece di uno solo che le contiene tutte.
        // Il runtime Python pesa ~15 MB per ABI: in un APK universale
        // sarebbero 60 MB di cui il telefono ne usa 15.
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            // Anche quello universale, per chi non sa che telefono ha.
            isUniversalApk = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    // Media3: il player e la sessione media (schermo spento, lockscreen,
    // tasti degli auricolari).
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.cast)
    implementation(libs.play.services.cast.framework)
    implementation(libs.androidx.mediarouter)
    // Serve l'activity AppCompat: il selettore dei dispositivi Cast
    // e' un DialogFragment e senza non ha dove aprirsi.
    implementation(libs.androidx.appcompat)
    implementation(libs.nanohttpd)

    // yt-dlp che gira dentro l'app.
    implementation(libs.youtubedl.android)

    // I download continuano anche se l'app viene chiusa.
    implementation(libs.androidx.work.runtime)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.coil.compose)
    implementation(libs.androidx.documentfile)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Il test della ricerca gira sulla JVM e interroga l'API vera:
    // e' la struttura della risposta che si rompe, non la logica.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
