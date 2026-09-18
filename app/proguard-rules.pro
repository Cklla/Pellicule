# Règles R8 pour le build de release.
#
# La plupart des bibliothèques du projet embarquent déjà les leurs (Retrofit, Moshi, Room, Hilt,
# Firebase, OkHttp) : rien à redire ici pour elles. Seul Moshi en mode réflexion a besoin d'aide,
# parce qu'il lit les noms des propriétés Kotlin à l'exécution — noms que R8 renomme par défaut.

# Métadonnées Kotlin : sans elles, l'adaptateur réflexif de Moshi ne sait plus reconstruire les
# classes de données (il ne verrait plus que des noms obfusqués et des constructeurs synthétiques).
-keep class kotlin.Metadata { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault

# Les DTO sont désérialisés par réflexion : leurs noms de propriétés doivent correspondre au JSON
# renvoyé par TMDB/Jellyfin, donc ni renommage ni suppression.
-keep class fr.cklla.pellicule.data.remote.dto.** { *; }
-keep class fr.cklla.pellicule.data.remote.jellyfin.dto.** { *; }

# kotlin-reflect, embarqué par moshi-kotlin.
-dontwarn kotlin.reflect.jvm.internal.**
-keep class kotlin.reflect.jvm.internal.** { *; }
